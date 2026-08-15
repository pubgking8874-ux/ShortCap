"""distraction_score.py — ShortsCap backend: distraction control component.

Implements the approved Phase 14A distraction formula. EXPLICIT LIMITATION
(kept from the spec): the current schema has NO app categorization, so apps
are never labelled "distracting" (YouTube/Instagram may be used for study).
The only reliable metric is total phone time per day from `app_usage`:

    s_d = 1.0                        if x_d <= 240 min (4 h)
    s_d = max(0, 1 - (x_d - 240)/240) otherwise         (8 h -> 0)
    enforcement (LIMIT_REACHED / APP_RESTRICTED events) multiplies the
    period value by (1 - min(0.10, 0.02 * n))

    v_distraction = sum(s_d * x_d) / sum(x_d)   (usage-weighted)

This penalizes EXCESSIVE phone time but never rewards minimal usage beyond
the full mark (respects "not simply reward using the phone less"). No app
data -> neutral 0.5. Pure and deterministic; no database access.
"""

from app.services.scoring.constants import DISTRACTION_THRESHOLD_MIN


def distraction_value(data: dict) -> tuple[float, str]:
    """Return (value in [0,1], status) for the distraction component.

    `data` is a plain dict:
      {"days": [{"minutes": float}, ...],
       "enforcement_events": int}   # LIMIT_REACHED / APP_RESTRICTED count
    """
    days = [d for d in data.get("days", []) if (d.get("minutes") or 0) > 0]
    if not days:
        return 0.5, "neutral"

    total = 0.0
    weight = 0.0
    for d in days:
        x = float(d["minutes"])
        excess = max(0.0, x - DISTRACTION_THRESHOLD_MIN)
        s = 1.0 if x <= DISTRACTION_THRESHOLD_MIN else max(
            0.0, 1.0 - excess / DISTRACTION_THRESHOLD_MIN
        )
        total += s * x
        weight += x

    value = total / weight
    n = int(data.get("enforcement_events", 0) or 0)
    if n > 0:
        value *= 1 - min(0.10, 0.02 * n)

    return round(value, 4), "evaluated"
