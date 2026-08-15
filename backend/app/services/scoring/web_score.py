"""web_score.py — ShortsCap backend: web discipline score component.

Implements the approved Phase 14A web formula. A single BLOCK_ATTEMPT is NOT
a sin — it can mean the enforcement system worked — so only persistence and
giving in cost points:

    p = min(0.15, 0.05*attempts) + min(0.10, 0.05*unblocks)
        + min(0.10, 0.05*repeat_domains)          # domains with >= 3 attempts
    v_web = max(0.5, 1 - p)

  * user has >= 1 ACTIVE blocked website and 0 attempts  -> 1.0
    (perfect avoidance; the configuration itself is data)
  * user has no blocked websites AND no events           -> neutral 0.5

Pure and deterministic; no database access.
"""


def web_value(data: dict) -> tuple[float, str]:
    """Return (value in [0,1], status) for the web component.

    `data` is a plain dict:
      {"blocked_active": int,        # active blocked_websites rows in the period
       "events": [{"type": str, "domain": str|None}, ...]}
    """
    blocked_active = int(data.get("blocked_active", 0) or 0)
    events = data.get("events", [])

    attempts = sum(1 for e in events if e.get("type") == "BLOCK_ATTEMPT")
    unblocks = sum(1 for e in events if e.get("type") == "UNBLOCKED")

    domain_counts: dict[str, int] = {}
    for e in events:
        if e.get("type") == "BLOCK_ATTEMPT" and e.get("domain"):
            domain_counts[e["domain"]] = domain_counts.get(e["domain"], 0) + 1
    repeats = sum(1 for count in domain_counts.values() if count >= 3)

    if blocked_active == 0 and not events:
        return 0.5, "neutral"
    if blocked_active > 0 and attempts == 0:
        return 1.0, "evaluated"

    penalty = (
        min(0.15, 0.05 * attempts)
        + min(0.10, 0.05 * unblocks)
        + min(0.10, 0.05 * repeats)
    )
    return round(max(0.5, 1.0 - penalty), 4), "evaluated"
