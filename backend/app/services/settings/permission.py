"""permission.py — ShortsCap backend: permission state service.

Business-level operations for the last-known permission sync mirror. The
Android system remains the real source of truth — this stores only the
synchronized/last-known state.
"""

from sqlalchemy.orm import Session

from app.models.permission_state import PermissionState
from app.repositories.settings import PermissionStateRepository


class PermissionStateService:
    """Business operations for permission state sync."""

    def __init__(self, db: Session) -> None:
        self.repository = PermissionStateRepository(db)

    def get_states(self, user_id: int) -> list[PermissionState]:
        """Return all stored permission states for the user (empty list if
        nothing has been synced yet — no defaults are invented)."""
        return self.repository.list_by_user_id(user_id)

    def update_states(self, user_id: int, items: list[dict]) -> list[PermissionState]:
        """Upsert each supplied permission state, then return the full list."""
        for item in items:
            self.repository.upsert(user_id, item)
        return self.repository.list_by_user_id(user_id)
