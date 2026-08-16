"""verify_short_control.py — end-to-end verification for the Shorts Control
backend domain (24-hour limit cycle + HUD preference + insights).

Exercises the full Shorts Control flow against a RUNNING server and a live
MySQL database:

  * Shorts Control combined state (applications catalog / limit cycle / HUD /
    insights)
  * limit-cycle activation (24h window, single active cycle), disable, expiry
  * count synchronization reconciled from usage sync + duplicate protection
  * warning state and LIMIT_REACHED persistence
  * limit change preserving count + 24-hour timer
  * user isolation + device ownership
  * insights (Yesterday / Today / This Week / This Month) + platform breakdown
  * regressions: settings / study / monitoring / reports / score / rank

Then verifies the rows directly in MySQL (including the single-active unique
constraint). It never modifies the database schema.

Usage (two terminals, from `backend/`):
    .venv\\Scripts\\python -m uvicorn app.main:app --port 8000   # terminal 1
    .venv\\Scripts\\python -m scripts.verify_short_control       # terminal 2

The script creates its own dev users + devices and cleans them up afterwards.
"""

import json
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone

from sqlalchemy import func, text

from app.database import SessionLocal
from app.models.device import Device
from app.models.shorts_limit_cycle import ShortsLimitCycle
from app.models.shorts_usage import ShortsUsage
from app.models.user import User

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90420
OTHER_USER_ID = 90421

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
            device_uuid=f"verify-shortcontrol-dev-{DEV_USER_ID}",
            device_name="Verify ShortControl Dev Device",
            is_active=True,
        )
        other_device = Device(
            user_id=OTHER_USER_ID,
            device_uuid=f"verify-shortcontrol-other-{OTHER_USER_ID}",
            device_name="Verify ShortControl Other Device",
            is_active=True,
        )
        db.add_all([dev_device, other_device])
        db.commit()
        return dev_device.id, other_device.id
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    """Remove rows created by this script."""
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(text("DELETE FROM shorts_limit_cycles WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_settings WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM devices WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def main() -> None:
    # Defensive: clear leftovers from a previously interrupted run first.
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    dev_device_id, other_device_id = setup_devices()
    today = date.today().isoformat()
    yesterday = (date.today() - timedelta(days=1)).isoformat()

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
    # 1. Shorts Control combined state (no cycle yet)
    # ------------------------------------------------------------------
    status, control = request("GET", "/shorts/control")
    expect_status("GET /shorts/control -> 200", status, 200, control)
    apps_ok = (
        isinstance(control, dict)
        and isinstance(control.get("applications"), dict)
        and {p["id"] for p in control["applications"]["platforms"]}
        == {
            "youtube_shorts",
            "instagram_reels",
            "tiktok_short_feed",
            "snapchat_spotlight",
            "facebook_reels",
            "moj_short_video",
            "x_short_video",
            "linkedin_short_video",
        }
        and len(control["applications"]["platforms"]) == 8
    )
    record("control: canonical 8-platform applications catalog", apps_ok, str(control.get("applications"))[:200])
    record(
        "control: hud appearance defaults to BRAIN",
        isinstance(control, dict) and control.get("hud", {}).get("appearance") == "BRAIN",
        str(control.get("hud")),
    )
    record(
        "control: limit_cycle is null before activation",
        isinstance(control, dict) and control.get("limit_cycle") is None,
        str(control.get("limit_cycle")),
    )
    record(
        "control: insights present for all four periods",
        isinstance(control, dict)
        and all(
            k in control.get("insights", {})
            for k in ("yesterday", "today", "this_week", "this_month")
        ),
        str(list(control.get("insights", {}).keys())),
    )

    # ------------------------------------------------------------------
    # 2. Activate a 24-hour cycle (limit 200)
    # ------------------------------------------------------------------
    status, cycle = request("POST", "/shorts/limit-cycle/activate", {"limit_count": 200})
    expect_status("POST /shorts/limit-cycle/activate -> 200", status, 200, cycle)
    cycle_id = cycle.get("id") if isinstance(cycle, dict) else None
    record(
        "activated cycle: ACTIVE, count 0, limit 200, user attached",
        status == 200
        and isinstance(cycle, dict)
        and cycle.get("status") == "ACTIVE"
        and cycle.get("current_count") == 0
        and cycle.get("limit_count") == 200
        and cycle.get("user_id") == DEV_USER_ID,
        cycle,
    )
    started = cycle.get("cycle_started_at")
    expires = cycle.get("cycle_expires_at")
    record(
        "cycle window is exactly 24 hours (start -> expires)",
        isinstance(started, str)
        and isinstance(expires, str)
        and (
            datetime.fromisoformat(expires) - datetime.fromisoformat(started)
            == timedelta(hours=24)
        ),
        f"started={started} expires={expires}",
    )

    # Activating again returns the SAME cycle (never a second one).
    status, cycle2 = request("POST", "/shorts/limit-cycle/activate", {"limit_count": 200})
    record(
        "second activate returns the existing cycle (single active cycle)",
        status == 200 and isinstance(cycle2, dict) and cycle2.get("id") == cycle_id,
        f"first={cycle_id} second={cycle2.get('id') if isinstance(cycle2, dict) else None}",
    )

    # GET /shorts/limit-cycle reflects the active window.
    status, cycle = request("GET", "/shorts/limit-cycle")
    expect_status("GET /shorts/limit-cycle -> 200", status, 200, cycle)
    record(
        "limit-cycle: remaining_seconds ~ 24h and usage_ratio 0.0",
        isinstance(cycle, dict)
        and abs((cycle.get("remaining_seconds") or 0) - 86400) < 120
        and cycle.get("usage_ratio") == 0.0,
        f"remaining={cycle.get('remaining_seconds')} ratio={cycle.get('usage_ratio')}",
    )

    # ------------------------------------------------------------------
    # 3. HUD preference persistence
    # ------------------------------------------------------------------
    status, control = request("PUT", "/shorts/control", {"hud_appearance": "LIVE_COUNTER"})
    expect_status("PUT /shorts/control (hud_appearance) -> 200", status, 200, control)
    record(
        "control: hud appearance updated + persisted",
        status == 200 and control.get("hud", {}).get("appearance") == "LIVE_COUNTER",
        str(control.get("hud")),
    )
    status, settings = request("GET", "/settings/shorts")
    record(
        "settings/shorts reflects hud_appearance (LIVE_COUNTER)",
        status == 200 and settings.get("hud_appearance") == "LIVE_COUNTER",
        str(settings.get("hud_appearance")),
    )

    # ------------------------------------------------------------------
    # 4. Count synchronization + duplicate protection + warning
    # ------------------------------------------------------------------
    # Configure a count-based warning BEFORE syncing usage.
    status, control = request("PUT", "/shorts/control", {"warning_count": 1})
    expect_status("PUT /shorts/control (warning_count=1) -> 200", status, 200, control)

    def sync_one_short(count: int, day: str = today) -> int:
        """Sync one daily usage summary; return the API status code."""
        status, body = request(
            "POST",
            "/shorts/usage/sync",
            {
                "device_id": dev_device_id,
                "usage_date": day,
                "shorts_count": count,
                "duration_seconds": count * 4,
                "warning_triggered": False,
                "limit_reached": False,
                "platform": "YOUTUBE",
                "surface": "YOUTUBE_SHORTS",
            },
        )
        return status

    status = sync_one_short(1)
    expect_status("sync 1 short -> 200", status, 200)
    status, cycle = request("GET", "/shorts/limit-cycle")
    record(
        "cycle count reconciled to 1 from synchronized usage",
        status == 200 and cycle.get("current_count") == 1,
        f"count={cycle.get('current_count')}",
    )
    record(
        "warning triggered once count >= warning_count",
        status == 200 and cycle.get("warning_triggered") is True,
        f"warning={cycle.get('warning_triggered')}",
    )

    # Repeat the EXACT same sync -> count must stay 1 (no double increment).
    status = sync_one_short(1)
    expect_status("duplicate sync -> 200", status, 200)
    status, cycle = request("GET", "/shorts/limit-cycle")
    record(
        "duplicate sync does not double the cycle count (idempotent)",
        status == 200 and cycle.get("current_count") == 1,
        f"count={cycle.get('current_count')}",
    )

    # ------------------------------------------------------------------
    # 5. Limit reached (persists across reads)
    # ------------------------------------------------------------------
    status = sync_one_short(3)
    expect_status("sync count=3 -> 200", status, 200)
    status, cycle = request("GET", "/shorts/limit-cycle")
    record(
        "cycle count reconciled to 3",
        status == 200 and cycle.get("current_count") == 3,
        f"count={cycle.get('current_count')}",
    )
    # Lower the limit below the count -> LIMIT_REACHED, persisted.
    status, control = request("PUT", "/shorts/control", {"limit_count": 3})
    expect_status("PUT /shorts/control (limit_count=3) -> 200", status, 200, control)
    status, cycle = request("GET", "/shorts/limit-cycle")
    record(
        "LIMIT_REACHED when count >= limit (persists on GET)",
        status == 200
        and cycle.get("status") == "LIMIT_REACHED"
        and cycle.get("limit_reached") is True,
        f"status={cycle.get('status')} limit_reached={cycle.get('limit_reached')}",
    )

    # ------------------------------------------------------------------
    # 6. Limit change preserves count + 24-hour timer
    # ------------------------------------------------------------------
    status, control = request("PUT", "/shorts/control", {"limit_count": 10})
    expect_status("PUT /shorts/control (limit_count=10) -> 200", status, 200, control)
    status, cycle = request("GET", "/shorts/limit-cycle")
    record(
        "limit change preserves count (3) and start time; status back to ACTIVE",
        status == 200
        and cycle.get("current_count") == 3
        and cycle.get("limit_count") == 10
        and cycle.get("status") == "ACTIVE"
        and cycle.get("cycle_started_at") == started,
        f"count={cycle.get('current_count')} limit={cycle.get('limit_count')} "
        f"status={cycle.get('status')} started={cycle.get('cycle_started_at')}",
    )

    # ------------------------------------------------------------------
    # 7. Expiry (timestamp-driven — mark past, then reads treat it as gone)
    # ------------------------------------------------------------------
    db = SessionLocal()
    try:
        row = (
            db.query(ShortsLimitCycle)
            .filter(ShortsLimitCycle.id == cycle_id)
            .first()
        )
        row.cycle_expires_at = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(minutes=1)
        db.commit()
    finally:
        db.close()

    status, _ = request("GET", "/shorts/limit-cycle")
    expect_status("expired cycle -> GET /shorts/limit-cycle 404", status, 404)
    status, control = request("GET", "/shorts/control")
    record(
        "expired cycle -> control limit_cycle null",
        status == 200 and control.get("limit_cycle") is None,
        str(control.get("limit_cycle")),
    )
    db = SessionLocal()
    try:
        row = (
            db.query(ShortsLimitCycle)
            .filter(ShortsLimitCycle.id == cycle_id)
            .first()
        )
        record(
            "expired cycle marked EXPIRED in DB (is_active freed)",
            row is not None and row.status == "EXPIRED" and row.is_active is None,
            f"status={row.status if row else None} is_active={row.is_active if row else None}",
        )
        # A new activation after expiry creates a NEW cycle (different id).
        db.close()
    finally:
        pass
    status, cycle3 = request("POST", "/shorts/limit-cycle/activate", {"limit_count": 200})
    record(
        "new activation after expiry creates a NEW cycle",
        status == 200 and isinstance(cycle3, dict) and cycle3.get("id") != cycle_id,
        f"old={cycle_id} new={cycle3.get('id') if isinstance(cycle3, dict) else None}",
    )

    # ------------------------------------------------------------------
    # 8. Disable
    # ------------------------------------------------------------------
    status, cycle = request("POST", "/shorts/limit-cycle/disable")
    record(
        "disable -> cycle DISABLED, no longer returned by GET",
        status == 200 and cycle.get("status") == "DISABLED",
        str(cycle),
    )
    status, _ = request("GET", "/shorts/limit-cycle")
    expect_status("after disable -> GET /shorts/limit-cycle 404", status, 404)

    # ------------------------------------------------------------------
    # 9. User isolation + device ownership
    # ------------------------------------------------------------------
    status, _ = request("GET", "/shorts/limit-cycle", user_id=OTHER_USER_ID)
    expect_status("other user sees no cycle (404)", status, 404)
    status, control = request("GET", "/shorts/control", user_id=OTHER_USER_ID)
    record(
        "other user control: no cycle, no usage in insights",
        status == 200
        and control.get("limit_cycle") is None
        and control["insights"]["today"]["total_shorts_count"] == 0,
        str(control.get("limit_cycle")),
    )
    status, _ = request(
        "POST",
        "/shorts/limit-cycle/activate",
        {"limit_count": 100, "device_id": other_device_id},
    )
    expect_status("activating with another user's device -> 404", status, 404)
    status, _ = request(
        "POST",
        "/shorts/usage/sync",
        {
            "device_id": other_device_id,
            "usage_date": today,
            "shorts_count": 9,
            "platform": "YOUTUBE",
            "surface": "YOUTUBE_SHORTS",
        },
    )
    expect_status("usage sync with another user's device -> 404", status, 404)

    # ------------------------------------------------------------------
    # 10. Insights (Yesterday / Today / This Week / This Month) + platform
    #     breakdown, verified against direct SQL below
    # ------------------------------------------------------------------
    # Reactivate, then seed usage for yesterday + today on a distinct platform.
    status, cycle = request("POST", "/shorts/limit-cycle/activate", {"limit_count": 200})
    expect_status("reactivate for insights -> 200", status, 200, cycle)
    status = sync_one_short(4, day=today)
    expect_status("sync today=4 -> 200", status, 200)
    status, _ = request(
        "POST",
        "/shorts/usage/sync",
        {
            "device_id": dev_device_id,
            "usage_date": yesterday,
            "shorts_count": 7,
            "duration_seconds": 28,
            "warning_triggered": True,
            "limit_reached": False,
            "platform": "INSTAGRAM",
            "surface": "INSTAGRAM_REELS",
        },
    )
    expect_status("sync yesterday=7 (INSTAGRAM) -> 200", status, 200)

    status, control = request("GET", "/shorts/control")
    ins = control.get("insights", {}) if isinstance(control, dict) else {}
    record(
        "insights today=4, yesterday=7 (real stored data)",
        status == 200
        and ins.get("today", {}).get("total_shorts_count") == 4
        and ins.get("yesterday", {}).get("total_shorts_count") == 7,
        f"today={ins.get('today')} yesterday={ins.get('yesterday')}",
    )
    record(
        "insights this_week / this_month include the seeded counts",
        status == 200
        and ins.get("this_week", {}).get("total_shorts_count") >= 11
        and ins.get("this_month", {}).get("total_shorts_count") >= 11,
        f"week={ins.get('this_week', {}).get('total_shorts_count')} "
        f"month={ins.get('this_month', {}).get('total_shorts_count')}",
    )
    today_breakdown = {p["platform"]: p["shorts_count"] for p in ins.get("today", {}).get("platform_breakdown", [])}
    record(
        "platform breakdown uses real stored platforms (YOUTUBE)",
        today_breakdown.get("YOUTUBE") == 4,
        str(today_breakdown),
    )
    record(
        "warning flag persisted in insights (yesterday warning_count=1)",
        status == 200 and ins.get("yesterday", {}).get("warning_count") == 1,
        str(ins.get("yesterday", {}).get("warning_count")),
    )

    # ------------------------------------------------------------------
    # 11. MySQL persistence + constraint checks (direct, independent of API)
    # ------------------------------------------------------------------
    db = SessionLocal()
    try:
        rows = db.query(ShortsLimitCycle).filter(ShortsLimitCycle.user_id == DEV_USER_ID).all()
        record(
            "shorts_limit_cycles rows persisted (2 active-era + expired + disabled)",
            len(rows) >= 3,
            f"count={len(rows)}",
        )
        active_rows = [r for r in rows if r.is_active is True]
        record(
            "at most one ACTIVE cycle row per user (is_active=True)",
            len(active_rows) <= 1,
            f"active_rows={len(active_rows)}",
        )
        current = next((r for r in rows if r.is_active is True), None)
        record(
            "active cycle ownership + window + limit correct in DB",
            current is not None
            and current.user_id == DEV_USER_ID
            and current.limit_count == 200
            and current.current_count == 4
            and current.cycle_expires_at - current.cycle_started_at == timedelta(hours=24),
            str(current),
        )

        constraint = db.execute(
            text(
                "SELECT constraint_name FROM information_schema.TABLE_CONSTRAINTS "
                "WHERE table_schema = DATABASE() AND table_name = 'shorts_limit_cycles' "
                "AND constraint_type = 'UNIQUE' "
                "AND constraint_name = 'uq_shorts_limit_cycles_user_active'"
            )
        ).first()
        record(
            "single-active unique constraint exists (user_id, is_active)",
            constraint is not None,
            str(constraint),
        )

        # Direct SQL comparison for today's insights.
        sql_total = (
            db.query(func.coalesce(func.sum(ShortsUsage.shorts_count), 0))
            .filter(
                ShortsUsage.user_id == DEV_USER_ID,
                ShortsUsage.usage_date == date.today(),
            )
            .scalar()
            or 0
        )
        record(
            "insights today matches direct SQL (4)",
            int(sql_total) == 4,
            f"sql={int(sql_total)}",
        )
    finally:
        db.close()

    # ------------------------------------------------------------------
    # 12. Regressions (settings / study / monitoring / reports / score / rank)
    # ------------------------------------------------------------------
    status, body = request("GET", "/settings")
    expect_status("GET /settings -> 200", status, 200, body)
    status, body = request("GET", "/settings/shorts")
    expect_status("GET /settings/shorts -> 200", status, 200, body)
    status, body = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, body)
    status, body = request("GET", "/monitoring/app-usage")
    expect_status("GET /monitoring/app-usage -> 200", status, 200, body)
    status, body = request("GET", "/reports/daily")
    expect_status("GET /reports/daily -> 200", status, 200, body)
    status, body = request("GET", "/score/daily")
    expect_status("GET /score/daily -> 200", status, 200, body)
    status, body = request("GET", "/rank/weekly")
    expect_status("GET /rank/weekly -> 200", status, 200, body)
    status, body = request("GET", "/shorts/summary")
    expect_status("GET /shorts/summary -> 200", status, 200, body)

    # ------------------------------------------------------------------
    # 13. Cleanup
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
