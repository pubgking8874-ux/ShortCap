"""errors.py — ShortsCap backend: web service exceptions.

Domain errors raised by the web services. They carry NO HTTP details — the
router maps each type to the appropriate status code:

  - WebNotFoundError   -> 404 (also used for unknown devices / another user's
    device or website, so other users' records are never revealed)
  - WebConflictError   -> 409 (a duplicate normalized domain for the same user)
  - WebValidationError -> 422 (domain-level input problems the schema layer
    cannot express, e.g. a malformed domain or an inverted date range)
"""


class WebError(Exception):
    """Base class for all web domain errors."""


class WebNotFoundError(WebError):
    """A referenced record does not exist (or belongs to another user)."""


class WebConflictError(WebError):
    """The operation conflicts with existing data (e.g. duplicate domain)."""


class WebValidationError(WebError):
    """The submitted data is invalid in a way the schema layer can't check."""
