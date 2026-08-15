"""batch.py — ShortsCap backend: batch scoring for the leaderboard engine.

Phase 15B: the Rank / Leaderboard engine must consume the approved Your
Score engine (the ONLY source of score values) without running the full
single-user pipeline once per user (N+1). This module computes period scores
for MANY users in a handful of grouped SQL queries and feeds each user's
collected data through the EXACT same module-level helpers used by the
single-user API (`assemble_score`, `component_values`, `PERIOD_BUILDERS`) —
so a leaderboard score is byte-identical to what `GET /score/*` would return
for the same user and period.

No business logic lives here (eligibility, ranking, tie-breaking and
pagination belong to RankService). No writes, no schema changes, no
leaderboard_scores snapshots — the board stays DYNAMIC (Phase 15A §9).
"""

from datetime import date

from sqlalchemy.orm import Session

from app.services.scoring.queries import ScoringQueries
from app.services.scoring.score_service import (
    PERIOD_BUILDERS,
    assemble_score,
)
from app.utils.datetime import utcnow


def batch_scores(
    db: Session,
    user_ids: list[int],
    period_type: str,
    report_date: date | None = None,
) -> dict[int, dict]:
    """Compute the Your Score for every user_id in one period.

    Returns ``{user_id: {user_id, period, score, status, components, activity}}``
    where ``components`` is the list of component dicts and ``activity`` the
    activity dict — the same structures `ScoreService.score` produces. Users
    with no data get the approved `insufficient_data` result (score 0).
    """
    if not user_ids:
        return {}

    if report_date is None:
        report_date = utcnow().date()
    period = PERIOD_BUILDERS[period_type](report_date)
    days = (period["end_date"] - period["start_date"]).days + 1
    start, end = period["start_date"], period["end_date"]

    queries = ScoringQueries(db)

    # Grouped SQL — one pass per source table for ALL users (no N+1).
    study = queries.study_aggregates_by_user(user_ids, start, end)
    shorts_days = queries.shorts_days_by_user(user_ids, start, end)
    app_days = queries.app_days_by_user(user_ids, start, end)
    enforcement = queries.enforcement_events_by_user(user_ids, start, end)
    blocked_active = queries.blocked_active_by_user(user_ids)
    web_events = queries.web_events_by_user(user_ids, start, end)
    shorts_limits = queries.shorts_limits_by_user(user_ids)
    active_days = queries.active_days_by_user(user_ids, start, end)

    results: dict[int, dict] = {}
    for uid in user_ids:
        collected = {
            "study": study.get(uid, {"completed": 0, "total": 0, "total_seconds": 0}),
            "shorts_days": shorts_days.get(uid, []),
            "shorts_limit": shorts_limits.get(uid),
            "app_days": app_days.get(uid, []),
            "enforcement_events": enforcement.get(uid, 0),
            "blocked_active": blocked_active.get(uid, 0),
            "web_events": web_events.get(uid, []),
            "active_days": active_days.get(uid, 0),
        }
        result = assemble_score(collected, period_type, days, period)
        results[uid] = {
            "user_id": uid,
            "period": result["period"],
            "score": result["score"],
            "status": result["status"],
            "components": result["components"],
            "activity": result["activity"],
        }
    return results
