"""verify_score.py — end-to-end verification for the Phase 14B Your Score
engine.

Seeds controlled data (study sessions, Shorts usage, app usage, web events,
a monitoring event and a blocked website) directly in MySQL at known dates,
then verifies the /score API against an INDEPENDENT implementation of the
approved Phase 14A formulas written in this script (from the spec document —
not imported from the app). API and independent math must agree.

Covered scenarios (per the phase requirements):
  1. normal productive profile (daily = 100, all components maxed)
  2. low-study / high-distraction day (imperfect day)
  3. high-Shorts profile (previous week)
  4. high-study day (month-before-last day)
  5. inactivity (no-data date -> 0, insufficient_data)
  6. missing-data behavior (study-only day -> neutral components)
  7. score boundary (0 <= score <= 100 everywhere)
  8. component breakdown (names, points, max, sum)
  9. deterministic repeat calculation (weekly called twice -> identical)
 10. user isolation (another user's score is 0 / insufficient_data)
 11. daily score
 12. weekly score
 13. monthly score

Also verifies: Settings / Study / Monitoring / Shorts / Web / Reports
regression, GET /, /health/db and /docs.

The script creates its own dev users and cleans up afterwards. It never
modifies the database schema.
"""

import json
import urllib.error
import urllib.request
from datetime import date, datetime, time, timedelta

from sqlalchemy import text

from app.database import SessionLocal
from app.models.app_usage import AppUsage
from app.models.blocked_website import BlockedWebsite
from app.models.device import Device
from app.models.monitoring_event import MonitoringEvent
from app.models.shorts_usage import ShortsUsage
from app.models.study_session import StudySession
from app.models.user import User
from app.models.website_event import WebsiteEvent

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90416
OTHER_USER_ID = 90417

# Approved constants (mirror backend/app/services/scoring/constants.py and
# backend/docs/your_score_spec.md) — used by the INDEPENDENT implementation.
WEIGHTS = {"study": 40, "shorts": 25, "distraction": 20, "web": 10, "consistency": 5}
MIN_SESSION_SEC = 300
STUDY_TARGET = 150          # min/day-equivalent
SHORTS_DEFAULT_LIMIT = 30   # min/day
DISTRACTION_THRESHOLD = 240  # min/day
REQUIRED_DAYS = {"daily": 1, "weekly": 3, "monthly": 7}
CONSISTENCY_TARGET = {"daily": 1, "weekly": 5, "monthly": 20}

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


# ---------------------------------------------------------------------------
# Seeded test data (known dates; the same lists feed the independent math)
# ---------------------------------------------------------------------------

TODAY: date = date.today()
YESTERDAY = TODAY - timedelta(days=1)
PREV_WEEK = TODAY - timedelta(days=7)      # always in the previous ISO week
PREV_MONTH = (TODAY.replace(day=1) - timedelta(days=1))
# 20 days back: outside the prev-week expansion (today-7..today-2), today,
# yesterday AND the prev-month row (>= 28 days back) — collision-proof.
STUDY_ONLY_DAY = TODAY - timedelta(days=20)
NO_DATA_DAY = TODAY - timedelta(days=400)

SEEDS = {
    "sessions": [
        {"date": TODAY, "status": "completed", "seconds": 9000},          # 150 min
        {"date": YESTERDAY, "status": "completed", "seconds": 3600},      # 60 min
        {"date": YESTERDAY, "status": "cancelled", "seconds": 3600},      # 60 min
        {"date": STUDY_ONLY_DAY, "status": "completed", "seconds": 5400}, # 90 min
        {"date": PREV_WEEK, "status": "completed", "seconds": 7200},      # 120 min
        {"date": PREV_MONTH, "status": "completed", "seconds": 5400},     # 90 min
    ],
    "shorts": [
        {"date": TODAY, "minutes": 20, "warning": False, "limit": False},
        {"date": YESTERDAY, "minutes": 60, "warning": True, "limit": True},
        {"date": PREV_WEEK, "minutes": 120, "warning": True, "limit": True},  # x7 days
        {"date": PREV_MONTH, "minutes": 15, "warning": False, "limit": False},
    ],
    "app": [
        {"date": TODAY, "minutes": 90},
        {"date": YESTERDAY, "minutes": 300},
        {"date": PREV_WEEK, "minutes": 200},  # x7 days
        {"date": PREV_MONTH, "minutes": 60},
    ],
    "web_events": [
        {"date": YESTERDAY, "type": "BLOCK_ATTEMPT", "domain": "x.com"},
        {"date": PREV_WEEK, "type": "BLOCK_ATTEMPT", "domain": "x.com"},
        {"date": PREV_WEEK, "type": "BLOCK_ATTEMPT", "domain": "x.com"},
        {"date": PREV_WEEK, "type": "BLOCK_ATTEMPT", "domain": "x.com"},
    ],
    "monitoring_events": [
        {"date": YESTERDAY, "type": "LIMIT_REACHED"},
    ],
}


def in_range(d: date, start: date, end: date) -> bool:
    return start <= d <= end


def period_bounds(period_type: str, report_date: date) -> tuple[date, date]:
    if period_type == "daily":
        return report_date, report_date
    if period_type == "weekly":
        start = report_date - timedelta(days=report_date.isoweekday() - 1)
        return start, start + timedelta(days=6)
    start = report_date.replace(day=1)
    next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
    return start, next_month - timedelta(days=1)


def expand(seeds: list[dict], key: str) -> list[dict]:
    """Expand shorthand seeds: a seed with a repeat marker counts on every
    day of its week (prev-week Shorts/app are 7-day patterns)."""
    out = []
    for s in seeds:
        out.append(s)
        if s.get("date") == PREV_WEEK:
            # 6 extra days (offsets 1..5 -> up to today-2) so the prev-week
            # pattern never collides with yesterday's explicit seeds.
            for offset in range(1, 6):
                out.append({**s, "date": PREV_WEEK + timedelta(days=offset)})
    return out


# ---------------------------------------------------------------------------
# INDEPENDENT implementation of the approved formulas (from the spec doc)
# ---------------------------------------------------------------------------


def exp_study(sessions: list[dict], days: int) -> float:
    meaningful = [
        s for s in sessions
        if s["status"] in ("completed", "cancelled") and s["seconds"] >= MIN_SESSION_SEC
    ]
    if not meaningful:
        return 0.5
    c = sum(1 for s in meaningful if s["status"] == "completed") / len(meaningful)
    total_min = sum(s["seconds"] for s in meaningful) / 60
    q = min(1.0, total_min / (STUDY_TARGET * days))
    return round(0.6 * c + 0.4 * q, 4)


def exp_shorts(days_data: list[dict], limit: int | None) -> float:
    days_data = [d for d in days_data if d["minutes"] > 0]
    if not days_data:
        return 0.5
    limit = limit if limit and limit > 0 else SHORTS_DEFAULT_LIMIT
    total, weight = 0.0, 0.0
    for d in days_data:
        u = d["minutes"]
        s = 1.0 if u <= limit else max(0.0, 1.0 - (u - limit) / limit)
        if d["limit"]:
            s *= 0.9
        elif d["warning"]:
            s *= 0.95
        total += s * u
        weight += u
    return round(total / weight, 4)


def exp_distraction(days_data: list[dict], enforcement: int) -> float:
    days_data = [d for d in days_data if d["minutes"] > 0]
    if not days_data:
        return 0.5
    total, weight = 0.0, 0.0
    for d in days_data:
        x = d["minutes"]
        s = 1.0 if x <= DISTRACTION_THRESHOLD else max(
            0.0, 1.0 - (x - DISTRACTION_THRESHOLD) / DISTRACTION_THRESHOLD
        )
        total += s * x
        weight += x
    value = total / weight
    if enforcement > 0:
        value *= 1 - min(0.10, 0.02 * enforcement)
    return round(value, 4)


def exp_web(events: list[dict], blocked_active: int) -> float:
    attempts = sum(1 for e in events if e["type"] == "BLOCK_ATTEMPT")
    unblocks = sum(1 for e in events if e["type"] == "UNBLOCKED")
    counts: dict[str, int] = {}
    for e in events:
        if e["type"] == "BLOCK_ATTEMPT" and e.get("domain"):
            counts[e["domain"]] = counts.get(e["domain"], 0) + 1
    repeats = sum(1 for n in counts.values() if n >= 3)
    if blocked_active == 0 and not events:
        return 0.5
    if blocked_active > 0 and attempts == 0:
        return 1.0
    p = min(0.15, 0.05 * attempts) + min(0.10, 0.05 * unblocks) + min(0.10, 0.05 * repeats)
    return round(max(0.5, 1.0 - p), 4)


def exp_score(period_type: str, report_date: date, shorts_limit: int | None = None) -> dict:
    start, end = period_bounds(period_type, report_date)
    days = (end - start).days + 1

    sessions = [s for s in SEEDS["sessions"] if in_range(s["date"], start, end)]
    shorts = [s for s in expand(SEEDS["shorts"], "shorts") if in_range(s["date"], start, end)]
    app = [s for s in expand(SEEDS["app"], "app") if in_range(s["date"], start, end)]
    web_events = [s for s in SEEDS["web_events"] if in_range(s["date"], start, end)]
    mon_events = [s for s in SEEDS["monitoring_events"] if in_range(s["date"], start, end)]

    active_dates = {
        s["date"] for s in sessions
    } | {s["date"] for s in shorts} | {s["date"] for s in app} \
        | {s["date"] for s in web_events} | {s["date"] for s in mon_events}
    active_days = len(active_dates)

    if active_days == 0:
        return {"score": 0, "status": "insufficient_data", "components": {}, "raw": 0.0}

    v_study = exp_study(sessions, days)
    v_shorts = exp_shorts(shorts, shorts_limit)
    v_dist = exp_distraction(app, len(mon_events))
    v_web = exp_web(web_events, blocked_active=1)  # dev user has 1 active blocked site
    v_cons = round(min(1.0, active_days / CONSISTENCY_TARGET[period_type]), 4)

    values = {
        "study": (v_study, "evaluated" if sessions else "neutral"),
        "shorts": (v_shorts, "evaluated" if shorts else "neutral"),
        "distraction": (v_dist, "evaluated" if app else "neutral"),
        "web": (v_web, "evaluated"),  # dev user always has an active blocked site
        "consistency": (v_cons, "evaluated"),
    }
    raw = sum(WEIGHTS[k] * v for k, (v, _) in values.items())

    required = REQUIRED_DAYS[period_type]
    if active_days < required:
        score = round(raw * active_days / required)
        status = "partial_data"
    else:
        score = round(raw)
        status = "sufficient_data"
    return {"score": max(0, min(100, int(score))), "status": status,
            "components": {k: round(WEIGHTS[k] * v, 1) for k, (v, _) in values.items()},
            "raw": round(raw, 2), "active_days": active_days, "required": required}


# ---------------------------------------------------------------------------
# Seeding + cleanup
# ---------------------------------------------------------------------------


def setup() -> int:
    """Insert users, one device for the dev user, the blocked website, and
    all seeded domain rows. Returns the dev device id."""
    db = SessionLocal()
    try:
        db.add(User(id=DEV_USER_ID))
        db.add(User(id=OTHER_USER_ID))
        db.flush()
        device = Device(
            user_id=DEV_USER_ID,
            device_uuid=f"verify-score-dev-{DEV_USER_ID}",
            device_name="Verify Score Device",
            is_active=True,
        )
        db.add(device)
        db.flush()
        device_id = device.id

        db.add(BlockedWebsite(
            user_id=DEV_USER_ID, domain="x.com", normalized_domain="x.com",
            verification_status="pending", is_blocked=True,
        ))
        for s in SEEDS["sessions"]:
            db.add(StudySession(
                user_id=DEV_USER_ID, status=s["status"],
                started_at=datetime.combine(s["date"], time(10, 0)),
                ended_at=datetime.combine(s["date"], time(12, 0)),
                actual_duration_seconds=s["seconds"],
            ))
        for s in expand(SEEDS["shorts"], "shorts"):
            db.add(ShortsUsage(
                user_id=DEV_USER_ID, device_id=device_id, usage_date=s["date"],
                platform="UNKNOWN", surface="UNKNOWN",
                shorts_count=10, duration_seconds=int(s["minutes"] * 60),
                warning_triggered=s["warning"], limit_reached=s["limit"],
            ))
        for s in expand(SEEDS["app"], "app"):
            db.add(AppUsage(
                user_id=DEV_USER_ID, device_id=device_id,
                package_name="com.example.app", app_name="Example App",
                usage_date=s["date"], duration_seconds=int(s["minutes"] * 60),
                launch_count=1,
            ))
        for s in SEEDS["web_events"]:
            db.add(WebsiteEvent(
                user_id=DEV_USER_ID, device_id=device_id, domain=s["domain"],
                event_type=s["type"], occurred_at=datetime.combine(s["date"], time(14, 0)),
            ))
        for s in SEEDS["monitoring_events"]:
            db.add(MonitoringEvent(
                user_id=DEV_USER_ID, device_id=device_id, event_type=s["type"],
                occurred_at=datetime.combine(s["date"], time(15, 0)),
            ))
        db.commit()
        return device_id
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(text("DELETE FROM website_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM blocked_websites WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM monitoring_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM app_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM shorts_usage WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM study_sessions WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM devices WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def check_score(name: str, api: dict, expected: dict) -> None:
    """Compare an API score response with the independent expected value."""
    ok = (
        api["score"] == expected["score"]
        and api["status"] == expected["status"]
        and 0 <= api["score"] <= 100
    )
    detail = f"api={api['score']} ({api['status']}) expected={expected['score']} ({expected['status']})"
    record(f"{name}: score matches independent math ({expected['score']})", ok, detail)

    comps = {c["name"]: c for c in api["components"]}
    order_ok = [c["name"] for c in api["components"]] == [
        "study", "shorts", "distraction", "web", "consistency",
    ]
    record(f"{name}: component names/order approved", order_ok, str(list(comps)))

    for key, points in expected["components"].items():
        got = comps[key]["points"]
        record(
            f"{name}: {key} component points == {points}",
            got == points,
            f"api={got} expected={points}",
        )


def main() -> None:
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    setup()

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
    # 1. Daily scores
    # ------------------------------------------------------------------
    status, daily = request("GET", f"/score/daily?date={TODAY.isoformat()}")
    expect_status("GET /score/daily (today) -> 200", status, 200, daily)
    check_score("daily(today) productive profile", daily,
                exp_score("daily", TODAY))

    status, daily_y = request("GET", f"/score/daily?date={YESTERDAY.isoformat()}")
    expect_status("GET /score/daily (yesterday) -> 200", status, 200, daily_y)
    check_score("daily(yesterday) low-study/high-distraction", daily_y,
                exp_score("daily", YESTERDAY))

    status, daily_so = request("GET", f"/score/daily?date={STUDY_ONLY_DAY.isoformat()}")
    expect_status("GET /score/daily (study-only day) -> 200", status, 200, daily_so)
    check_score("daily(study-only) missing-data neutral behavior", daily_so,
                exp_score("daily", STUDY_ONLY_DAY))
    comps = {c["name"]: c for c in daily_so["components"]}
    record(
        "missing data -> neutral components (shorts/distraction 0.5)",
        comps["shorts"]["status"] == "neutral" and comps["shorts"]["value"] == 0.5
        and comps["distraction"]["status"] == "neutral" and comps["distraction"]["value"] == 0.5,
        f"shorts={comps['shorts']} distraction={comps['distraction']}",
    )

    status, daily_pm = request("GET", f"/score/daily?date={PREV_MONTH.isoformat()}")
    expect_status("GET /score/daily (prev-month) -> 200", status, 200, daily_pm)
    check_score("daily(prev-month) high-study day", daily_pm,
                exp_score("daily", PREV_MONTH))

    # ------------------------------------------------------------------
    # 2. Weekly + monthly (independent math comparison)
    # ------------------------------------------------------------------
    status, weekly = request("GET", f"/score/weekly?date={TODAY.isoformat()}")
    expect_status("GET /score/weekly -> 200", status, 200, weekly)
    check_score("weekly(today)", weekly, exp_score("weekly", TODAY))
    record(
        "weekly computed on week aggregates, normalized <= 100",
        weekly["score"] <= 100 and weekly["period"]["type"] == "weekly",
        f"score={weekly['score']}",
    )

    status, monthly = request("GET", f"/score/monthly?date={TODAY.isoformat()}")
    expect_status("GET /score/monthly -> 200", status, 200, monthly)
    check_score("monthly(today)", monthly, exp_score("monthly", TODAY))

    status, weekly_pw = request("GET", f"/score/weekly?date={PREV_WEEK.isoformat()}")
    expect_status("GET /score/weekly (prev week) -> 200", status, 200, weekly_pw)
    check_score("weekly(prev week) high-Shorts profile", weekly_pw,
                exp_score("weekly", PREV_WEEK))

    # ------------------------------------------------------------------
    # 3. Deterministic repeat + explanation
    # ------------------------------------------------------------------
    status, weekly2 = request("GET", f"/score/weekly?date={TODAY.isoformat()}")
    same = (
        weekly["score"] == weekly2["score"]
        and weekly["components"] == weekly2["components"]
        and weekly["explanation"] == weekly2["explanation"]
    )
    record("deterministic repeat calculation (identical response)", same,
           f"first={weekly['score']} second={weekly2['score']}")

    record(
        "explanation present with deterministic summary + factors",
        isinstance(daily["explanation"], dict)
        and daily["explanation"]["summary"].startswith("Your Score:")
        and len(daily["explanation"]["positives"]) >= 1,
        str(daily.get("explanation")),
    )
    record(
        "imperfect day explanation lists negatives",
        len(daily_y["explanation"]["negatives"]) >= 1
        and any("Shorts" in n for n in daily_y["explanation"]["negatives"]),
        daily_y["explanation"]["negatives"],
    )
    # ------------------------------------------------------------------
    # 4. Inactivity + boundary + user isolation
    # ------------------------------------------------------------------
    status, no_data = request("GET", f"/score/daily?date={NO_DATA_DAY.isoformat()}")
    expect_status("GET /score/daily (no data) -> 200", status, 200, no_data)
    record(
        "inactivity -> score 0 with insufficient_data (never 100)",
        no_data["score"] == 0 and no_data["status"] == "insufficient_data",
        f"score={no_data.get('score')} status={no_data.get('status')}",
    )
    record(
        "inactivity explanation says no activity",
        "No activity" in no_data["explanation"]["summary"],
        no_data["explanation"]["summary"],
    )

    status, other = request("GET", f"/score/daily?date={TODAY.isoformat()}", user_id=OTHER_USER_ID)
    record(
        "user isolation: other user's score is 0 / insufficient_data",
        status == 200 and other["score"] == 0 and other["status"] == "insufficient_data",
        f"score={other.get('score')} status={other.get('status')}",
    )

    for label, resp in [("daily(today)", daily), ("daily(yesterday)", daily_y),
                        ("weekly", weekly), ("monthly", monthly),
                        ("weekly(prev)", weekly_pw), ("daily(prev-month)", daily_pm)]:
        record(
            f"score boundary 0..100 ({label})",
            0 <= resp["score"] <= 100 and resp["score"] == int(resp["score"]),
            f"score={resp['score']}",
        )

    # ------------------------------------------------------------------
    # 5. Regression: Settings / Study / Monitoring / Shorts / Web / Reports
    # ------------------------------------------------------------------
    status, body = request("GET", "/settings")
    expect_status("GET /settings -> 200", status, 200, body)
    status, body = request("GET", "/settings/shorts")
    expect_status("GET /settings/shorts -> 200", status, 200, body)
    status, body = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, body)
    status, body = request("GET", "/monitoring/app-usage")
    expect_status("GET /monitoring/app-usage -> 200", status, 200, body)
    status, body = request("GET", "/shorts/usage")
    expect_status("GET /shorts/usage -> 200", status, 200, body)
    status, body = request("GET", "/websites/blocked")
    expect_status("GET /websites/blocked -> 200", status, 200, body)
    status, body = request("GET", "/web/events")
    expect_status("GET /web/events -> 200", status, 200, body)
    status, body = request("GET", "/reports/daily")
    expect_status("GET /reports/daily -> 200", status, 200, body)
    status, body = request("GET", "/reports/weekly")
    expect_status("GET /reports/weekly -> 200", status, 200, body)

    # ------------------------------------------------------------------
    # 6. Cleanup
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
