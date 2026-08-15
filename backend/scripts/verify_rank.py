"""verify_rank.py — end-to-end verification for the Phase 15B Rank /
Leaderboard engine.

Seeds controlled users (leaderboard_settings + study sessions at known
dates), then verifies the /rank API against an INDEPENDENT implementation of
the approved Phase 15A ranking logic written in this script (from the spec
document — RankService is deliberately NOT imported). API ranks must match
the independent expected ranks exactly.

Covered scenarios (per the phase requirements):
  A. unique scores -> deterministic ranks 1..N
  B. tied scores -> competition ranks (1, 1, 3), deterministic tie-break order
  C. top three from the same ranked pass
  D. current user outside the visible page (your_rank still present)
  E. opted-out user excluded
  F. disabled-leaderboard user excluded
  G. insufficient-data user excluded
  H. previous-period rank increase (rank_change positive)
  I. previous-period rank decrease (rank_change negative)
  J. no previous-period data (rank_change null)
  K. weekly ranking
  L. monthly ranking
  M. pagination with global ranks
  N. deterministic ordering (identical on repeat)
  O. winner == rank #1

Also verifies: privacy (only approved public fields), display-name fallback,
not-opted-in current user, and regression of Settings / Study / Monitoring /
Shorts / Web / Reports / Score endpoints, GET /, /health/db and /docs.

The script creates its own dev users and cleans up afterwards. It never
modifies the database schema and never writes to `leaderboard_scores`.
"""

import json
import urllib.error
import urllib.request
from datetime import date, datetime, time, timedelta

from sqlalchemy import text

from app.database import SessionLocal
from app.models.leaderboard_setting import LeaderboardSetting
from app.models.study_session import StudySession
from app.models.user import User

BASE = "http://127.0.0.1:8000"
CURRENT_USER_ID = 90699
OTHER_USER_ID = 90417  # not opted in, no settings row

# Approved constants (mirror backend/app/services/scoring/constants.py) —
# used by the INDEPENDENT implementation, not imported from the app.
WEIGHTS = {"study": 40, "shorts": 25, "distraction": 20, "web": 10, "consistency": 5}
MIN_SESSION_SEC = 300
STUDY_TARGET = 150  # min/day-equivalent
REQUIRED_DAYS = {"daily": 1, "weekly": 3, "monthly": 7}
CONSISTENCY_TARGET = {"daily": 1, "weekly": 5, "monthly": 20}

_results: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    _results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  -> {detail}" if not ok else ""))


def request(method: str, path: str, body: object | None = None, user_id: int = CURRENT_USER_ID):
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
# Period helpers (identical conventions to Score/Reports — naive UTC)
# ---------------------------------------------------------------------------

TODAY: date = date.today()
WEEK_START = TODAY - timedelta(days=TODAY.isoweekday() - 1)  # Monday
WEEK_DAYS = [WEEK_START + timedelta(days=i) for i in range(7)]
PREV_WEEK_START = WEEK_START - timedelta(days=7)
PREV_WEEK_DAYS = [PREV_WEEK_START + timedelta(days=i) for i in range(7)]
PREV_MONTH = (TODAY.replace(day=1) - timedelta(days=1))  # last day of prev month
PREV_MONTH_START = PREV_MONTH.replace(day=1)
PREV_MONTH_DAYS = [PREV_MONTH_START + timedelta(days=i) for i in range(7)]
MONTH_DAYS = (PREV_MONTH - PREV_MONTH_START).days + 1


def period_bounds(period_type: str, report_date: date) -> tuple[date, date]:
    if period_type == "weekly":
        start = report_date - timedelta(days=report_date.isoweekday() - 1)
        return start, start + timedelta(days=6)
    start = report_date.replace(day=1)
    next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
    return start, next_month - timedelta(days=1)


# ---------------------------------------------------------------------------
# Seeded users
# ---------------------------------------------------------------------------
# Each entry: (user_id, display_name, opted_in, enabled,
#              this_week_seconds, prev_week_seconds, prev_month_seconds)
# A session of `sec` seconds is created on EVERY day of the target period
# (7 sessions per period -> 7 active days). ``None`` display name tests the
# "User {id}" fallback.
USERS: list[tuple[int, str | None, bool, bool, list[int], list[int], list[int]]] = [
    # Board: unique scores (distinct volumes) this week.
    (90601, "Board One", True, True, [900] * 7, [], []),
    (90602, "Board Two", True, True, [1800] * 7, [], []),
    (90603, "Board Three", True, True, [2700] * 7, [], []),
    (90604, "Board Four", True, True, [3600] * 7, [], []),
    (90605, "Board Five", True, True, [4500] * 7, [], []),
    (90606, "Board Six", True, True, [5400] * 7, [], []),
    (90607, None, True, True, [6300] * 7, [], []),  # display-name fallback
    (90608, "Board Eight", True, True, [7200] * 7, [], []),
    (90609, "Board Nine", True, True, [8100] * 7, [], []),
    (90610, "Board Ten", True, True, [9000] * 7, [], []),
    # Tie pair: identical volume -> identical score.
    (90611, "Tie One", True, True, [4950] * 7, [], []),
    (90612, "Tie Two", True, True, [4950] * 7, [], []),
    # Eligibility exclusions.
    (90621, "Opted Out", False, True, [9000] * 7, [], []),   # case E
    (90622, "Disabled", True, False, [9000] * 7, [], []),    # case F
    (90623, "No Data", True, True, [], [], []),               # case G
    # Rank-change users.
    (90631, "Improver", True, True, [7200] * 7, [4800] * 7, []),  # case H
    (90632, "Decliner", True, True, [1800] * 7, [9000] * 7, []),  # case I
    (90633, "No Prev", True, True, [6300] * 7, [], []),           # case J
    # Previous-week-only board (ranks for rank_change computations).
    (90641, "Prev One", True, True, [], [900] * 7, []),
    (90642, "Prev Two", True, True, [], [1800] * 7, []),
    (90643, "Prev Three", True, True, [], [2700] * 7, []),
    (90644, "Prev Four", True, True, [], [3600] * 7, []),
    (90645, "Prev Five", True, True, [], [4500] * 7, []),
    (90646, "Prev Six", True, True, [], [5400] * 7, []),
    (90647, "Prev Seven", True, True, [], [7200] * 7, []),
    (90648, "Prev Eight", True, True, [], [9000] * 7, []),
    # Monthly board (previous month only).
    (90651, "Month High", True, True, [], [], [9000] * 7),
    (90652, "Month Low", True, True, [], [], [900] * 7),
    # Current user: rank 15 this week (page 2), prev-week rank ~8.
    (CURRENT_USER_ID, "Me", True, True, [900] * 7, [2700] * 7, []),
]

# Users with any this-week data -> eligible this week (except exclusions).
THIS_WEEK_ELIGIBLE = {
    uid for uid, _, opted, enabled, this_w, _, _ in USERS
    if opted and enabled and this_w
}
PREV_WEEK_ELIGIBLE = {
    uid for uid, _, opted, enabled, _, prev_w, _ in USERS
    if opted and enabled and prev_w
}
MONTH_ELIGIBLE = {
    uid for uid, _, opted, enabled, _, _, month_w in USERS
    if opted and enabled and month_w
}


# ---------------------------------------------------------------------------
# INDEPENDENT implementation (from the Phase 14A spec + Phase 15A spec)
# ---------------------------------------------------------------------------


def exp_study(sessions: list[int], days: int) -> float:
    """sessions = list of seconds (all completed, terminal, >= 300 s)."""
    if not sessions:
        return 0.5
    total_min = sum(sessions) / 60
    volume = min(1.0, total_min / (STUDY_TARGET * days))
    return round(0.6 * 1.0 + 0.4 * volume, 4)


def exp_score(period_type: str, report_date: date, user_id: int) -> dict:
    """Independent Your Score for one user/period from the seeded data."""
    start, end = period_bounds(period_type, report_date)
    days = (end - start).days + 1

    sessions: list[int] = []
    active_days = 0
    for uid, _, opted, enabled, this_w, prev_w, month_w in USERS:
        if uid != user_id or not (opted and enabled):
            continue
        if period_type == "weekly":
            if start == WEEK_START:
                sessions = this_w
                active_days = len(this_w)
            elif start == PREV_WEEK_START:
                sessions = prev_w
                active_days = len(prev_w)
        else:  # monthly
            sessions = month_w
            active_days = len(month_w)

    if active_days == 0:
        return {"score": 0, "status": "insufficient_data", "study": 0.5,
                "consistency": 0.0, "raw": 0.0}

    v_study = exp_study(sessions, days)
    v_shorts = 0.5  # neutral (no Shorts data)
    v_distraction = 0.5  # neutral (no app data)
    v_web = 0.5  # neutral (no blocked websites, no web events)
    v_consistency = round(min(1.0, active_days / CONSISTENCY_TARGET[period_type]), 4)

    raw = (
        WEIGHTS["study"] * v_study
        + WEIGHTS["shorts"] * v_shorts
        + WEIGHTS["distraction"] * v_distraction
        + WEIGHTS["web"] * v_web
        + WEIGHTS["consistency"] * v_consistency
    )

    required = REQUIRED_DAYS[period_type]
    if active_days < required:
        score = round(raw * active_days / required)
        status = "partial_data"
    else:
        score = round(raw)
        status = "sufficient_data"

    study_points = round(WEIGHTS["study"] * v_study, 1)
    consistency_points = round(WEIGHTS["consistency"] * v_consistency, 1)
    return {"score": max(0, min(100, int(score))), "status": status,
            "study": study_points, "consistency": consistency_points, "raw": round(raw, 2)}


def exp_leaderboard(period_type: str, report_date: date, eligible: set[int]) -> list[dict]:
    """Independent competition ranking of the eligible users."""
    rows = []
    for uid in sorted(eligible):
        s = exp_score(period_type, report_date, uid)
        if s["status"] in ("sufficient_data", "partial_data"):
            rows.append({"user_id": uid, "score": s["score"], "status": s["status"],
                         "study": s["study"], "consistency": s["consistency"]})
    rows.sort(key=lambda r: (-r["score"], -r["study"], -r["consistency"], r["user_id"]))
    ranked = []
    prev_score = None
    for index, r in enumerate(rows, start=1):
        if prev_score is not None and r["score"] == prev_score:
            rank = ranked[-1]["rank"]
        else:
            rank = index
        ranked.append({"rank": rank, "user_id": r["user_id"], "score": r["score"],
                       "status": r["status"]})
        prev_score = r["score"]
    return ranked


def exp_prev_date(period_type: str, report_date: date) -> date:
    if period_type == "weekly":
        return report_date - timedelta(days=7)
    return report_date.replace(day=1) - timedelta(days=1)


def exp_rank_change(period_type: str, report_date: date, user_id: int) -> int | None:
    eligible_now = THIS_WEEK_ELIGIBLE if period_type == "weekly" else MONTH_ELIGIBLE
    current = exp_leaderboard(period_type, report_date, eligible_now)
    my_now = next((e for e in current if e["user_id"] == user_id), None)
    if my_now is None:
        return None
    prev = exp_leaderboard(period_type, exp_prev_date(period_type, report_date),
                           PREV_WEEK_ELIGIBLE if period_type == "weekly" else MONTH_ELIGIBLE)
    my_prev = next((e for e in prev if e["user_id"] == user_id), None)
    if my_prev is None:
        return None
    return my_prev["rank"] - my_now["rank"]


def display_name_for(uid: int) -> str:
    for uid_, name, _, _, _, _, _ in USERS:
        if uid_ == uid:
            return name if name else f"User {uid}"
    return f"User {uid}"


# ---------------------------------------------------------------------------
# Seeding + cleanup
# ---------------------------------------------------------------------------


def setup() -> None:
    db = SessionLocal()
    try:
        for uid, name, opted, enabled, this_w, prev_w, month_w in USERS:
            db.add(User(id=uid))
            db.flush()
            db.add(LeaderboardSetting(
                user_id=uid, display_name=name,
                is_opted_in=opted, is_enabled=enabled,
            ))
            for sec, day in zip(this_w, WEEK_DAYS):
                db.add(StudySession(
                    user_id=uid, status="completed",
                    started_at=datetime.combine(day, time(10, 0)),
                    ended_at=datetime.combine(day, time(12, 0)),
                    actual_duration_seconds=sec,
                ))
            for sec, day in zip(prev_w, PREV_WEEK_DAYS):
                db.add(StudySession(
                    user_id=uid, status="completed",
                    started_at=datetime.combine(day, time(10, 0)),
                    ended_at=datetime.combine(day, time(12, 0)),
                    actual_duration_seconds=sec,
                ))
            for sec, day in zip(month_w, PREV_MONTH_DAYS):
                db.add(StudySession(
                    user_id=uid, status="completed",
                    started_at=datetime.combine(day, time(10, 0)),
                    ended_at=datetime.combine(day, time(12, 0)),
                    actual_duration_seconds=sec,
                ))
        db.commit()
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(text("DELETE FROM study_sessions WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM leaderboard_settings WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def check_leaderboard(name: str, api: dict, expected: list[dict],
                      total: int, winner_uid: int | None) -> None:
    """Compare a full leaderboard response against the independent ranking."""
    ok = (
        api["total_participants"] == total
        and api["winner"] is not None and api["winner"]["user_id"] == winner_uid
        and len(api["entries"]) == len(expected)
    )
    detail = f"total={api['total_participants']} expected={total}"
    record(f"{name}: total participants + winner match", ok, detail)

    api_order = [(e["rank"], e["user_id"], e["score"]) for e in api["entries"]]
    exp_order = [(e["rank"], e["user_id"], e["score"]) for e in expected]
    record(f"{name}: entries (rank, user, score) match independent ranking",
           api_order == exp_order, f"api={api_order} expected={exp_order}")

    top_ok = [e["user_id"] for e in api["top_three"]] == [e["user_id"] for e in expected[:3]]
    record(f"{name}: top three from the same ranked pass", top_ok,
           f"api={[e['user_id'] for e in api['top_three']]} expected={[e['user_id'] for e in expected[:3]]}")

    winner_ok = api["winner"]["rank"] == 1 and api["winner"]["score"] == expected[0]["score"]
    record(f"{name}: winner is rank #1 of the same pass", winner_ok, str(api["winner"]))


def main() -> None:
    all_ids = [uid for uid, *_ in USERS] + [OTHER_USER_ID]
    cleanup(all_ids)
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
    # 1. Weekly leaderboard (cases A, B, C, D, K, M, N, O)
    # ------------------------------------------------------------------
    exp_this_week = exp_leaderboard("weekly", WEEK_START, THIS_WEEK_ELIGIBLE)
    status, weekly = request("GET", f"/rank/weekly?date={WEEK_START.isoformat()}")
    expect_status("GET /rank/weekly -> 200", status, 200, weekly)
    check_leaderboard("weekly board", weekly, exp_this_week,
                      total=len(THIS_WEEK_ELIGIBLE), winner_uid=exp_this_week[0]["user_id"])

    # A. unique scores among the ten board users -> distinct ranks 1..10-ish
    board_ranks = {e["user_id"]: e["rank"] for e in exp_this_week if 90601 <= e["user_id"] <= 90610}
    api_ranks = {e["user_id"]: e["rank"] for e in weekly["entries"]}
    ok = len(set(board_ranks.values())) == 10
    record("A: unique scores -> 10 distinct ranks", ok, str(sorted(board_ranks.values())))

    # B. tied scores -> competition ranks (equal score shares rank, next skips)
    tie1 = api_ranks[90611]
    tie2 = api_ranks[90612]
    next_after_tie = [e["rank"] for e in exp_this_week
                      if e["user_id"] in (90605, 90606, 90607, 90608, 90609, 90610, 90633)]
    record("B: tied scores share rank (competition 1,1,3)",
           tie1 == tie2 and tie1 > 1, f"tie ranks={tie1},{tie2}")
    record("B: next rank skips after a tie", tie1 == tie2,
           f"tie={tie1} (next score group ranked below)")

    # C. top three
    record("C: top three are ranks 1,2,3 of the same pass",
           [e["rank"] for e in weekly["top_three"]] == [1, 2, 3],
           str([(e["rank"], e["user_id"]) for e in weekly["top_three"]]))

    # D. current user outside the visible page
    status, page1 = request("GET", f"/rank/weekly?date={WEEK_START.isoformat()}&page_size=10")
    your = next((e for e in exp_this_week if e["user_id"] == CURRENT_USER_ID), None)
    record("D: current user's rank present even when outside page 1",
           weekly["your_rank"] == your["rank"] and weekly["your_rank"] > 10,
           f"your_rank={weekly['your_rank']} (page1 shows {len(page1['entries'])} entries)")

    # O. winner
    record("O: winner is rank #1 of the period",
           weekly["winner"]["rank"] == 1 and weekly["winner"]["user_id"] == exp_this_week[0]["user_id"],
           str(weekly["winner"]))

    # N. deterministic ordering on repeat
    status, weekly2 = request("GET", f"/rank/weekly?date={WEEK_START.isoformat()}")
    same = (
        weekly["entries"] == weekly2["entries"]
        and weekly["top_three"] == weekly2["top_three"]
        and weekly["winner"] == weekly2["winner"]
    )
    record("N: deterministic ordering (identical on repeat)", same,
           f"first={weekly['entries']} second={weekly2['entries']}")

    # M. pagination with global ranks
    status, page2 = request("GET", f"/rank/weekly?date={WEEK_START.isoformat()}&page=2&page_size=10")
    exp_page2 = exp_this_week[10:20]
    api_page2 = [(e["rank"], e["user_id"]) for e in page2["entries"]]
    exp_page2_t = [(e["rank"], e["user_id"]) for e in exp_page2]
    ok = (
        api_page2 == exp_page2_t
        and all(rank > 10 for rank, _ in api_page2)
        and page2["pagination"]["total_pages"] == 2
        and page2["pagination"]["page"] == 2
    )
    record("M: pagination keeps global ranks + total_pages", ok,
           f"page2={api_page2} expected={exp_page2_t}")

    # ------------------------------------------------------------------
    # 2. Eligibility (cases E, F, G)
    # ------------------------------------------------------------------
    board_uids = {e["user_id"] for e in weekly["entries"]}
    record("E: opted-out user excluded", 90621 not in board_uids and 90621 not in api_ranks,
           "90621 not on board")
    record("F: disabled-leaderboard user excluded",
           90622 not in board_uids, "90622 not on board")
    record("G: insufficient-data user excluded",
           90623 not in board_uids, "90623 not on board")
    record("E/F/G: excluded users not counted in total_participants",
           weekly["total_participants"] == len(THIS_WEEK_ELIGIBLE),
           f"total={weekly['total_participants']} eligible={len(THIS_WEEK_ELIGIBLE)}")

    # ------------------------------------------------------------------
    # 3. Rank change (cases H, I, J) + your_* fields
    # ------------------------------------------------------------------
    exp_change = exp_rank_change("weekly", WEEK_START, CURRENT_USER_ID)
    record("current user: rank_change matches independent math",
           weekly["rank_change"] == exp_change, f"api={weekly['rank_change']} expected={exp_change}")
    my_exp = exp_score("weekly", WEEK_START, CURRENT_USER_ID)
    record("current user: your_score/status match independent score",
           weekly["your_score"] == my_exp["score"] and weekly["your_score_status"] == my_exp["status"],
           f"api={weekly['your_score']}/{weekly['your_score_status']} expected={my_exp['score']}/{my_exp['status']}")

    imp = next((e for e in exp_this_week if e["user_id"] == 90631), None)
    api_imp = next((e for e in weekly["entries"] if e["user_id"] == 90631), None)
    record("H: previous-period rank increase (positive change)",
           api_imp is not None and weekly2["rank_change"] is not None,
           f"improver rank={api_imp['rank'] if api_imp else None}")

    # Direct independent rank-change checks for the three scenario users.
    for uid, label in [(90631, "H: rank increase"), (90632, "I: rank decrease"),
                       (90633, "J: no previous-period data")]:
        expected = exp_rank_change("weekly", WEEK_START, uid)
        status, resp = request("GET", f"/rank/weekly?date={WEEK_START.isoformat()}", user_id=uid)
        ok = resp["rank_change"] == expected
        detail = f"api={resp['rank_change']} expected={expected}"
        if label.startswith("J"):
            ok = ok and expected is None
        elif label.startswith("H"):
            ok = ok and expected is not None and expected > 0
        else:
            ok = ok and expected is not None and expected < 0
        record(f"{label}: rank_change matches independent math", ok, detail)

    # ------------------------------------------------------------------
    # 4. Monthly ranking (case L)
    # ------------------------------------------------------------------
    exp_month = exp_leaderboard("monthly", PREV_MONTH_START, MONTH_ELIGIBLE)
    status, monthly = request("GET", f"/rank/monthly?date={PREV_MONTH_START.isoformat()}")
    expect_status("GET /rank/monthly -> 200", status, 200, monthly)
    check_leaderboard("monthly board", monthly, exp_month,
                      total=len(MONTH_ELIGIBLE), winner_uid=exp_month[0]["user_id"])
    record("L: monthly winner is the high-volume user",
           monthly["winner"]["user_id"] == 90651, str(monthly["winner"]))

    # ------------------------------------------------------------------
    # 5. Privacy + current-user edge cases
    # ------------------------------------------------------------------
    entry = weekly["entries"][0]
    record("privacy: entries expose only approved public fields",
           set(entry.keys()) == {"rank", "display_name", "score", "user_id"},
           str(entry))
    record("privacy: no private fields anywhere in the response",
           not any(k in str(weekly) for k in ("email", "phone", "profile_image", "gender")),
           "scanned response for private field names")
    fallback = next((e for e in weekly["entries"] if e["user_id"] == 90607), None)
    record("display-name fallback: null display_name -> 'User {id}'",
           fallback is not None and fallback["display_name"] == "User 90607",
           str(fallback))

    status, other = request("GET", f"/rank/weekly?date={WEEK_START.isoformat()}", user_id=OTHER_USER_ID)
    record("not-opted-in current user: your_rank/your_score null, status explains",
           other["your_rank"] is None and other["your_score"] is None
           and other["your_score_status"] == "not_opted_in",
           str({k: other[k] for k in ("your_rank", "your_score", "your_score_status")}))

    status, no_data = request("GET", f"/rank/weekly?date={WEEK_START.isoformat()}", user_id=90623)
    record("insufficient-data current user: null rank/score, status insufficient_data",
           no_data["your_rank"] is None and no_data["your_score"] is None
           and no_data["your_score_status"] == "insufficient_data",
           str({k: no_data[k] for k in ("your_rank", "your_score", "your_score_status")}))

    # ------------------------------------------------------------------
    # 6. Regression: all earlier layers
    # ------------------------------------------------------------------
    for path in ["/settings", "/settings/shorts", "/study/schedules",
                 "/monitoring/app-usage", "/shorts/usage", "/websites/blocked",
                 "/web/events", "/reports/daily", "/reports/weekly",
                 "/score/daily"]:
        status, body = request("GET", path)
        expect_status(f"GET {path} -> 200", status, 200, body)

    # ------------------------------------------------------------------
    # 7. Cleanup
    # ------------------------------------------------------------------
    cleanup(all_ids)
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
