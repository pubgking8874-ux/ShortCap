"""errors.py — ShortsCap backend: monitoring service exceptions.

Domain errors raised by the monitoring services. They carry NO HTTP details —
the router maps each type to the appropriate status code:
  - MonitoringNotFoundError -> 404 (also used for unknown devices / another
    user's device, so other users' records are never revealed)
  - MonitoringValidationError -> 422 (domain-level input problems that the
    schema layer cannot express, e.g. an inverted date range)
"""


class MonitoringError(Exception):
    """Base class for all monitoring domain errors."""


class MonitoringNotFoundError(MonitoringError):
    """A referenced record does not exist (or belongs to another user)."""


class MonitoringValidationError(MonitoringError):
    """The submitted data is invalid in a way the schema layer can't check."""
