"""shorts_score.py — ShortsCap backend: Shorts discipline score component.

Implements the approved Phase 14A Shorts formula: per-day discipline is
compared against the user's CONFIGURED daily limit (fallback 30 min/day),
weighted by that day's usage:

    s_d = 1.0                      if usage_d <= limit
    s_d = max(0, 1 - over/limit)   otherwise   (2x limit -> 0)
    s_d *= 0.9 if limit_reached on d; else *= 0.95 if warning_triggered

    v_shorts = sum(s_d * usage_d) / sum(usage_d)

Shorts from every platform are combined into one global behavior (the
cross-platform budget) — YouTube / Instagram / TikTok / Snapchat etc. are
NOT scored separately. No Shorts data -> neutral 0.5 (never perfect).
Pure and deterministic; no database access.
"""

from app.services.scoring.constants import SHORTS_DEFAULT_LIMIT_MIN


def shorts_value(data: dict, limit_min: int | None) -> tuple[float, str]:
    """Return (value in [0,1], status) for the Shorts component.

    `data` is a plain dict:
      {"days": [{"minutes": float, "warning_triggered": bool,
                 "limit_reached": bool}, ...]}
    """
    days = [d for d in data.get("days", []) if (d.get("minutes") or 0) > 0]
    if not days:
        return 0.5, "neutral"

    limit = limit_min if limit_min and limit_min > 0 else SHORTS_DEFAULT_LIMIT_MIN

    total = 0.0
    weight = 0.0
    for d in days:
        usage = float(d["minutes"])
        over = max(0.0, usage - limit)
        s = 1.0 if usage <= limit else max(0.0, 1.0 - over / limit)
        if d.get("limit_reached"):
            s *= 0.9
        elif d.get("warning_triggered"):
            s *= 0.95
        total += s * usage
        weight += usage

    return round(total / weight, 4), "evaluated"
