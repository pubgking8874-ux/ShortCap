"""permission.py — ShortsCap backend: permission state repository.

Database operations only. The permission_states table holds MULTIPLE rows per
user (one per permission key) and is only a last-known sync mirror — the
Android system remains the real source of truth.
"""

from sqlalchemy.orm import Session

from app.models.permission_state import PermissionState


class PermissionStateRepository:
    """Data access for the `permission_states` table (list + upsert by key)."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def list_by_user_id(self, user_id: int) -> list[PermissionState]:
        return (
            self.db.query(PermissionState)
            .filter(PermissionState.user_id == user_id)
            .order_by(PermissionState.permission_key)
            .all()
        )

    def get_by_user_and_key(self, user_id: int, permission_key: str) -> PermissionState | None:
        return (
            self.db.query(PermissionState)
            .filter(
                PermissionState.user_id == user_id,
                PermissionState.permission_key == permission_key,
            )
            .first()
        )

    def upsert(self, user_id: int, data: dict) -> PermissionState:
        """Update the (user, key) row or insert it; never duplicates a key."""
        permission_key = data["permission_key"]
        state = self.get_by_user_and_key(user_id, permission_key)
        if state is None:
            state = PermissionState(user_id=user_id, permission_key=permission_key)
            self.db.add(state)
        for key, value in data.items():
            if key == "permission_key":
                continue
            if value is not None:
                setattr(state, key, value)
        self.db.commit()
        self.db.refresh(state)
        return state
