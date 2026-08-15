"""errors.py — ShortsCap backend: shorts service exceptions.

Domain errors raised by the shorts services. They carry NO HTTP details —
the router maps each type to the appropriate status code:
  - ShortsNotFoundError -> 404 (also used for unknown devices / another
    user's device, so other users' records are never revealed)
  - ShortsValidationError -> 422 (domain-level input problems that the
    schema layer cannot express, e.g. an inverted date range)
"""


class ShortsError(Exception):
    """Base class for all shorts domain errors."""


class ShortsNotFoundError(ShortsError):
    """A referenced record does not exist (or belongs to another user)."""


class ShortsValidationError(ShortsError):
    """The submitted data is invalid in a way the schema layer can't check."""
