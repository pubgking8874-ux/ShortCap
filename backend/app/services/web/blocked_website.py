"""blocked_website.py — ShortsCap backend: blocked website service.

BlockedWebsiteService — business-level operations for blocked websites:

  * every row belongs to the CURRENT user (never a client-supplied id)
  * domains are normalized through the shared `app/utils/domain.py` utility —
    ``https://youtube.com/``, ``http://www.youtube.com``, ``WWW.YouTube.com``
    and ``youtube.com`` all become ``youtube.com``
  * malformed domains are rejected (422) BEFORE any persistence
  * duplicate normalized domains for the same user are rejected (409); the
    schema's unique constraint `uq_blocked_websites_user_domain` is the backstop
  * updates re-normalize the domain when supplied and preserve ownership
  * the `/check` answer comes from the user's own rows only

No real-time blocking happens here — the backend stores configuration; the
Android app remains the real-time enforcement authority.
"""

from sqlalchemy.orm import Session

from app.models.blocked_website import BlockedWebsite
from app.repositories.web.blocked_website import BlockedWebsiteRepository
from app.services.web.errors import (
    WebConflictError,
    WebNotFoundError,
    WebValidationError,
)
from app.utils.domain import normalize_domain


class BlockedWebsiteService:
    """Business operations for blocked website configuration."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = BlockedWebsiteRepository(db)

    def _resolve_owned(self, user_id: int, website_id: int) -> BlockedWebsite:
        """Return the user's website row, or 404 (never revealing another
        user's record)."""
        website = self.repository.get_by_id(website_id)
        if website is None or website.user_id != user_id:
            raise WebNotFoundError("Blocked website not found.")
        return website

    @staticmethod
    def _normalize_required(domain: str) -> str:
        """Normalize a user-supplied domain, rejecting malformed input."""
        normalized = normalize_domain(domain)
        if normalized is None:
            raise WebValidationError("Invalid domain.")
        return normalized

    def _reject_duplicate(self, user_id: int, normalized: str) -> None:
        """Reject a normalized domain the user already has a row for."""
        if self.repository.exists_for_user(user_id, normalized):
            raise WebConflictError("Domain is already blocked for this user.")

    def create(self, user_id: int, data: dict) -> BlockedWebsite:
        """Create a blocked website for the user. The domain is normalized;
        duplicates for the same user are rejected (409)."""
        raw_domain = data["domain"]
        normalized = self._normalize_required(raw_domain)
        self._reject_duplicate(user_id, normalized)
        return self.repository.create(
            user_id,
            {
                "domain": raw_domain.strip(),
                "normalized_domain": normalized,
                "verification_status": data.get("verification_status", "pending"),
                "is_blocked": data.get("is_blocked", True),
            },
        )

    def get(self, user_id: int, website_id: int) -> BlockedWebsite:
        """Return one of the user's blocked websites (404 when absent)."""
        return self._resolve_owned(user_id, website_id)

    def list(self, user_id: int) -> list[BlockedWebsite]:
        """Return all of the user's blocked websites, oldest first."""
        return self.repository.list_by_user(user_id)

    def update(self, user_id: int, website_id: int, data: dict) -> BlockedWebsite:
        """Partial update of one of the user's blocked websites.

        The domain is re-normalized when supplied; the resulting normalized
        domain must not collide with another of the user's rows. Ownership is
        preserved (never another user's row)."""
        website = self._resolve_owned(user_id, website_id)

        updates: dict = {}
        if data.get("domain") is not None:
            normalized = self._normalize_required(data["domain"])
            if (
                normalized != website.normalized_domain
                and self.repository.exists_for_user(user_id, normalized)
            ):
                raise WebConflictError("Domain is already blocked for this user.")
            updates["domain"] = data["domain"].strip()
            updates["normalized_domain"] = normalized
        if data.get("verification_status") is not None:
            updates["verification_status"] = data["verification_status"]
        if data.get("is_blocked") is not None:
            updates["is_blocked"] = data["is_blocked"]

        return self.repository.update(website, updates)

    def delete(self, user_id: int, website_id: int) -> None:
        """Delete one of the user's blocked websites (404 when absent)."""
        website = self._resolve_owned(user_id, website_id)
        self.repository.delete(website)

    def check(self, user_id: int, raw_domain: str) -> dict:
        """Answer whether the user currently has the (normalized) domain
        blocked. An invalid domain answers with is_present=False rather than
        an error — the caller asked a question about an arbitrary string."""
        normalized = normalize_domain(raw_domain)
        if normalized is None:
            return {
                "domain": raw_domain.strip(),
                "normalized_domain": "",
                "is_present": False,
                "is_blocked": False,
            }
        website = self.repository.get_by_normalized_domain(user_id, normalized)
        return {
            "domain": raw_domain.strip(),
            "normalized_domain": normalized,
            "is_present": website is not None,
            "is_blocked": website.is_blocked if website is not None else False,
        }
