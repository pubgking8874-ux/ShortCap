"""datetime.py — ShortsCap backend: date/time helpers.

All timestamps are stored in MySQL `DATETIME` columns, which have no
timezone. The app writes **UTC** everywhere (matching `func.now()`-style
server defaults on `created_at` / `updated_at`), so server-side duration
calculations (`ended_at - started_at`) are exact regardless of the client's
timezone. Never mix naive local datetimes with these values.
"""

from datetime import datetime, timezone


def utcnow() -> datetime:
    """Current time as a NAIVE UTC datetime, ready for MySQL DATETIME.

    ``datetime.now(timezone.utc)`` would produce a timezone-AWARE value; the
    ORM silently strips the tzinfo when storing into a plain DATETIME
    column, which is exactly the naive-UTC value this helper returns. Using
    it consistently keeps every written timestamp in the same frame.
    """
    return datetime.now(timezone.utc).replace(tzinfo=None)
