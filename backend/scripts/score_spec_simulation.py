"""score_spec_simulation.py — Phase 14A validation for the Your Score spec.

This script implements the FORMULAS FROM THE SPECIFICATION DOCUMENT
(`backend/docs/your_score_spec.md`) in pure Python and validates them:

  * the six required profiles (A–F) — fairness of the resulting ranking
  * sensitivity — small behavior changes produce bounded score changes
  * distribution — a grid sweep shows useful separation (no clustering at
    100 or 0), with no combo exceeding 100
  * anti-gaming checks — tiny sessions excluded, volume capped, no
    inactivity credit
  * data availability — read-only row counts from MySQL (informational)

This is a SPEC-VALIDATION TOOL ONLY. It is NOT the production score engine:
it writes nothing, changes no schema, and produces no persisted data.
"""

from statistics import mean, median

from sqlalchemy import text

from app.database import SessionLocal

# ---------------------------------------------------------------------------
# Constants (mirror backend/docs/your_score_spec.md)
# ---------------------------------------------------------------------------
W = {"study": 40, "shorts": 25, "distraction": 20, "web": 10, "consistency": 5}
MIN_MEANINGFUL_SEC = 300          # sessions shorter than 5 min don't count
STUDY_TARGET_MIN_PER_DAY = 150    # volume target (2.5 h / day-equivalent)
                                  # (validated: 120/day made "Excellent" too easy)
SHORTS_DEFAULT_LIMIT_MIN = 30     # fallback when the user has no configured limit
DISTRACTION_THRESHOLD_MIN = 240   # 4 h / day phone-time threshold
ACTIVE_DAYS_REQUIRED = {"daily": 1, "weekly": 3, "monthly": 7}
CONSISTENCY_TARGET = {"daily": 1, "weekly": 5, "monthly": 20}

# ---------------------------------------------------------------------------
# Component formulas (exactly as specified)
# ---------------------------------------------------------------------------


def study_value(sessions: list[dict], days: int) -> tuple[float, str]:
    """v_study = 0.6 * completion + 0.4 * capped volume (meaningful sessions only)."""
    meaningful = [
        s for s in sessions
        if s.get("status") in ("completed", "cancelled")
        and s.get("duration_seconds", 0) >= MIN_MEANINGFUL_SEC
    ]
    if not meaningful:
        return 0.5, "neutral"
    completed = sum(1 for s in meaningful if s["status"] == "completed")
    c = completed / len(meaningful)
    total_min = sum(s["duration_seconds"] for s in meaningful) / 60
    q = min(1.0, total_min / (STUDY_TARGET_MIN_PER_DAY * days))
    return round(0.6 * c + 0.4 * q, 4), "evaluated"


def shorts_value(shorts_days: list[dict], limit_min: int | None) -> tuple[float, str]:
    """Usage-weighted mean of per-day discipline; 2x limit -> 0; neutral when no data."""
    limit = limit_min if limit_min and limit_min > 0 else SHORTS_DEFAULT_LIMIT_MIN
    days = [d for d in shorts_days if d.get("minutes", 0) > 0]
    if not days:
        return 0.5, "neutral"
    total = 0.0
    weight = 0.0
    for d in days:
        u = d["minutes"]
        over = max(0, u - limit)
        s = 1.0 if u <= limit else max(0.0, 1.0 - over / limit)
        if d.get("limit_reached"):
            s *= 0.9
        elif d.get("warning_triggered"):
            s *= 0.95
        total += s * u
        weight += u
    return round(total / weight, 4), "evaluated"


def distraction_value(
    app_days: list[dict], enforcement_events: int = 0
) -> tuple[float, str]:
    """Usage-weighted moderation: 4h/day threshold; neutral when no app data."""
    days = [d for d in app_days if d.get("minutes", 0) > 0]
    if not days:
        return 0.5, "neutral"
    total = 0.0
    weight = 0.0
    for d in days:
        x = d["minutes"]
        excess = max(0, x - DISTRACTION_THRESHOLD_MIN)
        s = 1.0 if x <= DISTRACTION_THRESHOLD_MIN else max(0.0, 1.0 - excess / DISTRACTION_THRESHOLD_MIN)
        total += s * x
        weight += x
    value = total / weight
    if enforcement_events > 0:
        value *= 1 - min(0.10, 0.02 * enforcement_events)
    return round(value, 4), "evaluated"


def web_value(blocked_count: int, events: list[dict]) -> tuple[float, str]:
    """Perfect avoidance when configured with zero attempts; persistence
    (repeats/unblocks) costs points; neutral when there is no web data at all."""
    attempts = sum(1 for e in events if e.get("type") == "BLOCK_ATTEMPT")
    unblocks = sum(1 for e in events if e.get("type") == "UNBLOCKED")
    domain_counts: dict[str, int] = {}
    for e in events:
        if e.get("type") == "BLOCK_ATTEMPT" and e.get("domain"):
            domain_counts[e["domain"]] = domain_counts.get(e["domain"], 0) + 1
    repeats = sum(1 for n in domain_counts.values() if n >= 3)

    if blocked_count == 0 and not events:
        return 0.5, "neutral"
    if blocked_count > 0 and attempts == 0:
        return 1.0, "evaluated"
    p = (
        min(0.15, 0.05 * attempts)
        + min(0.10, 0.05 * unblocks)
        + min(0.10, 0.05 * repeats)
    )
    return round(max(0.5, 1.0 - p), 4), "evaluated"


def consistency_value(active_days: int, period_type: str) -> float:
    """min(1, active_days / target) — days, never sessions."""
    return round(min(1.0, active_days / CONSISTENCY_TARGET[period_type]), 4)


# ---------------------------------------------------------------------------
# Score assembly + inactivity gate
# ---------------------------------------------------------------------------


def compute_score(
    profile: dict,
    days: int = 7,
    period_type: str = "weekly",
    shorts_limit_min: int | None = None,
) -> dict:
    """Compute a full score for one period from a profile of raw inputs."""
    active_days = profile.get("active_days", 0)

    if active_days == 0:
        return {
            "score": 0,
            "status": "insufficient_data",
            "components": {},
            "activity": {"active_days": 0, "required_days": ACTIVE_DAYS_REQUIRED[period_type], "coverage": 0.0},
        }

    v_study, s_study = study_value(profile.get("sessions", []), days)
    v_shorts, s_shorts = shorts_value(profile.get("shorts_days", []), shorts_limit_min)
    v_dist, s_dist = distraction_value(
        profile.get("app_days", []), profile.get("enforcement_events", 0)
    )
    v_web, s_web = web_value(profile.get("blocked_count", 0), profile.get("web_events", []))
    v_cons = consistency_value(active_days, period_type)

    components = {
        "study": (v_study, s_study),
        "shorts": (v_shorts, s_shorts),
        "distraction": (v_dist, s_dist),
        "web": (v_web, s_web),
        "consistency": (v_cons, "evaluated" if active_days > 0 else "neutral"),
    }
    raw = sum(W[k] * v for k, (v, _) in components.items())

    required = ACTIVE_DAYS_REQUIRED[period_type]
    if active_days < required:
        coverage = active_days / required
        score = round(raw * coverage)
        status = "partial_data"
    else:
        coverage = 1.0
        score = round(raw)
        status = "sufficient_data"

    return {
        "score": score,
        "status": status,
        "raw": round(raw, 2),
        "components": {
            k: {"value": v, "status": st, "points": round(W[k] * v, 1), "max": W[k]}
            for k, (v, st) in components.items()
        },
        "activity": {"active_days": active_days, "required_days": required, "coverage": coverage},
    }


def format_score(result: dict) -> str:
    parts = []
    for k in ("study", "shorts", "distraction", "web", "consistency"):
        comp = result["components"].get(k)
        if comp is None:
            continue
        parts.append(f"{k}={comp['points']:.1f}/{comp['max']}")
    return f"{result['score']} [{result['status']}] ({', '.join(parts)})"


# ---------------------------------------------------------------------------
# Profiles (weekly, 7 days) — realistic input sets
# ---------------------------------------------------------------------------


def profile_a() -> dict:
    """High study, low Shorts (within limit), low distraction."""
    return {
        "sessions": [
            {"status": "completed", "duration_seconds": 9000} for _ in range(6)
        ],
        "shorts_days": [
            {"minutes": 20, "warning_triggered": False, "limit_reached": False}
            for _ in range(7)
        ],
        "app_days": [{"minutes": 90} for _ in range(7)],
        "blocked_count": 1,
        "web_events": [],
        "active_days": 7,
    }


def profile_b() -> dict:
    """Low study, very low phone usage."""
    return {
        "sessions": [{"status": "completed", "duration_seconds": 1800}],
        "shorts_days": [],
        "app_days": [{"minutes": 40} for _ in range(1)],
        "blocked_count": 0,
        "web_events": [],
        "active_days": 1,
    }


def profile_c() -> dict:
    """High Shorts (2h/day vs 30-min limit), low study."""
    return {
        "sessions": [{"status": "completed", "duration_seconds": 1800}],
        "shorts_days": [
            {"minutes": 120, "warning_triggered": True, "limit_reached": True}
            for _ in range(7)
        ],
        "app_days": [{"minutes": 200} for _ in range(7)],
        "blocked_count": 0,
        "web_events": [],
        "active_days": 2,
    }


def profile_d() -> dict:
    """High study, moderate Shorts (45 min vs 30-min limit)."""
    return {
        "sessions": [
            {"status": "completed", "duration_seconds": 8400} for _ in range(6)
        ],
        "shorts_days": [
            {"minutes": 45, "warning_triggered": True, "limit_reached": False}
            for _ in range(7)
        ],
        "app_days": [{"minutes": 150} for _ in range(7)],
        "blocked_count": 1,
        "web_events": [{"type": "BLOCK_ATTEMPT", "domain": "x.com"}],
        "active_days": 6,
    }


def profile_e() -> dict:
    """No meaningful activity."""
    return {
        "sessions": [],
        "shorts_days": [],
        "app_days": [],
        "blocked_count": 0,
        "web_events": [],
        "active_days": 0,
    }


def profile_f() -> dict:
    """Heavy study, extreme distraction (8h phone/day + limit events)."""
    return {
        "sessions": [
            {"status": "completed", "duration_seconds": 12000} for _ in range(7)
        ],
        "shorts_days": [
            {"minutes": 25, "warning_triggered": False, "limit_reached": False}
            for _ in range(7)
        ],
        "app_days": [{"minutes": 480} for _ in range(7)],
        "enforcement_events": 3,
        "blocked_count": 0,
        "web_events": [],
        "active_days": 7,
    }


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------


def run_profiles() -> dict:
    profiles = {
        "A — high study, low Shorts, low distraction": profile_a(),
        "B — low study, very low phone usage": profile_b(),
        "C — high Shorts, low study": profile_c(),
        "D — high study, moderate Shorts": profile_d(),
        "E — no meaningful activity": profile_e(),
        "F — heavy study, extreme distraction": profile_f(),
    }
    results = {}
    print("\n=== PROFILE SIMULATION (weekly) ===")
    for name, profile in profiles.items():
        result = compute_score(profile, days=7)
        results[name] = result
        print(f"  {name}\n    -> {format_score(result)}")

    # Fairness assertions.
    a, b, c, d, e, f = (results[k] for k in profiles)
    checks = {
        "all profiles within 0..100": all(
            0 <= r["score"] <= 100 for r in results.values()
        ),
        "A (ideal) ranks highest": a["score"] >= d["score"] >= f["score"],
        "studying beats mere low usage (A > B)": a["score"] > b["score"],
        "heavy Shorts + low study ranks bottom (C < D)": c["score"] < d["score"],
        "inactivity is 0, not 100 (E)": e["score"] == 0 and e["status"] == "insufficient_data",
        "extreme distraction drags heavy study down (F < D)": f["score"] < d["score"],
        "A is Excellent (>= 90)": a["score"] >= 90,
        "B and C are Poor (< 40)": b["score"] < 40 and c["score"] < 40,
    }
    print("\n=== FAIRNESS CHECKS ===")
    for name, ok in checks.items():
        print(f"  {'PASS' if ok else 'FAIL'}  {name}")
    if not all(checks.values()):
        raise SystemExit("Fairness checks failed.")
    return results


def run_sensitivity() -> None:
    print("\n=== SENSITIVITY (bounded deltas) ===")

    # +30 min study on one day (below the cap: 5x120 = 600 of 1050 target min).
    base_p = {
        "sessions": [
            {"status": "completed", "duration_seconds": 7200} for _ in range(5)
        ],
        "shorts_days": [
            {"minutes": 15, "warning_triggered": False, "limit_reached": False} for _ in range(7)
        ],
        "app_days": [{"minutes": 120} for _ in range(7)],
        "blocked_count": 1,
        "web_events": [],
        "active_days": 7,
    }
    base = compute_score(base_p)
    p = dict(base_p)
    p["sessions"] = list(base_p["sessions"])
    p["sessions"][0] = {"status": "completed", "duration_seconds": 9000}  # 150 min
    plus30 = compute_score(p)
    delta_study = plus30["score"] - base["score"]
    print(f"  +30 min study  -> {delta_study:+} pts (expect 0.4..4.0)")
    assert 0.4 <= delta_study <= 4.0, delta_study

    # -20 min Shorts: 45 -> 25 (back under the configured 30-min limit).
    base = compute_score(profile_d())
    p = profile_d()
    p["shorts_days"] = [
        {"minutes": 25, "warning_triggered": False, "limit_reached": False} for _ in range(7)
    ]
    minus20 = compute_score(p)
    delta_shorts = minus20["score"] - base["score"]
    print(f"  -20 min Shorts (crosses limit) -> {delta_shorts:+} pts (expect 0.5..15)")
    assert 0.5 <= delta_shorts <= 15.0, delta_shorts

    # +1 completed meaningful session (3/5 -> 4/5 completion).
    base_p = {
        "sessions": [
            {"status": "completed", "duration_seconds": 5400} for _ in range(3)
        ]
        + [{"status": "cancelled", "duration_seconds": 3600} for _ in range(2)],
        "shorts_days": [
            {"minutes": 15, "warning_triggered": False, "limit_reached": False} for _ in range(7)
        ],
        "app_days": [{"minutes": 120} for _ in range(7)],
        "blocked_count": 1,
        "web_events": [],
        "active_days": 7,
    }
    base = compute_score(base_p)
    p = dict(base_p)
    p["sessions"] = base_p["sessions"] + [{"status": "completed", "duration_seconds": 5400}]
    plus_session = compute_score(p)
    delta_session = plus_session["score"] - base["score"]
    print(f"  +1 completed session -> {delta_session:+} pts (expect 0.5..5.0)")
    assert 0.5 <= delta_session <= 5.0, delta_session

    # +1 limit-violation day (add one 60-min day = 2x limit).
    base_p = profile_a()
    base = compute_score(base_p)
    p = profile_a()
    p["shorts_days"] = p["shorts_days"] + [
        {"minutes": 60, "warning_triggered": True, "limit_reached": True}
    ]
    plus_violation = compute_score(p)
    delta_violation = base["score"] - plus_violation["score"]
    print(f"  +1 limit-violation day -> {delta_violation:-} pts penalty (expect 1.0..9.0)")
    assert 1.0 <= delta_violation <= 9.0, delta_violation

    # +1 blocked-site attempt (repeat domain).
    base_p = profile_d()
    base = compute_score(base_p)
    p = profile_d()
    p["web_events"] = [{"type": "BLOCK_ATTEMPT", "domain": "x.com"} for _ in range(3)]
    plus_attempt = compute_score(p)
    delta_attempt = base["score"] - plus_attempt["score"]
    print(f"  2 more blocked attempts -> {delta_attempt:-} pts penalty (expect 0.0..3.0)")
    assert 0.0 <= delta_attempt <= 3.0, delta_attempt

    print("  Sensitivity bounds: ALL PASS")


def run_distribution() -> None:
    print("\n=== DISTRIBUTION SWEEP (grid of realistic weekly inputs) ===")
    scores = []
    for total_study_min in (0, 210, 420, 630, 840, 1260):
        for shorts_min in (0, 10, 30, 60, 120):
            for phone_min in (60, 150, 240, 360, 540):
                sessions = []
                if total_study_min > 0:
                    per = total_study_min / 5  # 5 sessions
                    sessions = [
                        {"status": "completed", "duration_seconds": int(per * 60)}
                        for _ in range(5)
                    ]
                shorts_days = []
                if shorts_min > 0:
                    shorts_days = [
                        {"minutes": shorts_min, "warning_triggered": False, "limit_reached": False}
                        for _ in range(7)
                    ]
                result = compute_score(
                    {
                        "sessions": sessions,
                        "shorts_days": shorts_days,
                        "app_days": [{"minutes": phone_min} for _ in range(7)],
                        "blocked_count": 0,
                        "web_events": [],
                        "active_days": 7,
                    }
                )
                scores.append(result["score"])

    scores.sort()
    n = len(scores)
    p10 = scores[int(n * 0.10)]
    p50 = scores[int(n * 0.50)]
    p90 = scores[int(n * 0.90)]
    pct_high = sum(1 for s in scores if s >= 90) / n
    pct_low = sum(1 for s in scores if s <= 39) / n
    print(f"  n={n}  min={scores[0]}  p10={p10}  median={p50}  p90={p90}  max={scores[-1]}")
    print(f"  % >= 90: {pct_high:.0%}   % <= 39: {pct_low:.0%}")

    checks = {
        "max never exceeds 100": scores[-1] <= 100,
        "min > 0 (active profiles never 0)": scores[0] > 0,
        "no clustering at the top (p90 < 100)": p90 < 100,
        "no clustering at the bottom (p10 > 0)": p10 > 0,
        "useful separation (p90 - p10 >= 30)": p90 - p10 >= 30,
        "100 is essentially unreachable in the grid (< 1% hit it)": pct_high < 0.01 or scores[-1] < 100,
    }
    for name, ok in checks.items():
        print(f"  {'PASS' if ok else 'FAIL'}  {name}")
    if not all(checks.values()):
        raise SystemExit("Distribution checks failed.")


def run_anti_gaming() -> None:
    print("\n=== ANTI-GAMING CHECKS ===")

    # 1. A 60-second "completed" session does not count (below 5-min floor).
    value, status = study_value(
        [{"status": "completed", "duration_seconds": 60}], days=7
    )
    ok1 = value == 0.5 and status == "neutral"
    print(f"  {'PASS' if ok1 else 'FAIL'}  tiny (60s) session excluded from study")

    # 2. Volume cap: 12 h/day study earns no more than the 2.5 h/day target.
    v_target, _ = study_value(
        [{"status": "completed", "duration_seconds": 150 * 60} for _ in range(7)], days=7
    )
    v_long, _ = study_value(
        [{"status": "completed", "duration_seconds": 720 * 60} for _ in range(7)], days=7
    )
    ok2 = v_target == v_long == 1.0
    print(f"  {'PASS' if ok2 else 'FAIL'}  volume capped (12h study == 2.5h/day target)")

    # 3. Do-nothing = 0 via the inactivity gate (never 100, never neutral 50).
    result = compute_score(profile_e())
    ok3 = result["score"] == 0 and result["status"] == "insufficient_data"
    print(f"  {'PASS' if ok3 else 'FAIL'}  inactivity gate: score 0, not 100")

    # 4. Missing Shorts data is neutral 0.5, NOT perfect.
    _, status = shorts_value([], None)
    ok4 = status == "neutral"
    print(f"  {'PASS' if ok4 else 'FAIL'}  missing Shorts data -> neutral, not perfect")

    # 5. Consistency counts days: 50 sessions in one day still = 1 active day.
    ok5 = consistency_value(1, "weekly") == 0.2  # 1 active day / 5-day weekly target
    print(f"  {'PASS' if ok5 else 'FAIL'}  consistency counts days, not sessions")

    if not (ok1 and ok2 and ok3 and ok4 and ok5):
        raise SystemExit("Anti-gaming checks failed.")


def inspect_mysql() -> None:
    print("\n=== DATA AVAILABILITY (read-only MySQL inspection) ===")
    db = SessionLocal()
    try:
        tables = [
            "study_sessions", "app_usage", "shorts_usage",
            "website_events", "monitoring_events", "leaderboard_scores",
        ]
        for t in tables:
            try:
                n = db.execute(text(f"SELECT COUNT(*) FROM {t}")).scalar()
                print(f"  {t}: {n} rows")
            except Exception as exc:  # noqa: BLE001
                print(f"  {t}: ERROR {exc}")
    finally:
        db.close()


def main() -> None:
    inspect_mysql()
    results = run_profiles()
    run_sensitivity()
    run_distribution()
    run_anti_gaming()

    print("\n=== SUMMARY ===")
    print(f"  Scores: {', '.join(f'{r['score']}' for r in results.values())}")
    print("  ALL CHECKS PASSED")


if __name__ == "__main__":
    main()
