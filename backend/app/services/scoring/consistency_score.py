"""consistency_score.py — ShortsCap backend: consistency score component.

Implements the approved Phase 14A consistency formula:

    v_consistency = min(1, active_days / target(period))

  * active days are DISTINCT calendar days with any recorded activity —
    never session counts (50 tiny sessions in one day = 1 active day, so
    artificial activity spikes earn nothing extra).
  * targets: daily 1, weekly 5, monthly 20.

Pure and deterministic; no database access.
"""

from app.services.scoring.constants import CONSISTENCY_TARGET_DAYS


def consistency_value(active_days: int, period_type: str) -> float:
    """Return the consistency component value in [0, 1]."""
    target = CONSISTENCY_TARGET_DAYS.get(period_type, 1)
    return round(min(1.0, active_days / target), 4)
