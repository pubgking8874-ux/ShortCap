"""blocked_website.py — ShortsCap backend: blocked website repository.

BlockedWebsiteRepository — database operations ONLY (no business rules).
Domain normalization, validation, ownership checks and duplicate handling
live in the service layer. The schema's unique constraint
(`uq_blocked_websites_user_domain` on user_id + normalized_domain) is the
backstop against duplicates.
"""

from sqlalchemy.orm import Session

from app.models.blocked_website import BlockedWebsite


class BlockedWebsiteRepository:
    """Data access for the `blocked_websites` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, website_id: int) -> BlockedWebsite | None:
        """Return one row by id, or None."""
        return (
            self.db.query(BlockedWebsite)
            .filter(BlockedWebsite.id == website_id)
            .first()
        )

    def get_by_normalized_domain(
        self, user_id: int, normalized_domain: str
    ) -> BlockedWebsite | None:
        """Return the user's row for a normalized domain, or None."""
        return (
            self.db.query(BlockedWebsite)
            .filter(
                BlockedWebsite.user_id == user_id,
                BlockedWebsite.normalized_domain == normalized_domain,
            )
            .first()
        )

    def list_by_user(self, user_id: int) -> list[BlockedWebsite]:
        """Return all of a user's website rows, oldest first."""
        return (
            self.db.query(BlockedWebsite)
            .filter(BlockedWebsite.user_id == user_id)
            .order_by(BlockedWebsite.id.asc())
            .all()
        )

    def exists_for_user(self, user_id: int, normalized_domain: str) -> bool:
        """True when the user already has a row for the normalized domain."""
        return self.get_by_normalized_domain(user_id, normalized_domain) is not None

    def create(self, user_id: int, data: dict) -> BlockedWebsite:
        """Insert a blocked-website row for the user."""
        website = BlockedWebsite(user_id=user_id, **data)
        self.db.add(website)
        self.db.commit()
        self.db.refresh(website)
        return website

    def update(self, website: BlockedWebsite, data: dict) -> BlockedWebsite:
        """Apply only the supplied, non-None values to an existing row."""
        for key, value in data.items():
            if value is not None:
                setattr(website, key, value)
        self.db.commit()
        self.db.refresh(website)
        return website

    def delete(self, website: BlockedWebsite) -> None:
        """Delete an existing row."""
        self.db.delete(website)
        self.db.commit()
