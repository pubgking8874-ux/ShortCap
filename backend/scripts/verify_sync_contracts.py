"""verify_sync_contracts.py — Phase 16 backend verification.

Verifies every API contract the Android synchronization layer (Phase 16)
consumes, using the EXACT payload shapes the Android DTOs produce
(`app/src/main/java/com/shortscap/app/network/Dtos.kt`). This is the
server-side half of the contract: if this passes, the Android
`HttpBackendApi` field names/values line up with the FastAPI schemas.

Covers, in Android sync order:

  SETTINGS   GET/PUT /settings (+ monitoring/shorts/notifications/leaderboard
             sub-resources, permissions sync)
  STUDY      schedule create/update/delete, session start/end, break start/end
  MONITORING app-usage sync (single + batch), monitoring event create
  SHORTS     usage sync (with platform/surface), shorts event create
  WEB        blocked-website create + list, website event create
  READS      reports daily/weekly/monthly, score daily/weekly/monthly,
             rank weekly/monthly

Plus regression of Settings / Study / Monitoring / Shorts / Web / Reports /
Score / Rank endpoints, GET /, /health/db and /docs.

The script creates its own dev users + device and cleans up afterwards. It
never modifies the database schema.
"""

import json
import urllib.error
import urllib.request
from datetime import date, datetime, time, timedelta

from sqlalchemy import text

from app.database import SessionLocal
from app.models.device import Device
from app.models.user import User

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90801
OTHER_USER_ID = 90802

_results: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    _results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  -> {detail}" if not ok else ""))


def request(method: str, path: str, body: object | None = None, user_id: int = DEV_USER_ID):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", "X-Dev-User-Id": str(user_id)},
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
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


TODAY: date = date.today()
USAGE_DATE = TODAY.isoformat()
NOW_ISO = datetime.combine(TODAY, time(9, 30)).isoformat()


def setup() -> int:
    """Create the dev users + one device for the dev user. Returns device id."""
    db = SessionLocal()
    try:
        db.add(User(id=DEV_USER_ID))
        db.add(User(id=OTHER_USER_ID))
        db.flush()
        device = Device(
            user_id=DEV_USER_ID,
            device_uuid=f"verify-sync-dev-{DEV_USER_ID}",
            device_name="Verify Sync Device",
            is_active=True,
        )
        db.add(device)
        db.flush()
        db.commit()
        return device.id
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(text("DELETE FROM website_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM blocked_websites WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM monitoring_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM app_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM study_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM break_sessions WHERE study_session_id IN "
                            "(SELECT id FROM study_sessions WHERE user_id = :uid)"), {"uid": uid})
            db.execute(text("DELETE FROM study_sessions WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM study_schedules WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM leaderboard_settings WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM devices WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def main() -> None:
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    device_id = setup()

    # ------------------------------------------------------------------
    # 0. Server sanity
    # ------------------------------------------------------------------
    status, body = request("GET", "/")
    expect_status("GET / (server up)", status, 200, body)
    status, body = request("GET", "/health/db")
    ok = status == 200 and isinstance(body, dict) and body.get("status") == "connected"
    record("GET /health/db connected", ok, f"status={status} body={body}")
    status, _ = request("GET", "/docs")
    expect_status("GET /docs (Swagger)", status, 200)

    # ------------------------------------------------------------------
    # 1. SETTINGS sync (GET/PUT /settings + sub-resources)
    # ------------------------------------------------------------------
    status, settings = request("GET", "/settings")
    expect_status("GET /settings -> 200", status, 200, settings)
    record("GET /settings returns server defaults",
           isinstance(settings, dict) and settings.get("theme") in ("dark", "light", "system"),
           str(settings))

    status, updated = request("PUT", "/settings", {
        "theme": "dark",
        "language": "en",
        "notifications_enabled": True,
        "sound_enabled": False,
    })
    expect_status("PUT /settings (partial) -> 200", status, 200, updated)
    record("PUT /settings persists partial update",
           updated.get("theme") == "dark" and updated.get("sound_enabled") is False,
           str(updated))

    for sub, payload in [
        ("monitoring", {"device_monitoring_enabled": True, "monitoring_enabled": True, "strict_mode_enabled": True}),
        ("shorts", {"daily_limit_minutes": 30, "warning_minutes": 20, "is_enabled": True}),
        ("notifications", {"study_notifications": True, "monitoring_notifications": False}),
        ("leaderboard", {"is_enabled": True, "is_opted_in": True, "display_name": "SyncDev"}),
    ]:
        status, body_ = request("PUT", f"/settings/{sub}", payload)
        expect_status(f"PUT /settings/{sub} -> 200", status, 200, body_)
        status, body_ = request("GET", f"/settings/{sub}")
        expect_status(f"GET /settings/{sub} -> 200", status, 200, body_)

    # Permissions sync (list body — the Android syncPermissions shape).
    status, perms = request("PUT", "/settings/permissions", [
        {"permission_key": "USAGE_ACCESS", "is_enabled": True},
        {"permission_key": "ACCESSIBILITY", "is_enabled": False},
    ])
    expect_status("PUT /settings/permissions (list) -> 200", status, 200, perms)
    status, perms = request("GET", "/settings/permissions")
    expect_status("GET /settings/permissions -> 200", status, 200, perms)
    record("permissions synced as last-known mirror",
           isinstance(perms, list) and len(perms) >= 2, str(perms))

    # ------------------------------------------------------------------
    # 2. STUDY sync (schedules, sessions, breaks)
    # ------------------------------------------------------------------
    status, schedule = request("POST", "/study/schedules", {
        "title": "Morning Study",
        "subject": "Physics",
        "start_time": "08:00:00",
        "duration_minutes": 45,
        "days_of_week": ["Mon", "Wed", "Fri"],
        "reminder_minutes": 10,
        "is_enabled": True,
    })
    expect_status("POST /study/schedules -> 201", status, 201, schedule)
    schedule_id = schedule.get("id")
    record("schedule created with id", isinstance(schedule_id, int), str(schedule))

    status, schedules = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, schedules)
    record("schedule list contains the created schedule",
           any(s.get("id") == schedule_id for s in schedules), str(schedules))

    status, updated_sched = request("PUT", f"/study/schedules/{schedule_id}", {"is_enabled": False})
    expect_status("PUT /study/schedules/{id} -> 200", status, 200, updated_sched)
    record("schedule update persists", updated_sched.get("is_enabled") is False, str(updated_sched))

    status, session = request("POST", "/study/sessions/start", {
        "schedule_id": schedule_id,
        "device_id": device_id,
        "planned_duration_seconds": 2700,
    })
    expect_status("POST /study/sessions/start -> 201", status, 201, session)
    session_id = session.get("id")
    record("session started (status active)",
           isinstance(session_id, int) and session.get("status") == "active", str(session))

    status, brk = request("POST", f"/study/sessions/{session_id}/breaks/start")
    expect_status("POST /study/sessions/{id}/breaks/start -> 201", status, 201, brk)
    break_id = brk.get("id")
    record("break started", isinstance(break_id, int), str(brk))

    status, brk = request("POST", f"/study/breaks/{break_id}/end")
    expect_status("POST /study/breaks/{id}/end -> 200", status, 200, brk)
    record("break ended (completed)", brk.get("status") == "completed", str(brk))

    status, ended = request("POST", f"/study/sessions/{session_id}/end", {"cancelled": False})
    expect_status("POST /study/sessions/{id}/end -> 200", status, 200, ended)
    record("session ended (completed)", ended.get("status") == "completed", str(ended))

    status, sessions = request("GET", "/study/sessions")
    expect_status("GET /study/sessions -> 200", status, 200, sessions)
    record("session history contains the session",
           any(s.get("id") == session_id for s in sessions), str(sessions))

    status, events = request("GET", "/study/events")
    expect_status("GET /study/events -> 200", status, 200, events)
    event_types = {e.get("event_type") for e in events}
    record("study events created for each action",
           {"STUDY_STARTED", "STUDY_ENDED", "BREAK_STARTED", "BREAK_ENDED"} <= event_types,
           str(event_types))

    status, _ = request("DELETE", f"/study/schedules/{schedule_id}")
    expect_status("DELETE /study/schedules/{id} -> 204", status, 204)

    # ------------------------------------------------------------------
    # 3. MONITORING sync (usage + events)
    # ------------------------------------------------------------------
    status, usage = request("POST", "/monitoring/app-usage/sync", {
        "device_id": device_id,
        "package_name": "com.example.app",
        "app_name": "Example App",
        "usage_date": USAGE_DATE,
        "duration_seconds": 3600,
        "launch_count": 3,
    })
    expect_status("POST /monitoring/app-usage/sync (single) -> 200", status, 200, usage)
    status, usage_batch = request("POST", "/monitoring/app-usage/sync", [
        {"device_id": device_id, "package_name": "com.example.app", "app_name": "Example App",
         "usage_date": USAGE_DATE, "duration_seconds": 3600, "launch_count": 3},
        {"device_id": device_id, "package_name": "com.example.two", "app_name": "Two",
         "usage_date": USAGE_DATE, "duration_seconds": 600, "launch_count": 1},
    ])
    expect_status("POST /monitoring/app-usage/sync (batch) -> 200", status, 200, usage_batch)
    record("usage batch persisted two rows", isinstance(usage_batch, list) and len(usage_batch) == 2,
           str(usage_batch))

    status, mon_event = request("POST", "/monitoring/events", {
        "device_id": device_id,
        "event_type": "LIMIT_REACHED",
        "app_package": "com.example.app",
        "occurred_at": NOW_ISO,
    })
    expect_status("POST /monitoring/events -> 201", status, 201, mon_event)
    record("monitoring event created", mon_event.get("event_type") == "LIMIT_REACHED", str(mon_event))

    # ------------------------------------------------------------------
    # 4. SHORTS sync (usage with platform/surface + events)
    # ------------------------------------------------------------------
    status, shorts_usage = request("POST", "/shorts/usage/sync", {
        "device_id": device_id,
        "usage_date": USAGE_DATE,
        "shorts_count": 5,
        "duration_seconds": 300,
        "warning_triggered": False,
        "limit_reached": False,
        "platform": "YOUTUBE",
        "surface": "YOUTUBE_SHORTS",
    })
    expect_status("POST /shorts/usage/sync -> 200", status, 200, shorts_usage)
    record("shorts usage persisted with platform/surface",
           shorts_usage[0].get("platform") == "YOUTUBE"
           and shorts_usage[0].get("surface") == "YOUTUBE_SHORTS",
           str(shorts_usage))

    # Idempotent re-sync (same platform/surface/date -> overwrite, no duplicate).
    status, shorts_again = request("POST", "/shorts/usage/sync", {
        "device_id": device_id,
        "usage_date": USAGE_DATE,
        "shorts_count": 8,
        "duration_seconds": 480,
        "warning_triggered": True,
        "limit_reached": False,
        "platform": "YOUTUBE",
        "surface": "YOUTUBE_SHORTS",
    })
    expect_status("POST /shorts/usage/sync (repeat) -> 200", status, 200, shorts_again)
    record("shorts re-sync is idempotent (overwrite, one row)",
           shorts_again[0].get("shorts_count") == 8, str(shorts_again))

    status, shorts_event = request("POST", "/shorts/events", {
        "device_id": device_id,
        "event_type": "SHORT_COUNTED",
        "occurred_at": NOW_ISO,
        "duration_seconds": 60,
        "metadata_json": {"platform": "YOUTUBE", "surface": "YOUTUBE_SHORTS"},
    })
    expect_status("POST /shorts/events -> 201", status, 201, shorts_event)
    record("shorts event created", shorts_event.get("event_type") == "SHORT_COUNTED", str(shorts_event))

    # ------------------------------------------------------------------
    # 5. WEB sync (blocked websites + events)
    # ------------------------------------------------------------------
    status, website = request("POST", "/websites/blocked", {
        "domain": "tiktok.com",
        "is_blocked": True,
        "verification_status": "pending",
    })
    expect_status("POST /websites/blocked -> 201", status, 201, website)
    website_id = website.get("id")
    record("blocked website created + normalized",
           website.get("normalized_domain") == "tiktok.com", str(website))

    status, websites = request("GET", "/websites/blocked")
    expect_status("GET /websites/blocked -> 200", status, 200, websites)
    record("blocked websites list contains the created domain",
           any(w.get("id") == website_id for w in websites), str(websites))

    status, web_event = request("POST", "/web/events", {
        "device_id": device_id,
        "blocked_website_id": website_id,
        "domain": "tiktok.com",
        "event_type": "BLOCK_ATTEMPT",
        "occurred_at": NOW_ISO,
    })
    expect_status("POST /web/events -> 201", status, 201, web_event)
    record("web event created", web_event.get("event_type") == "BLOCK_ATTEMPT", str(web_event))

    # ------------------------------------------------------------------
    # 6. READS: Reports / Score / Rank (server-authoritative retrieval)
    # ------------------------------------------------------------------
    for period in ("daily", "weekly", "monthly"):
        status, report = request("GET", f"/reports/{period}")
        expect_status(f"GET /reports/{period} -> 200", status, 200, report)
        record(f"report {period} returns per-domain structure",
               isinstance(report, dict) and {"study", "monitoring", "shorts", "web"} <= set(report),
               str(report))

    for period in ("daily", "weekly", "monthly"):
        status, score = request("GET", f"/score/{period}")
        expect_status(f"GET /score/{period} -> 200", status, 200, score)
        record(f"score {period} returns 0..100 with status",
               isinstance(score, dict) and 0 <= score.get("score", -1) <= 100
               and score.get("status") in ("sufficient_data", "partial_data", "insufficient_data"),
               str(score))

    for period in ("weekly", "monthly"):
        status, rank = request("GET", f"/rank/{period}")
        expect_status(f"GET /rank/{period} -> 200", status, 200, rank)
        record(f"rank {period} returns the leaderboard contract",
               isinstance(rank, dict) and {"your_rank", "your_score", "total_participants",
                                           "winner", "top_three", "entries"} <= set(rank),
               str(rank))

    # ------------------------------------------------------------------
    # 7. User isolation (the other dev user sees nothing of the first)
    # ------------------------------------------------------------------
    status, other_settings = request("GET", "/settings", user_id=OTHER_USER_ID)
    record("user isolation: other user has independent settings",
           status == 200 and other_settings.get("user_id") == OTHER_USER_ID, str(other_settings))
    status, other_schedules = request("GET", "/study/schedules", user_id=OTHER_USER_ID)
    record("user isolation: other user sees zero schedules",
           status == 200 and other_schedules == [], str(other_schedules))

    # ------------------------------------------------------------------
    # 8. Regression: all earlier layers
    # ------------------------------------------------------------------
    for path in ["/settings", "/settings/shorts", "/study/schedules", "/monitoring/app-usage",
                 "/monitoring/summary", "/shorts/usage", "/shorts/summary",
                 "/websites/blocked", "/web/events", "/web/summary",
                 "/reports/daily", "/reports/weekly", "/score/daily", "/rank/weekly"]:
        status, body = request("GET", path)
        expect_status(f"GET {path} -> 200", status, 200, body)

    # ------------------------------------------------------------------
    # 9. Cleanup
    # ------------------------------------------------------------------
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    print("\nCleaned up verification rows.")

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
