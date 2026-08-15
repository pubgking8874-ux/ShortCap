"""verify_reports.py — end-to-end verification for the Phase 13 reporting /
insights layer.

Exercises the Reports API (GET /reports/daily, /weekly, /monthly) against a
RUNNING server and a live MySQL database:

  * seeds TODAY's data through the real APIs (study session + break, shorts
    usage sync, app-usage sync + monitoring events, blocked website + web
    events) and seeds YESTERDAY / PREVIOUS-WEEK / PREVIOUS-MONTH rows
    directly in MySQL so previous-period comparisons have real numbers
  * verifies daily / weekly / monthly values, per-platform breakdown, the
    7-day trend, previous-period comparisons (including the zero-guard),
    a no-data period, user isolation, and direct-SQL cross-checks
  * runs a regression pass over Settings / Study / Monitoring / Shorts / Web

All expectations are computed dynamically from the seeded rows and the
actual report date, so the script is robust regardless of the day it runs
(e.g. near month boundaries).

Usage (two terminals, from `backend/`):
    .venv\\Scripts\\python -m uvicorn app.main:app --reload      # terminal 1
    .venv\\Scripts\\python -m scripts.verify_reports             # terminal 2

The script creates its own dev users + devices and cleans them up afterwards.
It never modifies the database schema.
"""

import json
import urllib.error
import urllib.request
from datetime import date, datetime, time, timedelta

from sqlalchemy import text

from app.database import SessionLocal
from app.models.app_usage import AppUsage
from app.models.device import Device
from app.models.monitoring_event import MonitoringEvent
from app.models.shorts_usage import ShortsUsage
from app.models.study_session import StudySession
from app.models.user import User
from app.models.website_event import WebsiteEvent

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90414
OTHER_USER_ID = 90415

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
            device_uuid=f"verify-reports-dev-{DEV_USER_ID}",
            device_name="Verify Dev Device",
            is_active=True,
        )
        other_device = Device(
            user_id=OTHER_USER_ID,
            device_uuid=f"verify-reports-other-{OTHER_USER_ID}",
            device_name="Verify Other Device",
            is_active=True,
        )
        db.add_all([dev_device, other_device])
        db.commit()
        return dev_device.id, other_device.id
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    """Remove rows created by this script (dev users, devices, all domain rows)."""
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(text("DELETE FROM study_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM break_sessions WHERE study_session_id IN "
                            "(SELECT id FROM study_sessions WHERE user_id = :uid)"), {"uid": uid})
            db.execute(text("DELETE FROM study_sessions WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM monitoring_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM app_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM website_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM blocked_websites WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM devices WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def seed_previous_rows(dev_device_id: int, today: date) -> tuple[int, int, int]:
    """Insert YESTERDAY / PREVIOUS-WEEK / PREVIOUS-MONTH rows directly in
    MySQL (the APIs stamp timestamps server-side, so direct inserts are the
    only way to backdate). Returns their study totals for expectations."""
    yesterday = today - timedelta(days=1)
    prev_week = today - timedelta(days=7)  # always in the previous ISO week
    prev_month = (today.replace(day=1) - timedelta(days=1))  # last day of previous month

    db = SessionLocal()
    try:
        # --- YESTERDAY: study 600s / shorts 400s / app 900s / 1 block attempt
        db.add(StudySession(
            user_id=DEV_USER_ID, status="completed",
            started_at=datetime.combine(yesterday, time(11, 0)),
            ended_at=datetime.combine(yesterday, time(12, 0)),
            actual_duration_seconds=600,
        ))
        db.add(ShortsUsage(
            user_id=DEV_USER_ID, device_id=dev_device_id, usage_date=yesterday,
            platform="UNKNOWN", surface="UNKNOWN",
            shorts_count=20, duration_seconds=400,
            warning_triggered=False, limit_reached=False,
        ))
        db.add(AppUsage(
            user_id=DEV_USER_ID, device_id=dev_device_id, package_name="com.example.news",
            app_name="News App", usage_date=yesterday, duration_seconds=900, launch_count=3,
        ))
        db.add(WebsiteEvent(
            user_id=DEV_USER_ID, device_id=dev_device_id, domain="youtube.com",
            event_type="BLOCK_ATTEMPT", occurred_at=datetime.combine(yesterday, time(13, 0)),
        ))
        # --- PREVIOUS WEEK: study 1200s / shorts 200s / app 300s / 1 block attempt
        db.add(StudySession(
            user_id=DEV_USER_ID, status="completed",
            started_at=datetime.combine(prev_week, time(10, 0)),
            ended_at=datetime.combine(prev_week, time(12, 0)),
            actual_duration_seconds=1200,
        ))
        db.add(ShortsUsage(
            user_id=DEV_USER_ID, device_id=dev_device_id, usage_date=prev_week,
            platform="UNKNOWN", surface="UNKNOWN",
            shorts_count=10, duration_seconds=200,
            warning_triggered=False, limit_reached=False,
        ))
        db.add(AppUsage(
            user_id=DEV_USER_ID, device_id=dev_device_id, package_name="com.example.news",
            app_name="News App", usage_date=prev_week, duration_seconds=300, launch_count=1,
        ))
        db.add(WebsiteEvent(
            user_id=DEV_USER_ID, device_id=dev_device_id, domain="youtube.com",
            event_type="BLOCK_ATTEMPT", occurred_at=datetime.combine(prev_week, time(13, 0)),
        ))
        # --- PREVIOUS MONTH: study 500s / shorts 100s / app 150s / 1 block attempt
        db.add(StudySession(
            user_id=DEV_USER_ID, status="completed",
            started_at=datetime.combine(prev_month, time(10, 0)),
            ended_at=datetime.combine(prev_month, time(12, 0)),
            actual_duration_seconds=500,
        ))
        db.add(ShortsUsage(
            user_id=DEV_USER_ID, device_id=dev_device_id, usage_date=prev_month,
            platform="UNKNOWN", surface="UNKNOWN",
            shorts_count=5, duration_seconds=100,
            warning_triggered=False, limit_reached=False,
        ))
        db.add(AppUsage(
            user_id=DEV_USER_ID, device_id=dev_device_id, package_name="com.example.news",
            app_name="News App", usage_date=prev_month, duration_seconds=150, launch_count=1,
        ))
        db.add(WebsiteEvent(
            user_id=DEV_USER_ID, device_id=dev_device_id, domain="youtube.com",
            event_type="BLOCK_ATTEMPT", occurred_at=datetime.combine(prev_month, time(13, 0)),
        ))
        db.commit()
        return 600, 1200, 500
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
    # 1. Seed TODAY's data through the real APIs
    # ------------------------------------------------------------------
    # Study: session -> break -> end break -> end session. The report date is
    # derived from the server-stamped ended_at so expectations can never drift
    # across a midnight boundary.
    status, sess = request("POST", "/study/sessions/start", {})
    expect_status("POST /study/sessions/start -> 201", status, 201, sess)
    sid = sess.get("id")
    status, brk = request("POST", f"/study/sessions/{sid}/breaks/start", {})
    expect_status("POST break start -> 201", status, 201, brk)
    bid = brk.get("id")
    status, brk_end = request("POST", f"/study/breaks/{bid}/end", {})
    expect_status("POST break end -> 200", status, 200, brk_end)
    break_seconds = int(brk_end.get("duration_seconds") or 0)
    status, sess_end = request("POST", f"/study/sessions/{sid}/end", {})
    expect_status("POST /study/sessions/{id}/end -> 200", status, 200, sess_end)
    study_seconds = int(sess_end.get("actual_duration_seconds") or 0)
    today = date.fromisoformat(str(sess_end.get("ended_at"))[:10])
    today_str = today.isoformat()
    print(f"  -> report date (from server timestamps): {today_str}")

    # Shorts: YOUTUBE (100 / 2000, warning) + UNKNOWN (50 / 1000, limit).
    status, synced = request(
        "POST", "/shorts/usage/sync",
        [
            {"device_id": dev_device_id, "usage_date": today_str, "shorts_count": 100,
             "duration_seconds": 2000, "warning_triggered": True, "limit_reached": False,
             "platform": "YOUTUBE", "surface": "YOUTUBE_SHORTS"},
            {"device_id": dev_device_id, "usage_date": today_str, "shorts_count": 50,
             "duration_seconds": 1000, "warning_triggered": False, "limit_reached": True},
        ],
    )
    expect_status("POST /shorts/usage/sync (2 rows) -> 200", status, 200, synced)

    # Monitoring: app usage for 2 apps + 2 monitoring events.
    status, synced = request(
        "POST", "/monitoring/app-usage/sync",
        [
            {"device_id": dev_device_id, "package_name": "com.instagram.android",
             "app_name": "Instagram", "usage_date": today_str, "duration_seconds": 1800, "launch_count": 4},
            {"device_id": dev_device_id, "package_name": "com.google.android.youtube",
             "app_name": "YouTube", "usage_date": today_str, "duration_seconds": 600, "launch_count": 2},
        ],
    )
    expect_status("POST /monitoring/app-usage/sync (2 apps) -> 200", status, 200, synced)
    for etype in ("MONITORING_STARTED", "LIMIT_WARNING"):
        status, _ = request("POST", "/monitoring/events",
                            {"device_id": dev_device_id, "event_type": etype})
        expect_status(f"POST /monitoring/events {etype} -> 201", status, 201)

    # Web: blocked website + 3 events today.
    status, website = request("POST", "/websites/blocked", {"domain": "youtube.com"})
    expect_status("POST /websites/blocked -> 201", status, 201, website)
    wid = website.get("id")
    for etype in ("BLOCK_ATTEMPT", "BLOCKED", "UNBLOCKED"):
        body = {"device_id": dev_device_id, "domain": "youtube.com", "event_type": etype}
        if etype == "BLOCKED":
            body["blocked_website_id"] = wid
        status, _ = request("POST", "/web/events", body)
        expect_status(f"POST /web/events {etype} -> 201", status, 201)

    # Seed previous-period rows directly in MySQL.
    seed_previous_rows(dev_device_id, today)

    # Expected TODAY values (from what was seeded).
    exp = {
        "study_seconds": study_seconds,
        "break_seconds": break_seconds,
        "shorts_count": 150,          # 100 + 50
        "shorts_duration": 3000,      # 2000 + 1000
        "shorts_warning": 1,
        "shorts_limit": 1,
        "app_usage": 2400,            # 1800 + 600
        "monitored_apps": 2,
        "monitoring_events": 2,
        "block_attempts": 1,
        "blocked": 1,
        "unblocked": 1,
        "unique_domains": 1,
    }
    # YESTERDAY / PREV-WEEK / PREV-MONTH rows (from seed_previous_rows).
    prev_day = {"study": 600, "shorts": 400, "app": 900, "blocks": 1}
    prev_week = {"study": 1200, "shorts": 200, "app": 300, "blocks": 1}
    prev_month = {"study": 500, "shorts": 100, "app": 150, "blocks": 1}

    # ------------------------------------------------------------------
    # 2. Daily report
    # ------------------------------------------------------------------
    status, daily = request("GET", f"/reports/daily?date={today_str}")
    expect_status("GET /reports/daily -> 200", status, 200, daily)

    def d_check(prefix: str, r: dict) -> None:
        study = r["study"]
        mon = r["monitoring"]
        shorts = r["shorts"]
        web = r["web"]
        ok = (
            study["total_study_seconds"] == exp["study_seconds"]
            and study["completed_sessions"] == 1
            and study["cancelled_sessions"] == 0
            and study["break_seconds"] == exp["break_seconds"]
            and study["completed_breaks"] == 1
            and mon["total_app_usage_seconds"] == exp["app_usage"]
            and mon["monitored_apps_count"] == exp["monitored_apps"]
            and mon["monitoring_event_count"] == exp["monitoring_events"]
            and shorts["total_shorts_count"] == exp["shorts_count"]
            and shorts["total_duration_seconds"] == exp["shorts_duration"]
            and shorts["warning_count"] == exp["shorts_warning"]
            and shorts["limit_reached_count"] == exp["shorts_limit"]
            and web["total_block_attempts"] == exp["block_attempts"]
            and web["total_blocked_events"] == exp["blocked"]
            and web["total_unblock_events"] == exp["unblocked"]
            and web["unique_blocked_domains"] == exp["unique_domains"]
        )
        record(f"{prefix} domain metrics match seeded data", ok,
               f"study={study} shorts={shorts} web={web} mon={mon}")

    d_check("daily", daily)
    record(
        "daily period is the requested UTC date",
        daily["period"]["type"] == "daily"
        and daily["period"]["start_date"] == today_str
        and daily["period"]["end_date"] == today_str,
        daily["period"],
    )
    record(
        "daily report has no daily_trend (weekly/monthly only)",
        daily.get("daily_trend") is None,
        str(daily.get("daily_trend")),
    )

    # Platform breakdown: only actual platforms, YOUTUBE first (by duration).
    breakdown = daily["shorts"]["platform_breakdown"]
    record(
        "platform breakdown has only YOUTUBE + UNKNOWN (no fabricated platforms)",
        len(breakdown) == 2
        and breakdown[0]["platform"] == "YOUTUBE"
        and breakdown[0]["shorts_count"] == 100
        and breakdown[0]["duration_seconds"] == 2000
        and breakdown[1]["platform"] == "UNKNOWN"
        and breakdown[1]["shorts_count"] == 50
        and breakdown[1]["duration_seconds"] == 1000,
        breakdown,
    )
    record(
        "top apps ranked by duration (Instagram first)",
        daily["monitoring"]["top_apps"][0]["app_name"] == "Instagram"
        and daily["monitoring"]["top_apps"][0]["duration_seconds"] == 1800
        and daily["monitoring"]["top_apps"][1]["app_name"] == "YouTube",
        daily["monitoring"]["top_apps"],
    )

    # Comparison vs YESTERDAY (real numbers; zero-guard exercised later).
    comp = daily["comparison"]
    study_pct = round((exp["study_seconds"] - prev_day["study"]) / prev_day["study"] * 100, 1)
    record(
        "daily comparison vs previous day (real values + percentages)",
        comp["study_seconds"]["previous"] == prev_day["study"]
        and comp["study_seconds"]["change_percent"] == study_pct
        and comp["shorts_seconds"]["current"] == exp["shorts_duration"]
        and comp["shorts_seconds"]["previous"] == prev_day["shorts"]
        and comp["shorts_seconds"]["change_percent"] == 650.0
        and comp["app_usage_seconds"]["previous"] == prev_day["app"]
        and comp["app_usage_seconds"]["change_percent"] == 166.7
        and comp["block_attempts"]["previous"] == prev_day["blocks"]
        and comp["block_attempts"]["change_percent"] == 0.0,
        comp,
    )
    record(
        "include_comparison=false omits the comparison block",
        daily.get("comparison") is not None,
        "comparison should be present by default",
    )
    status, no_comp = request("GET", f"/reports/daily?date={today_str}&include_comparison=false")
    record(
        "include_comparison=false returns no comparison",
        status == 200 and no_comp.get("comparison") is None,
        str(no_comp.get("comparison")),
    )

    # ------------------------------------------------------------------
    # 3. Weekly report
    # ------------------------------------------------------------------
    week_start = today - timedelta(days=today.isoweekday() - 1)  # Monday
    yesterday = today - timedelta(days=1)
    yesterday_in_week = yesterday >= week_start  # False when today is Monday

    w_study = exp["study_seconds"] + (prev_day["study"] if yesterday_in_week else 0)
    w_shorts = exp["shorts_duration"] + (prev_day["shorts"] if yesterday_in_week else 0)
    w_app = exp["app_usage"] + (prev_day["app"] if yesterday_in_week else 0)
    w_blocks = exp["block_attempts"] + (prev_day["blocks"] if yesterday_in_week else 0)
    w_completed = 1 + (1 if yesterday_in_week else 0)

    status, weekly = request("GET", f"/reports/weekly?date={today_str}")
    expect_status("GET /reports/weekly -> 200", status, 200, weekly)
    w = weekly
    record(
        "weekly totals include every day of the ISO week",
        w["study"]["total_study_seconds"] == w_study
        and w["study"]["completed_sessions"] == w_completed
        and w["shorts"]["total_duration_seconds"] == w_shorts
        and w["monitoring"]["total_app_usage_seconds"] == w_app
        and w["web"]["total_block_attempts"] == w_blocks,
        f"study={w['study']} shorts={w['shorts']['total_duration_seconds']} "
        f"app={w['monitoring']['total_app_usage_seconds']} blocks={w['web']['total_block_attempts']}",
    )
    record(
        "weekly period is Monday–Sunday of the ISO week",
        w["period"]["type"] == "weekly"
        and w["period"]["start_date"] == week_start.isoformat()
        and w["period"]["end_date"] == (week_start + timedelta(days=6)).isoformat(),
        w["period"],
    )

    trend = w["daily_trend"]
    record(
        "weekly daily_trend has 7 entries starting Monday",
        isinstance(trend, list) and len(trend) == 7
        and trend[0]["date"] == week_start.isoformat(),
        str(trend[:2]) if trend else "missing",
    )
    today_idx = (today - week_start).days
    record(
        "trend carries today's study seconds at the right index",
        trend[today_idx]["date"] == today.isoformat()
        and trend[today_idx]["study_seconds"] == exp["study_seconds"]
        and trend[today_idx]["shorts_seconds"] == exp["shorts_duration"]
        and trend[today_idx]["app_usage_seconds"] == exp["app_usage"]
        and trend[today_idx]["block_attempts"] == exp["block_attempts"],
        trend[today_idx],
    )
    if yesterday_in_week:
        y_idx = (yesterday - week_start).days
        record(
            "trend carries yesterday's values (600 study / 1 block attempt)",
            trend[y_idx]["study_seconds"] == prev_day["study"]
            and trend[y_idx]["block_attempts"] == prev_day["blocks"],
            trend[y_idx],
        )
    zero_days = sum(1 for t in trend if t["study_seconds"] == 0 and t["block_attempts"] == 0)
    record(
        "trend has honest zero days for days without data",
        zero_days >= 5 - (1 if yesterday_in_week else 0),
        f"zero days={zero_days}",
    )
    record(
        "trend study sum equals the weekly study total",
        sum(t["study_seconds"] for t in trend) == w_study,
        f"sum={sum(t['study_seconds'] for t in trend)} expected={w_study}",
    )
    record(
        "weekly platform breakdown duration sums to the weekly total",
        sum(p["duration_seconds"] for p in w["shorts"]["platform_breakdown"]) == w_shorts,
        w["shorts"]["platform_breakdown"],
    )

    wcomp = w["comparison"]
    w_pct = round((w_study - prev_week["study"]) / prev_week["study"] * 100, 1)
    record(
        "weekly comparison vs previous ISO week",
        wcomp["study_seconds"]["previous"] == prev_week["study"]
        and wcomp["study_seconds"]["change_percent"] == w_pct
        and wcomp["shorts_seconds"]["previous"] == prev_week["shorts"]
        and wcomp["app_usage_seconds"]["previous"] == prev_week["app"]
        and wcomp["block_attempts"]["previous"] == prev_week["blocks"],
        wcomp,
    )

    # ------------------------------------------------------------------
    # 4. Monthly report
    # ------------------------------------------------------------------
    def in_month(d: date) -> bool:
        return d.year == today.year and d.month == today.month

    prev_week_date = today - timedelta(days=7)
    m_study = exp["study_seconds"] \
        + (prev_day["study"] if in_month(yesterday) else 0) \
        + (prev_week["study"] if in_month(prev_week_date) else 0)
    m_shorts = exp["shorts_duration"] \
        + (prev_day["shorts"] if in_month(yesterday) else 0) \
        + (prev_week["shorts"] if in_month(prev_week_date) else 0)
    m_app = exp["app_usage"] \
        + (prev_day["app"] if in_month(yesterday) else 0) \
        + (prev_week["app"] if in_month(prev_week_date) else 0)
    m_blocks = exp["block_attempts"] \
        + (prev_day["blocks"] if in_month(yesterday) else 0) \
        + (prev_week["blocks"] if in_month(prev_week_date) else 0)

    status, monthly = request("GET", f"/reports/monthly?date={today_str}")
    expect_status("GET /reports/monthly -> 200", status, 200, monthly)
    m = monthly
    record(
        "monthly totals include every day of the calendar month",
        m["study"]["total_study_seconds"] == m_study
        and m["shorts"]["total_duration_seconds"] == m_shorts
        and m["monitoring"]["total_app_usage_seconds"] == m_app
        and m["web"]["total_block_attempts"] == m_blocks,
        f"study={m['study']['total_study_seconds']} shorts={m['shorts']['total_duration_seconds']} "
        f"app={m['monitoring']['total_app_usage_seconds']} blocks={m['web']['total_block_attempts']}",
    )
    record(
        "monthly period spans the full calendar month",
        m["period"]["type"] == "monthly"
        and m["period"]["start_date"] == today.replace(day=1).isoformat()
        and m["period"]["label"] == today.strftime("%Y-%m"),
        m["period"],
    )
    mcomp = m["comparison"]
    m_pct = round((m_study - prev_month["study"]) / prev_month["study"] * 100, 1)
    record(
        "monthly comparison vs previous calendar month",
        mcomp["study_seconds"]["previous"] == prev_month["study"]
        and mcomp["study_seconds"]["change_percent"] == m_pct
        and mcomp["shorts_seconds"]["previous"] == prev_month["shorts"]
        and mcomp["app_usage_seconds"]["previous"] == prev_month["app"]
        and mcomp["block_attempts"]["previous"] == prev_month["blocks"],
        mcomp,
    )
    m_trend = m["daily_trend"]
    first_of_month = today.replace(day=1)
    next_first = (first_of_month + timedelta(days=32)).replace(day=1)
    days_in_month = (next_first - first_of_month).days
    record(
        "monthly daily_trend spans every day of the month",
        len(m_trend) == days_in_month,
        f"trend days={len(m_trend)} expected={days_in_month}",
    )
    record(
        "monthly trend study sum equals the monthly study total",
        sum(t["study_seconds"] for t in m_trend) == m_study,
        f"sum={sum(t['study_seconds'] for t in m_trend)} expected={m_study}",
    )

    # ------------------------------------------------------------------
    # 5. No-data period (valid empty structure, not an error). Chosen far
    #    enough back (400 days) that its whole week AND month contain none
    #    of the seeded rows (which all live within ~31 days of today).
    # ------------------------------------------------------------------
    no_data = today - timedelta(days=400)
    status, empty = request("GET", f"/reports/daily?date={no_data.isoformat()}")
    expect_status("GET /reports/daily (no data) -> 200", status, 200, empty)
    record(
        "no-data daily report is an all-zero structure",
        empty["study"]["total_study_seconds"] == 0
        and empty["study"]["completed_sessions"] == 0
        and empty["monitoring"]["total_app_usage_seconds"] == 0
        and empty["monitoring"]["monitored_apps_count"] == 0
        and empty["monitoring"]["top_apps"] == []
        and empty["shorts"]["total_shorts_count"] == 0
        and empty["shorts"]["platform_breakdown"] == []
        and empty["web"]["total_block_attempts"] == 0
        and empty["web"]["unique_blocked_domains"] == 0,
        empty,
    )
    # Zero-guard: previous period also empty -> change_percent is None.
    record(
        "no-data comparison change_percent is None (zero-guard, never fake)",
        empty["comparison"]["study_seconds"]["previous"] == 0
        and empty["comparison"]["study_seconds"]["change_percent"] is None
        and empty["comparison"]["shorts_seconds"]["change_percent"] is None,
        empty["comparison"],
    )
    status, empty_w = request("GET", f"/reports/weekly?date={no_data.isoformat()}")
    record(
        "no-data weekly report is zero + 7 zero trend days",
        status == 200
        and empty_w["study"]["total_study_seconds"] == 0
        and len(empty_w["daily_trend"]) == 7
        and all(t["study_seconds"] == 0 for t in empty_w["daily_trend"]),
        f"status={status} trend_days={len(empty_w.get('daily_trend', []))}",
    )
    status, empty_m = request("GET", f"/reports/monthly?date={no_data.isoformat()}")
    record(
        "no-data monthly report is zero",
        status == 200 and empty_m["study"]["total_study_seconds"] == 0
        and empty_m["shorts"]["total_duration_seconds"] == 0,
        f"status={status}",
    )

    # ------------------------------------------------------------------
    # 6. User isolation (another user's report is all zeros)
    # ------------------------------------------------------------------
    status, other = request("GET", f"/reports/daily?date={today_str}", user_id=OTHER_USER_ID)
    record(
        "other user sees a zero report (no cross-user aggregation)",
        status == 200
        and other["study"]["total_study_seconds"] == 0
        and other["shorts"]["total_shorts_count"] == 0
        and other["monitoring"]["total_app_usage_seconds"] == 0
        and other["web"]["total_block_attempts"] == 0,
        other,
    )

    # ------------------------------------------------------------------
    # 7. Direct SQL verification (independent of the API)
    # ------------------------------------------------------------------
    db = SessionLocal()
    try:
        start_dt, end_dt = (
            datetime.combine(today, time.min),
            datetime.combine(today, time.max),
        )
        sql_study = db.execute(
            text(
                "SELECT COALESCE(SUM(actual_duration_seconds),0) FROM study_sessions "
                "WHERE user_id = :uid AND status IN ('completed','cancelled') "
                "AND ended_at BETWEEN :s AND :e"
            ),
            {"uid": DEV_USER_ID, "s": start_dt, "e": end_dt},
        ).scalar()
        sql_shorts = db.execute(
            text(
                "SELECT COALESCE(SUM(duration_seconds),0) FROM shorts_usage "
                "WHERE user_id = :uid AND usage_date = :d"
            ),
            {"uid": DEV_USER_ID, "d": today},
        ).scalar()
        sql_app = db.execute(
            text(
                "SELECT COALESCE(SUM(duration_seconds),0) FROM app_usage "
                "WHERE user_id = :uid AND usage_date = :d"
            ),
            {"uid": DEV_USER_ID, "d": today},
        ).scalar()
        sql_web = db.execute(
            text(
                "SELECT COUNT(*) FROM website_events "
                "WHERE user_id = :uid AND event_type = 'BLOCK_ATTEMPT' "
                "AND occurred_at BETWEEN :s AND :e"
            ),
            {"uid": DEV_USER_ID, "s": start_dt, "e": end_dt},
        ).scalar()
    finally:
        db.close()

    record(
        "direct SQL study total == API daily study total",
        int(sql_study or 0) == exp["study_seconds"],
        f"SQL={sql_study} API={exp['study_seconds']}",
    )
    record(
        "direct SQL shorts total == API daily shorts total",
        int(sql_shorts or 0) == exp["shorts_duration"],
        f"SQL={sql_shorts} API={exp['shorts_duration']}",
    )
    record(
        "direct SQL app-usage total == API daily app-usage total",
        int(sql_app or 0) == exp["app_usage"],
        f"SQL={sql_app} API={exp['app_usage']}",
    )
    record(
        "direct SQL block-attempt count == API daily web count",
        int(sql_web or 0) == exp["block_attempts"],
        f"SQL={sql_web} API={exp['block_attempts']}",
    )

    # ------------------------------------------------------------------
    # 8. Regression: Settings + Study + Monitoring + Shorts + Web
    # ------------------------------------------------------------------
    status, body = request("GET", "/settings")
    expect_status("GET /settings -> 200", status, 200, body)
    status, body = request("GET", "/settings/monitoring")
    expect_status("GET /settings/monitoring -> 200", status, 200, body)
    status, body = request("GET", "/settings/shorts")
    expect_status("GET /settings/shorts -> 200", status, 200, body)

    status, body = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, body)
    status, body = request("GET", "/study/sessions")
    expect_status("GET /study/sessions -> 200", status, 200, body)

    status, body = request("GET", "/monitoring/app-usage")
    expect_status("GET /monitoring/app-usage -> 200", status, 200, body)
    status, body = request("GET", "/monitoring/summary")
    expect_status("GET /monitoring/summary -> 200", status, 200, body)

    status, body = request("GET", "/shorts/usage")
    expect_status("GET /shorts/usage -> 200", status, 200, body)
    status, body = request("GET", "/shorts/summary")
    expect_status("GET /shorts/summary -> 200", status, 200, body)

    status, body = request("GET", "/websites/blocked")
    expect_status("GET /websites/blocked -> 200", status, 200, body)
    status, body = request("GET", "/web/events")
    expect_status("GET /web/events -> 200", status, 200, body)

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
