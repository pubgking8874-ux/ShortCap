"""verify_shorts.py — end-to-end verification for the Phase 10 shorts data
layer.

Exercises the full Shorts flow (usage sync -> history -> events -> summary)
plus duplicate-sync and invalid-input cases against a RUNNING server and a
live MySQL database, then verifies the rows directly in MySQL and reports
PASS/FAIL per item. Also confirms the existing Settings, Study and Monitoring
endpoints still work.

Usage (two terminals, from `backend/`):
    .venv\\Scripts\\python -m uvicorn app.main:app --reload      # terminal 1
    .venv\\Scripts\\python -m scripts.verify_shorts               # terminal 2

The script creates its own dev users + devices and cleans them up afterwards.
It never modifies the database schema.
"""

import json
import urllib.error
import urllib.request

from sqlalchemy import text

from app.database import SessionLocal
from app.models.device import Device
from app.models.shorts_event import ShortsEvent
from app.models.shorts_usage import ShortsUsage
from app.models.user import User

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90410
OTHER_USER_ID = 90411

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


def setup_devices() -> tuple[int, int]:
    """Insert dev users + one device each; return (dev_device_id, other_device_id)."""
    db = SessionLocal()
    try:
        db.add(User(id=DEV_USER_ID))
        db.add(User(id=OTHER_USER_ID))
        db.flush()
        dev_device = Device(
            user_id=DEV_USER_ID,
            device_uuid=f"verify-shorts-dev-{DEV_USER_ID}",
            device_name="Verify Dev Device",
            is_active=True,
        )
        other_device = Device(
            user_id=OTHER_USER_ID,
            device_uuid=f"verify-shorts-other-{OTHER_USER_ID}",
            device_name="Verify Other Device",
            is_active=True,
        )
        db.add_all([dev_device, other_device])
        db.commit()
        return dev_device.id, other_device.id
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    """Remove rows created by this script (dev users, devices, shorts rows)."""
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(text("DELETE FROM shorts_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM devices WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def main() -> None:
    # Defensive: clear leftovers from a previously interrupted run first.
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    dev_device_id, other_device_id = setup_devices()

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
    # 1. Shorts usage sync
    # ------------------------------------------------------------------
    day1 = "2026-08-10"
    status, synced = request(
        "POST", "/shorts/usage/sync",
        {
            "device_id": dev_device_id,
            "usage_date": day1,
            "shorts_count": 120,
            "duration_seconds": 2400,
            "warning_triggered": True,
            "limit_reached": False,
        },
    )
    expect_status("POST /shorts/usage/sync (single) -> 200", status, 200, synced)
    record(
        "single record synced + user attached + warning state persisted",
        status == 200
        and isinstance(synced, list)
        and len(synced) == 1
        and synced[0].get("user_id") == DEV_USER_ID
        and synced[0].get("shorts_count") == 120
        and synced[0].get("warning_triggered") is True,
        synced,
    )

    # Batch sync: day1 overwritten, day2 created.
    day2 = "2026-08-11"
    status, synced = request(
        "POST", "/shorts/usage/sync",
        [
            {
                "device_id": dev_device_id,
                "usage_date": day1,
                "shorts_count": 150,
                "duration_seconds": 3000,
                "warning_triggered": True,
                "limit_reached": False,
            },
            {
                "device_id": dev_device_id,
                "usage_date": day2,
                "shorts_count": 80,
                "duration_seconds": 1600,
                "warning_triggered": False,
                "limit_reached": True,
            },
        ],
    )
    expect_status("POST /shorts/usage/sync (batch) -> 200", status, 200, synced)
    record(
        "batch sync returns both records",
        status == 200 and isinstance(synced, list) and len(synced) == 2,
        synced,
    )
    day1_row = next((s for s in synced if s.get("usage_date") == day1), None)
    record(
        "re-synced day1 OVERWRITES (150/3000, no duplicate row)",
        day1_row is not None
        and day1_row.get("shorts_count") == 150
        and day1_row.get("duration_seconds") == 3000,
        day1_row,
    )

    # Repeat the exact same sync -> still one row, same values (idempotent).
    status, synced = request(
        "POST", "/shorts/usage/sync",
        {
            "device_id": dev_device_id,
            "usage_date": day1,
            "shorts_count": 150,
            "duration_seconds": 3000,
            "warning_triggered": True,
            "limit_reached": False,
        },
    )
    expect_status("repeat same sync -> 200", status, 200, synced)
    record(
        "duplicate sync idempotent (same values, no growth)",
        status == 200 and synced[0].get("shorts_count") == 150,
        synced,
    )

    # ------------------------------------------------------------------
    # 2. Shorts usage history
    # ------------------------------------------------------------------
    status, usage = request("GET", "/shorts/usage")
    expect_status("GET /shorts/usage -> 200", status, 200, usage)
    record(
        "usage history contains both days",
        status == 200 and isinstance(usage, list) and len(usage) == 2,
        usage,
    )

    status, filtered = request("GET", f"/shorts/usage?device_id={dev_device_id}")
    record(
        "device-filtered usage -> 2 rows",
        status == 200 and isinstance(filtered, list) and len(filtered) == 2,
        filtered,
    )

    status, filtered = request("GET", "/shorts/usage?date_from=2026-08-11&date_to=2026-08-12")
    record(
        "date-range-filtered usage -> 1 row (day2)",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1
        and filtered[0].get("usage_date") == day2,
        filtered,
    )

    status, filtered = request("GET", "/shorts/usage?date_from=2026-08-12&date_to=2026-08-13")
    record(
        "usage outside range -> 0 rows",
        status == 200 and isinstance(filtered, list) and len(filtered) == 0,
        filtered,
    )

    # ------------------------------------------------------------------
    # 3. Shorts events
    # ------------------------------------------------------------------
    status, event = request(
        "POST", "/shorts/events",
        {
            "device_id": dev_device_id,
            "event_type": "SHORT_STARTED",
            "duration_seconds": 0,
            "metadata_json": {"platform": "youtube_shorts"},
        },
    )
    expect_status("POST /shorts/events -> 201", status, 201, event)
    record(
        "event created with user + occurred_at stamped",
        status == 201
        and isinstance(event, dict)
        and event.get("user_id") == DEV_USER_ID
        and event.get("occurred_at") is not None
        and event.get("metadata_json", {}).get("platform") == "youtube_shorts",
        event,
    )

    # Explicit aware timestamp -> normalized to naive UTC.
    status, tz_event = request(
        "POST", "/shorts/events",
        {
            "device_id": dev_device_id,
            "event_type": "SHORT_COUNTED",
            "occurred_at": "2026-08-10T09:00:00+05:30",
            "duration_seconds": 4,
        },
    )
    expect_status("POST event with aware timestamp -> 201", status, 201, tz_event)
    record(
        "aware timestamp normalized to naive UTC (03:30:00)",
        status == 201
        and isinstance(tz_event, dict)
        and tz_event.get("occurred_at") == "2026-08-10T03:30:00",
        tz_event,
    )

    for etype, dur in [("SHORT_ENDED", 4), ("WARNING_TRIGGERED", None), ("LIMIT_REACHED", None)]:
        body = {"device_id": dev_device_id, "event_type": etype}
        if dur is not None:
            body["duration_seconds"] = dur
        status, _ = request("POST", "/shorts/events", body)
        expect_status(f"POST {etype} -> 201", status, 201)

    status, events = request("GET", "/shorts/events")
    expect_status("GET /shorts/events -> 200", status, 200, events)
    record(
        "all 5 events returned",
        status == 200 and isinstance(events, list) and len(events) == 5,
        events,
    )

    status, filtered = request("GET", "/shorts/events?event_type=WARNING_TRIGGERED")
    record(
        "event_type filter -> 1 event",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1
        and filtered[0].get("event_type") == "WARNING_TRIGGERED",
        filtered,
    )

    status, filtered = request(
        "GET", "/shorts/events?start_date=2026-08-10&end_date=2026-08-10"
    )
    record(
        "date-range filter -> 1 event (the normalized one)",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1,
        filtered,
    )

    # ------------------------------------------------------------------
    # 4. Summary
    # ------------------------------------------------------------------
    status, summary = request("GET", "/shorts/summary")
    expect_status("GET /shorts/summary -> 200", status, 200, summary)
    summary_ok = (
        status == 200
        and isinstance(summary, dict)
        and summary.get("total_shorts_count") == 230          # 150 + 80
        and summary.get("total_duration_seconds") == 4600     # 3000 + 1600
        and summary.get("average_daily_shorts") == 115        # 230 / 2 days
        and summary.get("average_daily_duration") == 2300     # 4600 / 2 days
        and summary.get("warning_count") == 1                 # day1 only
        and summary.get("limit_reached_count") == 1           # day2 only
    )
    record("summary values correct", summary_ok, summary)

    # ------------------------------------------------------------------
    # 5. Invalid tests
    # ------------------------------------------------------------------
    status, _ = request(
        "POST", "/shorts/usage/sync",
        {"device_id": dev_device_id, "usage_date": day1, "shorts_count": -1},
    )
    expect_status("negative shorts_count -> 422", status, 422)

    status, _ = request(
        "POST", "/shorts/usage/sync",
        {"device_id": dev_device_id, "usage_date": day1, "duration_seconds": -5},
    )
    expect_status("negative duration_seconds -> 422", status, 422)

    status, _ = request(
        "POST", "/shorts/usage/sync",
        {"device_id": dev_device_id, "usage_date": "not-a-date"},
    )
    expect_status("invalid usage_date -> 422", status, 422)

    status, _ = request(
        "POST", "/shorts/events",
        {"device_id": dev_device_id, "event_type": "NOT_A_REAL_EVENT"},
    )
    expect_status("invalid event type -> 422", status, 422)

    status, _ = request(
        "POST", "/shorts/events",
        {"device_id": dev_device_id, "event_type": "SHORT_STARTED", "duration_seconds": -3},
    )
    expect_status("negative event duration -> 422", status, 422)

    status, _ = request(
        "POST", "/shorts/usage/sync",
        {"device_id": 999999999, "usage_date": day1},
    )
    expect_status("unknown device -> 404", status, 404)

    status, _ = request(
        "POST", "/shorts/usage/sync",
        {"device_id": other_device_id, "usage_date": day1},
    )
    expect_status("another user's device -> 404", status, 404)

    status, _ = request(
        "POST", "/shorts/events",
        {"device_id": other_device_id, "event_type": "SHORT_STARTED"},
    )
    expect_status("another user's device (event) -> 404", status, 404)

    status, _ = request("POST", "/shorts/usage/sync", {"usage_date": day1})
    expect_status("malformed payload (missing device_id) -> 422", status, 422)

    status, _ = request("GET", "/shorts/usage?date_from=2026-08-12&date_to=2026-08-10")
    expect_status("inverted usage date range -> 422", status, 422)

    status, _ = request("GET", "/shorts/events?start_date=2026-08-12&end_date=2026-08-10")
    expect_status("inverted event date range -> 422", status, 422)

    # ------------------------------------------------------------------
    # 6. User isolation (another user sees none of this data)
    # ------------------------------------------------------------------
    status, usage = request("GET", "/shorts/usage", user_id=OTHER_USER_ID)
    record(
        "other user sees no usage rows",
        status == 200 and isinstance(usage, list) and len(usage) == 0,
        usage,
    )
    status, events = request("GET", "/shorts/events", user_id=OTHER_USER_ID)
    record(
        "other user sees no events",
        status == 200 and isinstance(events, list) and len(events) == 0,
        events,
    )
    status, summary = request("GET", "/shorts/summary", user_id=OTHER_USER_ID)
    record(
        "other user summary is all zeros",
        status == 200 and summary.get("total_shorts_count") == 0
        and summary.get("warning_count") == 0,
        summary,
    )

    # ------------------------------------------------------------------
    # 7. MySQL persistence (direct check, independent of the API)
    # ------------------------------------------------------------------
    db = SessionLocal()
    try:
        usage_rows = db.query(ShortsUsage).filter(ShortsUsage.user_id == DEV_USER_ID).all()
        event_rows = db.query(ShortsEvent).filter(ShortsEvent.user_id == DEV_USER_ID).all()

        record("shorts_usage rows persisted (2, no dupes)", len(usage_rows) == 2, f"count={len(usage_rows)}")
        record("shorts_events rows persisted (5)", len(event_rows) == 5, f"count={len(event_rows)}")

        day1_row = next((r for r in usage_rows if r.usage_date.isoformat() == day1), None)
        record(
            "usage FK (device) + ownership (user) correct",
            day1_row is not None
            and day1_row.device_id == dev_device_id
            and day1_row.user_id == DEV_USER_ID
            and day1_row.usage_date.isoformat() == day1,
            str(day1_row),
        )
        record(
            "duplicate sync produced no duplicate row",
            len([r for r in usage_rows if r.usage_date.isoformat() == day1]) == 1,
            f"day1 rows={len([r for r in usage_rows if r.usage_date.isoformat() == day1])}",
        )
        record(
            "day1 values overwritten by re-sync (150/3000)",
            day1_row is not None
            and day1_row.shorts_count == 150
            and day1_row.duration_seconds == 3000,
            str(day1_row),
        )

        day2_row = next((r for r in usage_rows if r.usage_date.isoformat() == day2), None)
        record(
            "warning / limit states persisted per day",
            day1_row is not None and day1_row.warning_triggered is True
            and day1_row.limit_reached is False
            and day2_row is not None and day2_row.warning_triggered is False
            and day2_row.limit_reached is True,
            f"day1={day1_row and (day1_row.warning_triggered, day1_row.limit_reached)} "
            f"day2={day2_row and (day2_row.warning_triggered, day2_row.limit_reached)}",
        )

        counted = next((e for e in event_rows if e.event_type == "SHORT_COUNTED"), None)
        record(
            "event FK (device) + ownership (user) + duration correct",
            counted is not None
            and counted.device_id == dev_device_id
            and counted.user_id == DEV_USER_ID
            and counted.duration_seconds == 4,
            str(counted),
        )
        record(
            "aware timestamp stored as naive UTC",
            counted is not None
            and counted.occurred_at.strftime("%Y-%m-%dT%H:%M:%S") == "2026-08-10T03:30:00",
            str(counted.occurred_at) if counted else "missing",
        )

        event_types = {e.event_type for e in event_rows}
        record(
            "events match submitted actions",
            {
                "SHORT_STARTED",
                "SHORT_COUNTED",
                "SHORT_ENDED",
                "WARNING_TRIGGERED",
                "LIMIT_REACHED",
            } <= event_types,
            sorted(event_types),
        )
    finally:
        db.close()

    # ------------------------------------------------------------------
    # 8. Regression: existing Settings + Study + Monitoring endpoints
    # ------------------------------------------------------------------
    status, body = request("GET", "/settings")
    expect_status("GET /settings -> 200", status, 200, body)
    status, body = request("GET", "/settings/shorts")
    expect_status("GET /settings/shorts -> 200", status, 200, body)

    status, body = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, body)
    status, sess = request("POST", "/study/sessions/start", {})
    expect_status("POST /study/sessions/start -> 201", status, 201, sess)
    if status == 201 and isinstance(sess, dict):
        sid = sess.get("id")
        status, ended = request("POST", f"/study/sessions/{sid}/end")
        expect_status("POST /study/sessions/{id}/end -> 200", status, 200, ended)

    status, body = request("GET", "/monitoring/app-usage")
    expect_status("GET /monitoring/app-usage -> 200", status, 200, body)
    status, body = request("GET", "/monitoring/summary")
    expect_status("GET /monitoring/summary -> 200", status, 200, body)

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
