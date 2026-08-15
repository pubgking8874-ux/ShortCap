"""errors.py — ShortsCap backend: study service exceptions.

Domain errors raised by the study services. They carry NO HTTP details — the
router maps each type to the appropriate status code:
  - StudyNotFoundError  -> 404 (also used for cross-user access, so the
    existence of another user's records is never revealed)
  - StudyStateError     -> 400 (invalid state transitions, e.g. ending a
    completed session, starting a break on a completed session, overlapping
    active breaks)
"""


class StudyError(Exception):
    """Base class for all study domain errors."""


class StudyNotFoundError(StudyError):
    """A record does not exist (or belongs to another user)."""


class StudyStateError(StudyError):
    """The requested transition is invalid for the record's current state."""
