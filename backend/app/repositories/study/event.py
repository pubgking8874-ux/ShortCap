"""event.py — ShortsCap backend: study event repository.

StudyEventRepository — database operations ONLY (no business rules). Which
events to create, and when, is decided by the service layer.
"""

from datetime import datetime

from sqlalchemy.orm import Session

from app.models.study_event import StudyEvent


class StudyEventRepository:
    """Data access for the `study_events` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def create(
        self,
        user_id: int,
        event_type: str,
        event_time: datetime,
        study_session_id: int | None = None,
        break_session_id: int | None = None,
        metadata_json: dict | None = None,
    ) -> StudyEvent:
        """Insert one study event row."""
        event = StudyEvent(
            user_id=user_id,
            event_type=event_type,
            event_time=event_time,
            study_session_id=study_session_id,
            break_session_id=break_session_id,
            metadata_json=metadata_json,
        )
        self.db.add(event)
        self.db.commit()
        self.db.refresh(event)
        return event

    def list_user_events(
        self,
        user_id: int,
        event_type: str | None = None,
        study_session_id: int | None = None,
        date_from: datetime | None = None,
        date_to: datetime | None = None,
    ) -> list[StudyEvent]:
        """Return a user's study events, newest first, optionally filtered."""
        query = self.db.query(StudyEvent).filter(StudyEvent.user_id == user_id)
        if event_type is not None:
            query = query.filter(StudyEvent.event_type == event_type)
        if study_session_id is not None:
            query = query.filter(StudyEvent.study_session_id == study_session_id)
        if date_from is not None:
            query = query.filter(StudyEvent.event_time >= date_from)
        if date_to is not None:
            query = query.filter(StudyEvent.event_time <= date_to)
        return query.order_by(StudyEvent.event_time.desc()).all()
