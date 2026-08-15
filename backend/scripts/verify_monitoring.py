"""verify_monitoring.py — end-to-end verification for the Phase 9 monitoring
data layer.

Exercises the full monitoring flow (usage sync -> history -> events ->
summary) plus duplicate-sync and invalid-input cases against a RUNNING server
and a live MySQL database, then verifies the rows directly in MySQL and
reports PASS/FAIL per item. Also confirms the existing Settings and Study
endpoints still work.

Usage (two terminals, from `backend/`):
    .venv\\Scripts\\python -m uvicorn app.main:app --reload      # terminal 1
    .venv\\Scripts\\python -m scripts.verify_monitoring           # terminal 2

The script creates its own dev users + devices and cleans them up afterwards.
"""

import json
import urllib.error
import urllib.request

from sqlalchemy import text

from app.database import SessionLocal
from app.models.app_usage import AppUsage
from app.models.device import Device
from app.models.monitoring_event import MonitoringEvent
from app.models.user import User

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90310
OTHER_USER_ID = 90311

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
            device_uuid=f"verify-monitoring-dev-{DEV_USER_ID}",
            device_name="Verify Dev Device",
            is_active=True,
        )
        other_device = Device(
            user_id=OTHER_USER_ID,
            device_uuid=f"verify-monitoring-other-{OTHER_USER_ID}",
            device_name="Verify Other Device",
            is_active=True,
        )
        db.add_all([dev_device, other_device])
        db.commit()
        return dev_device.id, other_device.id
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    """Remove rows created by this script (dev users, devices, monitoring rows)."""
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(
                text(
                    "DELETE FROM monitoring_events WHERE user_id = :uid"
                ),
                {"uid": uid},
            )
            db.execute(text("DELETE FROM app_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(
                text("DELETE FROM devices WHERE user_id = :uid"),
                {"uid": uid},
            )
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
    # 1. App usage sync
    # ------------------------------------------------------------------
    usage_date = "2026-08-10"
    status, synced = request(
        "POST", "/monitoring/app-usage/sync",
        {
            "device_id": dev_device_id,
            "package_name": "com.example.alpha",
            "app_name": "Alpha",
            "usage_date": usage_date,
            "duration_seconds": 1200,
            "launch_count": 3,
        },
    )
    expect_status("POST /monitoring/app-usage/sync (single) -> 200", status, 200, synced)
    record(
        "single record synced + user attached",
        status == 200
        and isinstance(synced, list)
        and len(synced) == 1
        and synced[0].get("user_id") == DEV_USER_ID
        and synced[0].get("duration_seconds") == 1200,
        synced,
    )

    # Batch sync: alpha overwritten, beta created.
    status, synced = request(
        "POST", "/monitoring/app-usage/sync",
        [
            {
                "device_id": dev_device_id,
                "package_name": "com.example.alpha",
                "app_name": "Alpha",
                "usage_date": usage_date,
                "duration_seconds": 1500,
                "launch_count": 4,
            },
            {
                "device_id": dev_device_id,
                "package_name": "com.example.beta",
                "app_name": "Beta",
                "usage_date": usage_date,
                "duration_seconds": 600,
                "launch_count": 1,
            },
        ],
    )
    expect_status("POST /monitoring/app-usage/sync (batch) -> 200", status, 200, synced)
    record(
        "batch sync returns both records",
        status == 200 and isinstance(synced, list) and len(synced) == 2,
        synced,
    )
    alpha = next((s for s in synced if s.get("package_name") == "com.example.alpha"), None)
    record(
        "re-synced alpha OVERWRITES (1500/4, no duplicate row)",
        alpha is not None
        and alpha.get("duration_seconds") == 1500
        and alpha.get("launch_count") == 4,
        alpha,
    )

    # Repeat the exact same sync -> still one row, same values (idempotent).
    status, synced = request(
        "POST", "/monitoring/app-usage/sync",
        {
            "device_id": dev_device_id,
            "package_name": "com.example.alpha",
            "app_name": "Alpha",
            "usage_date": usage_date,
            "duration_seconds": 1500,
            "launch_count": 4,
        },
    )
    expect_status("repeat same sync -> 200", status, 200, synced)
    record(
        "duplicate sync idempotent (same values, no growth)",
        status == 200 and synced[0].get("duration_seconds") == 1500,
        synced,
    )

    # ------------------------------------------------------------------
    # 2. App usage history
    # ------------------------------------------------------------------
    status, usage = request("GET", "/monitoring/app-usage")
    expect_status("GET /monitoring/app-usage -> 200", status, 200, usage)
    record(
        "usage history contains both packages",
        status == 200 and isinstance(usage, list) and len(usage) == 2,
        usage,
    )

    status, filtered = request("GET", f"/monitoring/app-usage?package_name=com.example.alpha")
    record(
        "package-filtered usage -> 1 row",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1
        and filtered[0].get("package_name") == "com.example.alpha",
        filtered,
    )

    status, filtered = request(
        "GET", "/monitoring/app-usage?date_from=2026-08-09&date_to=2026-08-11"
    )
    record(
        "date-range-filtered usage -> 2 rows",
        status == 200 and isinstance(filtered, list) and len(filtered) == 2,
        filtered,
    )

    status, filtered = request(
        "GET", "/monitoring/app-usage?date_from=2026-08-11&date_to=2026-08-12"
    )
    record(
        "usage outside range -> 0 rows",
        status == 200 and isinstance(filtered, list) and len(filtered) == 0,
        filtered,
    )

    # ------------------------------------------------------------------
    # 3. Monitoring events
    # ------------------------------------------------------------------
    status, event = request(
        "POST", "/monitoring/events",
        {
            "device_id": dev_device_id,
            "event_type": "MONITORING_STARTED",
            "metadata_json": {"source": "verify"},
        },
    )
    expect_status("POST /monitoring/events -> 201", status, 201, event)
    record(
        "event created with user + occurred_at stamped",
        status == 201
        and isinstance(event, dict)
        and event.get("user_id") == DEV_USER_ID
        and event.get("occurred_at") is not None,
        event,
    )

    # Explicit aware timestamp -> normalized to naive UTC.
    status, tz_event = request(
        "POST", "/monitoring/events",
        {
            "device_id": dev_device_id,
            "event_type": "LIMIT_WARNING",
            "app_package": "com.example.alpha",
            "occurred_at": "2026-08-10T09:00:00+05:30",
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

    for etype in ["LIMIT_REACHED", "APP_RESTRICTED", "MONITORING_STOPPED"]:
        body = {"device_id": dev_device_id, "event_type": etype}
        if etype == "APP_RESTRICTED":
            body["app_package"] = "com.example.beta"
        status, _ = request("POST", "/monitoring/events", body)
        expect_status(f"POST {etype} -> 201", status, 201)

    status, events = request("GET", "/monitoring/events")
    expect_status("GET /monitoring/events -> 200", status, 200, events)
    record(
        "all 5 events returned",
        status == 200 and isinstance(events, list) and len(events) == 5,
        events,
    )

    status, filtered = request("GET", "/monitoring/events?event_type=LIMIT_WARNING")
    record(
        "event_type filter -> 1 event",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1
        and filtered[0].get("event_type") == "LIMIT_WARNING",
        filtered,
    )

    status, filtered = request("GET", "/monitoring/events?app_package=com.example.beta")
    record(
        "app_package filter -> 1 event",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1
        and filtered[0].get("app_package") == "com.example.beta",
        filtered,
    )

    status, filtered = request(
        "GET", "/monitoring/events?start_date=2026-08-10&end_date=2026-08-10"
    )
    record(
        "date-range filter -> 1 event (the normalized one)",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1,
        filtered,
    )

    # ------------------------------------------------------------------
    # 4. Summary
    # ------------------------------------------------------------------
    status, summary = request("GET", "/monitoring/summary")
    expect_status("GET /monitoring/summary -> 200", status, 200, summary)
    summary_ok = (
        status == 200
        and isinstance(summary, dict)
        and summary.get("total_app_usage_seconds") == 2100  # 1500 + 600
        and summary.get("total_launches") == 5             # 4 + 1
        and summary.get("monitored_apps_count") == 2       # alpha + beta
        and summary.get("event_count") == 5
    )
    record("summary values correct", summary_ok, summary)

    # ------------------------------------------------------------------
    # 5. Invalid tests
    # ------------------------------------------------------------------
    status, _ = request(
        "POST", "/monitoring/app-usage/sync",
        {"device_id": dev_device_id, "package_name": "com.example.x",
         "usage_date": usage_date, "duration_seconds": -1},
    )
    expect_status("negative duration -> 422", status, 422)

    status, _ = request(
        "POST", "/monitoring/app-usage/sync",
        {"device_id": dev_device_id, "package_name": "com.example.x",
         "usage_date": usage_date, "launch_count": -2},
    )
    expect_status("negative launch_count -> 422", status, 422)

    status, _ = request(
        "POST", "/monitoring/app-usage/sync",
        {"device_id": dev_device_id, "package_name": "not a package!",
         "usage_date": usage_date},
    )
    expect_status("invalid package name -> 422", status, 422)

    status, _ = request(
        "POST", "/monitoring/app-usage/sync",
        {"device_id": dev_device_id, "package_name": "com.example.x",
         "app_name": "   ", "usage_date": usage_date},
    )
    expect_status("empty app_name -> 422", status, 422)

    status, _ = request(
        "POST", "/monitoring/events",
        {"device_id": dev_device_id, "event_type": "NOT_A_REAL_EVENT"},
    )
    expect_status("invalid event type -> 422", status, 422)

    status, _ = request(
        "POST", "/monitoring/app-usage/sync",
        {"device_id": 999999999, "package_name": "com.example.x",
         "usage_date": usage_date},
    )
    expect_status("unknown device -> 404", status, 404)

    status, _ = request(
        "POST", "/monitoring/app-usage/sync",
        {"device_id": other_device_id, "package_name": "com.example.x",
         "usage_date": usage_date},
    )
    expect_status("another user's device -> 404", status, 404)

    status, _ = request(
        "POST", "/monitoring/events",
        {"device_id": other_device_id, "event_type": "MONITORING_STARTED"},
    )
    expect_status("another user's device (event) -> 404", status, 404)

    status, _ = request("GET", "/monitoring/app-usage?date_from=2026-08-12&date_to=2026-08-10")
    expect_status("inverted usage date range -> 422", status, 422)

    status, _ = request("GET", "/monitoring/events?start_date=2026-08-12&end_date=2026-08-10")
    expect_status("inverted event date range -> 422", status, 422)

    # ------------------------------------------------------------------
    # 6. User isolation (another user sees none of this data)
    # ------------------------------------------------------------------
    status, usage = request("GET", "/monitoring/app-usage", user_id=OTHER_USER_ID)
    record(
        "other user sees no usage rows",
        status == 200 and isinstance(usage, list) and len(usage) == 0,
        usage,
    )
    status, summary = request("GET", "/monitoring/summary", user_id=OTHER_USER_ID)
    record(
        "other user summary is all zeros",
        status == 200 and summary.get("total_app_usage_seconds") == 0
        and summary.get("event_count") == 0,
        summary,
    )

    # ------------------------------------------------------------------
    # 7. MySQL persistence (direct check, independent of the API)
    # ------------------------------------------------------------------
    db = SessionLocal()
    try:
        usage_rows = (
            db.query(AppUsage).filter(AppUsage.user_id == DEV_USER_ID).all()
        )
        event_rows = (
            db.query(MonitoringEvent)
            .filter(MonitoringEvent.user_id == DEV_USER_ID)
            .all()
        )

        record("app_usage rows persisted (2, no dupes)", len(usage_rows) == 2, f"count={len(usage_rows)}")
        record("monitoring_events rows persisted (5)", len(event_rows) == 5, f"count={len(event_rows)}")

        alpha_row = next(
            (r for r in usage_rows if r.package_name == "com.example.alpha"), None
        )
        record(
            "usage FK (device) + ownership (user) correct",
            alpha_row is not None
            and alpha_row.device_id == dev_device_id
            and alpha_row.user_id == DEV_USER_ID
            and alpha_row.usage_date.isoformat() == "2026-08-10",
            str(alpha_row),
        )
        record(
            "duplicate sync produced no duplicate row",
            len([r for r in usage_rows if r.package_name == "com.example.alpha"]) == 1,
            f"alpha rows={len([r for r in usage_rows if r.package_name == 'com.example.alpha'])}",
        )
        record(
            "alpha values overwritten by re-sync",
            alpha_row is not None
            and alpha_row.duration_seconds == 1500
            and alpha_row.launch_count == 4,
            str(alpha_row),
        )

        started = next(
            (e for e in event_rows if e.event_type == "MONITORING_STARTED"), None
        )
        record(
            "event FK (device) + ownership (user) correct",
            started is not None
            and started.device_id == dev_device_id
            and started.user_id == DEV_USER_ID
            and started.occurred_at is not None,
            str(started),
        )

        warning = next(
            (e for e in event_rows if e.event_type == "LIMIT_WARNING"), None
        )
        record(
            "aware timestamp stored as naive UTC",
            warning is not None
            and warning.occurred_at.strftime("%Y-%m-%dT%H:%M:%S") == "2026-08-10T03:30:00",
            str(warning.occurred_at) if warning else "missing",
        )

        event_types = {e.event_type for e in event_rows}
        record(
            "events match submitted actions",
            {
                "MONITORING_STARTED",
                "LIMIT_WARNING",
                "LIMIT_REACHED",
                "APP_RESTRICTED",
                "MONITORING_STOPPED",
            } <= event_types,
            sorted(event_types),
        )
    finally:
        db.close()

    # ------------------------------------------------------------------
    # 8. Regression: existing Settings + Study endpoints still work
    # ------------------------------------------------------------------
    status, body = request("GET", "/settings")
    expect_status("GET /settings -> 200", status, 200, body)
    status, body = request("GET", "/settings/monitoring")
    expect_status("GET /settings/monitoring -> 200", status, 200, body)

    status, body = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, body)
    status, sess = request("POST", "/study/sessions/start", {})
    expect_status("POST /study/sessions/start -> 201", status, 201, sess)
    if status == 201 and isinstance(sess, dict):
        sid = sess.get("id")
        status, ended = request("POST", f"/study/sessions/{sid}/end")
        expect_status("POST /study/sessions/{id}/end -> 200", status, 200, ended)
    status, events = request("GET", "/study/events")
    expect_status("GET /study/events -> 200", status, 200, events)

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
