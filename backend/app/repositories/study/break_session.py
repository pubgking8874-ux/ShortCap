"""break_session.py — ShortsCap backend: break session repository.

BreakSessionRepository — database operations ONLY (no business rules).
Ownership checks and the no-overlapping-active-breaks rule live in the
service layer.
"""

from sqlalchemy.orm import Session

from app.models.break_session import BreakSession


class BreakSessionRepository:
    """Data access for the `break_sessions` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, break_id: int) -> BreakSession | None:
        """Return a break by id, or None if it does not exist."""
        return (
            self.db.query(BreakSession)
            .filter(BreakSession.id == break_id)
            .first()
        )

    def list_for_session(self, study_session_id: int) -> list[BreakSession]:
        """Return all breaks of one study session, oldest first."""
        return (
            self.db.query(BreakSession)
            .filter(BreakSession.study_session_id == study_session_id)
            .order_by(BreakSession.id.asc())
            .all()
        )

    def get_active_break_for_session(self, study_session_id: int) -> BreakSession | None:
        """Return the currently ACTIVE break of a study session, or None."""
        return (
            self.db.query(BreakSession)
            .filter(
                BreakSession.study_session_id == study_session_id,
                BreakSession.status == "active",
            )
            .first()
        )

    def create(self, data: dict) -> BreakSession:
        """Insert a break row."""
        break_session = BreakSession(**data)
        self.db.add(break_session)
        self.db.commit()
        self.db.refresh(break_session)
        return break_session

    def update(self, break_session: BreakSession, data: dict) -> BreakSession:
        """Apply only the supplied, non-None values to an existing row."""
        for key, value in data.items():
            if value is not None:
                setattr(break_session, key, value)
        self.db.commit()
        self.db.refresh(break_session)
        return break_session
