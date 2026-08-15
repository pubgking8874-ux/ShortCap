"""domain.py — ShortsCap backend: domain normalization + validation.

Single reusable utility for website domains. Mirrors the Android app's own
rules (`web/DomainValidator.kt` + `normalizeWebDomain` in `web/WebComponents.kt`)
so the backend accepts exactly what the app accepts:

  - lowercases and strips scheme (`https://`, `http://`, …)
  - strips a leading `www.`
  - strips path / query / fragment (domain-only storage)
  - strips trailing dots and slashes
  - requires dot-separated labels (each 1–63 chars of letters/digits/hyphens,
    never starting/ending with a hyphen); a bare single label such as
    `localhost` is rejected
  - localhost and pure IPv4 addresses are NOT treated as normal public
    domains (the product does not support them as blocked domains)

No DNS / network checks are performed — pure syntax normalization.
"""

import re

# Same shape as the Android DomainValidator regex.
_DOMAIN_RE = re.compile(
    r"^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
    r"(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
)

# Scheme prefix, e.g. https://, http://, ftp://
_SCHEME_RE = re.compile(r"^[a-z][a-z0-9+.-]*://")

# Pure IPv4 (digits only) — rejected as "not a normal public domain".
_IPV4_RE = re.compile(r"^\d{1,3}(\.\d{1,3}){3}$")


def normalize_domain(input_value: str) -> str | None:
    """Normalize a user-supplied URL/domain to one canonical bare domain.

    ``https://youtube.com/``, ``http://www.youtube.com``, ``WWW.YouTube.com``
    and ``youtube.com`` all normalize to ``youtube.com``. Returns ``None``
    when the input cannot be normalized to a valid domain (malformed input,
    bare label, localhost, IP address, …).
    """
    if input_value is None or not input_value.strip():
        return None

    value = input_value.strip().lower()

    # Remove scheme (https://, http://, ftp://, ...).
    value = _SCHEME_RE.sub("", value)

    # Remove a leading "www." (repeatedly, matching the Android helper).
    while value.startswith("www."):
        value = value[4:]

    # Strip path / query / fragment — domain-only storage.
    for separator in ("/", "?", "#"):
        value = value.split(separator, 1)[0]

    # Trailing dot/slash and surrounding whitespace.
    value = value.rstrip(".").strip()

    if not value or not _DOMAIN_RE.match(value):
        return None

    # Do not silently treat localhost / IP addresses as public domains.
    if _IPV4_RE.match(value):
        return None

    return value


def is_valid_domain(input_value: str) -> bool:
    """True when [input_value] normalizes to a valid domain."""
    return normalize_domain(input_value) is not None
