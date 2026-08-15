"""session.py — ShortsCap backend: study session repository.

StudySessionRepository — database operations ONLY (no business rules).
Ownership checks, state transitions, duration calculation and event
creation live in the service layer.
"""

from datetime import datetime

from sqlalchemy.orm import Session

from app.models.study_session import StudySession


class StudySessionRepository:
    """Data access for the `study_sessions` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, session_id: int) -> StudySession | None:
        """Return a session by id, or None if it does not exist."""
        return (
            self.db.query(StudySession)
            .filter(StudySession.id == session_id)
            .first()
        )

    def list_user_sessions(
        self,
        user_id: int,
        status: str | None = None,
        schedule_id: int | None = None,
        date_from: datetime | None = None,
        date_to: datetime | None = None,
    ) -> list[StudySession]:
        """Return a user's sessions, newest first, optionally filtered."""
        query = self.db.query(StudySession).filter(StudySession.user_id == user_id)
        if status is not None:
            query = query.filter(StudySession.status == status)
        if schedule_id is not None:
            query = query.filter(StudySession.schedule_id == schedule_id)
        if date_from is not None:
            query = query.filter(StudySession.started_at >= date_from)
        if date_to is not None:
            query = query.filter(StudySession.started_at <= date_to)
        return query.order_by(StudySession.started_at.desc()).all()

    def create(self, user_id: int, data: dict) -> StudySession:
        """Insert a session row for the user."""
        session = StudySession(user_id=user_id, **data)
        self.db.add(session)
        self.db.commit()
        self.db.refresh(session)
        return session

    def update(self, session: StudySession, data: dict) -> StudySession:
        """Apply only the supplied, non-None values to an existing row."""
        for key, value in data.items():
            if value is not None:
                setattr(session, key, value)
        self.db.commit()
        self.db.refresh(session)
        return session
