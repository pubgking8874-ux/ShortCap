"""verify_study.py — end-to-end verification for the Phase 8 study data layer.

Exercises the full study flow (schedule -> session -> break -> end ->
history -> events) plus the invalid-state cases against a RUNNING server and
a live MySQL database, then verifies the rows directly in MySQL and reports
PASS/FAIL per item.

Usage (two terminals, from `backend/`):
    .venv\\Scripts\\python -m uvicorn app.main:app --reload      # terminal 1
    .venv\\Scripts\\python -m scripts.verify_study                # terminal 2

The script cleans up the rows it creates (its own dev users) afterwards.
"""

import json
import urllib.error
import urllib.request

from sqlalchemy import text

from app.database import SessionLocal
from app.models.break_session import BreakSession
from app.models.study_event import StudyEvent
from app.models.study_schedule import StudySchedule
from app.models.study_session import StudySession
from app.models.user import User

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90210
OTHER_USER_ID = 90211

_results: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    _results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  -> {detail}" if not ok else ""))


def request(method: str, path: str, body: object | None = None, user_id: int = DEV_USER_ID):
    """Return (status, parsed-json-or-None). Never raises on HTTP errors."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", "X-Dev-User-Id": str(user_id)},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            if not raw:
                return resp.status, None
            try:
                return resp.status, json.loads(raw)
            except (ValueError, json.JSONDecodeError):
                return resp.status, raw.decode(errors="replace")
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        try:
            detail = json.loads(raw)
        except Exception:  # noqa: BLE001
            detail = raw.decode(errors="replace")
        return exc.code, detail
    except urllib.error.URLError as exc:
        record("Server reachable", False, f"is uvicorn running on {BASE}? ({exc})")
        raise SystemExit(1)


def expect_status(name: str, got: int, want: int, detail: object | None = None) -> None:
    record(name, got == want, f"expected {want}, got {got} ({detail})")


def cleanup(user_ids: list[int]) -> None:
    """Remove rows created by this script (dev users + their study rows)."""
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(
                text(
                    "DELETE FROM study_events WHERE user_id = :uid"
                ),
                {"uid": uid},
            )
            db.execute(
                text(
                    "DELETE FROM break_sessions WHERE study_session_id IN "
                    "(SELECT id FROM study_sessions WHERE user_id = :uid)"
                ),
                {"uid": uid},
            )
            db.execute(text("DELETE FROM study_sessions WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM study_schedules WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def main() -> None:
    # ------------------------------------------------------------------
    # 0. Server sanity (FastAPI verification)
    # ------------------------------------------------------------------
    status, body = request("GET", "/")
    expect_status("GET / (server up)", status, 200, body)

    status, body = request("GET", "/health/db")
    ok = status == 200 and isinstance(body, dict) and body.get("status") == "connected"
    record("GET /health/db connected", ok, f"status={status} body={body}")

    status, _ = request("GET", "/docs")
    expect_status("GET /docs (Swagger)", status, 200)

    # ------------------------------------------------------------------
    # 1. Study schedules
    # ------------------------------------------------------------------
    status, sched = request(
        "POST", "/study/schedules",
        {
            "title": "Math Revision",
            "subject": "Mathematics",
            "start_time": "18:00:00",
            "duration_minutes": 60,
            "days_of_week": ["Monday", "Tue", "Friday"],
            "reminder_minutes": 15,
            "is_enabled": True,
        },
    )
    expect_status("POST /study/schedules -> 201", status, 201, sched)
    schedule_ok = (
        status == 201
        and sched is not None
        and sched.get("title") == "Math Revision"
        and sched.get("days_of_week") == ["Mon", "Tue", "Fri"]
        and sched.get("duration_minutes") == 60
    )
    record("schedule created with normalized days_of_week", schedule_ok, sched)
    schedule_id = sched.get("id") if isinstance(sched, dict) else None

    status, scheds = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, scheds)
    record(
        "schedule appears in list",
        isinstance(scheds, list) and any(s.get("id") == schedule_id for s in scheds),
        scheds,
    )

    status, one = request("GET", f"/study/schedules/{schedule_id}")
    expect_status("GET /study/schedules/{id} -> 200", status, 200, one)
    record("GET schedule returns subject", isinstance(one, dict) and one.get("subject") == "Mathematics", one)

    status, updated = request(
        "PUT", f"/study/schedules/{schedule_id}",
        {"title": "Math Revision (updated)", "is_enabled": False, "days_of_week": ["Sunday"]},
    )
    expect_status("PUT /study/schedules/{id} -> 200", status, 200, updated)
    record(
        "PUT preserves unspecified fields + applies supplied",
        isinstance(updated, dict)
        and updated.get("title") == "Math Revision (updated)"
        and updated.get("is_enabled") is False
        and updated.get("subject") == "Mathematics"
        and updated.get("days_of_week") == ["Sun"],
        updated,
    )

    # ------------------------------------------------------------------
    # 2. Study session start
    # ------------------------------------------------------------------
    status, session = request(
        "POST", "/study/sessions/start",
        {"schedule_id": schedule_id, "planned_duration_seconds": 3600},
    )
    expect_status("POST /study/sessions/start -> 201", status, 201, session)
    session_ok = (
        status == 201
        and isinstance(session, dict)
        and session.get("status") == "active"
        and session.get("schedule_id") == schedule_id
        and session.get("started_at") is not None
        and session.get("planned_duration_seconds") == 3600
    )
    record("session started (active, schedule linked, started_at set)", session_ok, session)
    session_id = session.get("id") if isinstance(session, dict) else None

    status, sessions = request("GET", "/study/sessions")
    expect_status("GET /study/sessions -> 200", status, 200, sessions)
    record(
        "session appears in history",
        isinstance(sessions, list) and any(s.get("id") == session_id for s in sessions),
        sessions,
    )

    status, got = request("GET", f"/study/sessions/{session_id}")
    expect_status("GET /study/sessions/{id} -> 200", status, 200, got)
    record("GET session returns active session", isinstance(got, dict) and got.get("status") == "active", got)

    # ------------------------------------------------------------------
    # 3. Break start / end
    # ------------------------------------------------------------------
    status, brk = request("POST", f"/study/sessions/{session_id}/breaks/start")
    expect_status("POST breaks/start -> 201", status, 201, brk)
    break_ok = (
        status == 201
        and isinstance(brk, dict)
        and brk.get("status") == "active"
        and brk.get("study_session_id") == session_id
    )
    record("break started (active, linked to session)", break_ok, brk)
    break_id = brk.get("id") if isinstance(brk, dict) else None

    status, brk2 = request("POST", f"/study/sessions/{session_id}/breaks/start")
    expect_status("overlapping active break -> 400", status, 400, brk2)

    status, ended_brk = request("POST", f"/study/breaks/{break_id}/end")
    expect_status("POST /study/breaks/{id}/end -> 200", status, 200, ended_brk)
    record(
        "break ended (completed, duration calculated)",
        isinstance(ended_brk, dict)
        and ended_brk.get("status") == "completed"
        and ended_brk.get("duration_seconds") is not None
        and ended_brk.get("ended_at") is not None,
        ended_brk,
    )

    status, again = request("POST", f"/study/breaks/{break_id}/end")
    expect_status("ending completed break again -> 400", status, 400, again)

    # ------------------------------------------------------------------
    # 4. Study session end
    # ------------------------------------------------------------------
    status, ended = request("POST", f"/study/sessions/{session_id}/end")
    expect_status("POST sessions/{id}/end -> 200", status, 200, ended)
    record(
        "session completed with server-side duration",
        isinstance(ended, dict)
        and ended.get("status") == "completed"
        and ended.get("ended_at") is not None
        and ended.get("actual_duration_seconds") is not None,
        ended,
    )

    status, twice = request("POST", f"/study/sessions/{session_id}/end")
    expect_status("ending completed session again -> 400", status, 400, twice)

    status, brk_on_done = request("POST", f"/study/sessions/{session_id}/breaks/start")
    expect_status("break on completed session -> 400", status, 400, brk_on_done)

    # ------------------------------------------------------------------
    # 5. Study events + history
    # ------------------------------------------------------------------
    status, events = request("GET", "/study/events")
    expect_status("GET /study/events -> 200", status, 200, events)
    event_types = {e.get("event_type") for e in events} if isinstance(events, list) else set()
    record(
        "events match actions (STUDY_STARTED/BREAK_STARTED/BREAK_ENDED/STUDY_ENDED)",
        {"STUDY_STARTED", "BREAK_STARTED", "BREAK_ENDED", "STUDY_ENDED"} <= event_types,
        sorted(event_types),
    )

    status, filtered = request("GET", "/study/events?event_type=STUDY_ENDED")
    record(
        "GET /study/events filtered by event_type",
        status == 200
        and isinstance(filtered, list)
        and all(e.get("event_type") == "STUDY_ENDED" for e in filtered),
        filtered,
    )

    status, hist = request("GET", f"/study/sessions?status=completed&schedule_id={schedule_id}")
    record(
        "GET /study/sessions filtered by status+schedule_id",
        status == 200
        and isinstance(hist, list)
        and any(s.get("id") == session_id and s.get("status") == "completed" for s in hist),
        hist,
    )

    # ------------------------------------------------------------------
    # 6. Cancel flow
    # ------------------------------------------------------------------
    status, c_sess = request("POST", "/study/sessions/start", {})
    expect_status("second session start -> 201", status, 201, c_sess)
    cancel_id = c_sess.get("id") if isinstance(c_sess, dict) else None
    status, cancelled = request("POST", f"/study/sessions/{cancel_id}/cancel")
    expect_status("POST sessions/{id}/cancel -> 200", status, 200, cancelled)
    record(
        "session cancelled (status cancelled + STUDY_CANCELLED event)",
        isinstance(cancelled, dict) and cancelled.get("status") == "cancelled",
        cancelled,
    )
    status, events = request("GET", "/study/events?event_type=STUDY_CANCELLED")
    record(
        "STUDY_CANCELLED event created",
        status == 200 and isinstance(events, list) and len(events) >= 1,
        events,
    )

    # ------------------------------------------------------------------
    # 7. Error cases (validation / not found / cross-user)
    # ------------------------------------------------------------------
    status, _ = request("POST", "/study/schedules", {"title": "X", "duration_minutes": -5})
    expect_status("negative duration -> 422", status, 422)

    status, _ = request("POST", "/study/schedules", {"title": "X", "reminder_minutes": -1})
    expect_status("negative reminder -> 422", status, 422)

    status, _ = request("POST", "/study/schedules", {"title": "X", "days_of_week": ["Funday"]})
    expect_status("invalid day name -> 422", status, 422)

    status, _ = request("POST", "/study/schedules", {"title": ""})
    expect_status("empty title -> 422", status, 422)

    status, _ = request("POST", "/study/sessions/start", {"schedule_id": 999999999})
    expect_status("start with nonexistent schedule -> 404", status, 404)

    status, _ = request("POST", "/study/sessions/start", {"planned_duration_seconds": 0})
    expect_status("zero planned duration -> 422", status, 422)

    status, _ = request("GET", "/study/sessions/999999999")
    expect_status("GET nonexistent session -> 404", status, 404)

    status, _ = request("POST", "/study/sessions/999999999/end")
    expect_status("end nonexistent session -> 404", status, 404)

    status, _ = request("POST", "/study/breaks/999999999/end")
    expect_status("end nonexistent break -> 404", status, 404)

    status, _ = request("PUT", f"/study/schedules/{schedule_id}", {"title": "sneaky"}, user_id=OTHER_USER_ID)
    expect_status("update another user's schedule -> 404", status, 404)

    status, _ = request("GET", f"/study/schedules/{schedule_id}", user_id=OTHER_USER_ID)
    expect_status("GET another user's schedule -> 404", status, 404)

    status, _ = request("GET", f"/study/sessions/{session_id}", user_id=OTHER_USER_ID)
    expect_status("GET another user's session -> 404", status, 404)

    status, _ = request("POST", f"/study/sessions/{session_id}/end", user_id=OTHER_USER_ID)
    expect_status("end another user's session -> 404", status, 404)

    # ------------------------------------------------------------------
    # 8. MySQL persistence (direct check, independent of the API)
    # ------------------------------------------------------------------
    db = SessionLocal()
    try:
        scheds = db.query(StudySchedule).filter(StudySchedule.user_id == DEV_USER_ID).all()
        sessions = db.query(StudySession).filter(StudySession.user_id == DEV_USER_ID).all()
        breaks = (
            db.query(BreakSession)
            .join(StudySession, BreakSession.study_session_id == StudySession.id)
            .filter(StudySession.user_id == DEV_USER_ID)
            .all()
        )
        events = db.query(StudyEvent).filter(StudyEvent.user_id == DEV_USER_ID).all()

        record("study_schedules row persisted", len(scheds) == 1, f"count={len(scheds)}")
        record("study_sessions rows persisted", len(sessions) == 2, f"count={len(sessions)}")
        record("break_sessions row persisted", len(breaks) == 1, f"count={len(breaks)}")
        record("study_events rows persisted", len(events) >= 6, f"count={len(events)}")

        s1 = next((s for s in sessions if s.id == session_id), None)
        if s1 is not None:
            fk_ok = (
                s1.schedule_id == schedule_id
                and s1.status == "completed"
                and s1.started_at is not None
                and s1.ended_at is not None
            )
            record("session FK + status + timestamps correct", fk_ok, str(s1))
            # MySQL DATETIME stores whole seconds, so the round-tripped
            # difference can be off by at most 1s from the full-precision
            # in-memory computation when the session straddles a second
            # boundary — allow a 1s tolerance.
            db_duration = int((s1.ended_at - s1.started_at).total_seconds())
            duration_ok = s1.actual_duration_seconds is not None and abs(
                s1.actual_duration_seconds - db_duration
            ) <= 1
            record(
                "session actual_duration == ended_at - started_at (+-1s storage tolerance)",
                duration_ok,
                f"actual={s1.actual_duration_seconds} db={db_duration}",
            )

        b1 = next((b for b in breaks if b.id == break_id), None)
        if b1 is not None:
            db_break_duration = int((b1.ended_at - b1.started_at).total_seconds())
            record(
                "break FK + duration correct (+-1s storage tolerance)",
                b1.study_session_id == session_id
                and b1.status == "completed"
                and b1.duration_seconds is not None
                and abs(b1.duration_seconds - db_break_duration) <= 1,
                str(b1),
            )

        session_event_types = {
            e.event_type for e in events if e.study_session_id == session_id
        }
        record(
            "events match actions in DB",
            {
                "STUDY_STARTED",
                "BREAK_STARTED",
                "BREAK_ENDED",
                "STUDY_ENDED",
            } <= session_event_types,
            sorted(session_event_types),
        )
    finally:
        db.close()

    # ------------------------------------------------------------------
    # 9. Cleanup (rows this script created)
    # ------------------------------------------------------------------
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    print("\nCleaned up verification rows.")

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------
    failed = [r for r in _results if not r[1]]
    total = len(_results)
    print(f"\n{total - len(failed)}/{total} checks passed")
    if failed:
        print("FAILED checks:")
        for name, _, detail in failed:
            print(f"  - {name} ({detail})")
        raise SystemExit(1)
    print("ALL CHECKS PASSED")


if __name__ == "__main__":
    main()
