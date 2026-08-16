"""logging.py — ShortsCap backend: sanitized request logging middleware.

Access log for developers. Deliberately minimal and SANITIZED:

  - logs only method, path, HTTP status and duration;
  - NEVER logs request headers (they can carry credentials/tokens),
    request bodies, query strings, or anything user-supplied;
  - active only when settings.DEBUG is true, so release/production
    deployments produce no access-log noise and no accidental leakage.

Production diagnostics belong in the deployment layer (structured logging /
monitoring) and are documented as such in `docs/security_audit.md`.
"""

import logging
import time

from app.config import settings

_logger = logging.getLogger("shortscap.access")


class RequestLoggingMiddleware:
    """Sanitized access log — DEBUG only."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not settings.DEBUG:
            await self.app(scope, receive, send)
            return

        start = time.perf_counter()
        status = "-"

        async def send_wrapper(message):
            nonlocal status
            if message["type"] == "http.response.start":
                status = str(message.get("status", "-"))
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        finally:
            duration_ms = (time.perf_counter() - start) * 1000
            # Path only — never query string (may contain credentials).
            _logger.info(
                "%s %s -> %s (%.1f ms)",
                scope.get("method", ""),
                scope.get("path", ""),
                status,
                duration_ms,
            )
