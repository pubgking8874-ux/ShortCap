"""security.py — ShortsCap backend: security middleware.

A minimal, API-appropriate header set (added to every HTTP response):

  - X-Content-Type-Options: nosniff   — prevents MIME-sniffing attacks
  - X-Frame-Options: DENY             — no rendering in a frame anywhere
                                        (harmless for a JSON API, protects any
                                        future HTML/docs page)
  - Referrer-Policy: no-referrer      — never leak the API URL as a referrer

Browser-only headers with no JSON-API purpose (CSP, HSTS, …) are deliberately
NOT added here — HSTS and TLS belong to the deployment (proxy/load balancer)
layer and are documented as such in `docs/security_audit.md`.
"""

_HEADERS: tuple[tuple[str, str], ...] = (
    ("x-content-type-options", "nosniff"),
    ("x-frame-options", "DENY"),
    ("referrer-policy", "no-referrer"),
)


class SecurityHeadersMiddleware:
    """Adds the static security headers to every HTTP response."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        async def send_wrapper(message):
            if message["type"] == "http.response.start":
                existing = dict(
                    (name.lower(), value) for name, value in message.get("headers", [])
                )
                extra = [
                    (name.encode("latin-1"), value.encode("latin-1"))
                    for name, value in _HEADERS
                    if name not in existing
                ]
                message = {
                    **message,
                    "headers": list(message.get("headers", [])) + extra,
                }
            await send(message)

        await self.app(scope, receive, send_wrapper)
