"""limit_cycle.py — ShortsCap backend: Shorts limit cycle repository.

ShortsLimitCycleRepository — database operations ONLY (no business rules).
Activation rules, expiry handling, state transitions and count reconciliation
live in the service layer. Every query is scoped to the caller's `user_id`
(cross-user cycles are never returned).
"""

from datetime import datetime

from sqlalchemy.orm import Session

from app.models.shorts_limit_cycle import ShortsLimitCycle


class ShortsLimitCycleRepository:
    """Data access for the `shorts_limit_cycles` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_active(self, user_id: int) -> ShortsLimitCycle | None:
        """Return the user's single active cycle (is_active=True), or None.

        The unique constraint on (user_id, is_active) guarantees at most one
        such row exists per user; the query still filters to be explicit.
        """
        return (
            self.db.query(ShortsLimitCycle)
            .filter(
                ShortsLimitCycle.user_id == user_id,
                ShortsLimitCycle.is_active.is_(True),
            )
            .first()
        )

    def get_by_id(self, user_id: int, cycle_id: int) -> ShortsLimitCycle | None:
        """Return one cycle by id, but only if it belongs to the user."""
        return (
            self.db.query(ShortsLimitCycle)
            .filter(ShortsLimitCycle.id == cycle_id, ShortsLimitCycle.user_id == user_id)
            .first()
        )

    def create(
        self,
        user_id: int,
        *,
        limit_count: int,
        cycle_started_at: datetime,
        cycle_expires_at: datetime,
        device_id: int | None = None,
        current_count: int = 0,
        status: str = "ACTIVE",
    ) -> ShortsLimitCycle:
        """Insert one cycle row (the new active window)."""
        cycle = ShortsLimitCycle(
            user_id=user_id,
            device_id=device_id,
            limit_count=limit_count,
            current_count=current_count,
            cycle_started_at=cycle_started_at,
            cycle_expires_at=cycle_expires_at,
            status=status,
            is_active=True,
        )
        self.db.add(cycle)
        self.db.commit()
        self.db.refresh(cycle)
        return cycle

    def update(self, cycle: ShortsLimitCycle, data: dict) -> ShortsLimitCycle:
        """Apply every supplied key to an existing cycle.

        Unlike the usage/settings repositories, NULL values are applied
        deliberately here: clearing `is_active` (releasing the single active
        slot) is a normal state transition (expired / disabled / finished
        windows), and skipping None would leak the guard.
        """
        for key, value in data.items():
            setattr(cycle, key, value)
        self.db.commit()
        self.db.refresh(cycle)
        return cycle

    def list_history(self, user_id: int, limit: int = 50) -> list[ShortsLimitCycle]:
        """Return the user's cycles, newest first (for audit/debug use)."""
        return (
            self.db.query(ShortsLimitCycle)
            .filter(ShortsLimitCycle.user_id == user_id)
            .order_by(ShortsLimitCycle.cycle_started_at.desc(), ShortsLimitCycle.id.desc())
            .limit(limit)
            .all()
        )
