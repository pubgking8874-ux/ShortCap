"""study_score.py — ShortsCap backend: study score component (pure).

Implements the approved Phase 14A study formula:

    v_study = 0.6 * completion + 0.4 * capped volume

  * completion  = completed meaningful sessions / meaningful sessions
  * volume      = min(1, total_study_min / (150 * days))  (linear-to-cap)

Only *meaningful* sessions count: terminal status (completed | cancelled)
and `actual_duration_seconds >= 300`. `actual_duration_seconds` is the
server-computed value (ended_at - started_at) from the study layer — client
durations are never trusted. No meaningful sessions -> neutral 0.5 (no study
data is never treated as perfect study). Pure and deterministic: no database
access, no writes.
"""

from app.services.scoring.constants import (
    MIN_MEANINGFUL_SESSION_SEC,
    STUDY_TARGET_MIN_PER_DAY,
)


def study_value(data: dict, days: int) -> tuple[float, str]:
    """Return (value in [0,1], status) for the study component.

    `data` is a plain dict with aggregates for the period (already filtered
    to meaningful sessions by the query layer):
      {"completed": int, "total": int, "total_seconds": int}
    """
    total = int(data.get("total", 0) or 0)
    if total == 0:
        return 0.5, "neutral"

    completed = int(data.get("completed", 0) or 0)
    completion = completed / total

    total_min = int(data.get("total_seconds", 0) or 0) / 60
    volume = min(1.0, total_min / (STUDY_TARGET_MIN_PER_DAY * max(1, days)))

    return round(0.6 * completion + 0.4 * volume, 4), "evaluated"


# Re-exported so the anti-gaming constant lives in one place.
__all__ = ["study_value", "MIN_MEANINGFUL_SESSION_SEC"]
