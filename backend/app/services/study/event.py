"""event.py — ShortsCap backend: study event service.

StudyEventService — read-side operations for the study event history.
Events themselves are created by the session / break services via the
StudyEventRepository; this service only returns a user's own events.
"""

from datetime import datetime

from sqlalchemy.orm import Session

from app.models.study_event import StudyEvent
from app.repositories.study import StudyEventRepository


class StudyEventService:
    """Business operations for reading study events."""

    def __init__(self, db: Session) -> None:
        self.repository = StudyEventRepository(db)

    def list_events(
        self,
        user_id: int,
        event_type: str | None = None,
        study_session_id: int | None = None,
        date_from: datetime | None = None,
        date_to: datetime | None = None,
    ) -> list[StudyEvent]:
        """Return the user's study events, newest first, with optional
        filters (event_type, session, date range on event_time). Only the
        caller's own events are ever returned."""
        return self.repository.list_user_events(
            user_id,
            event_type=event_type,
            study_session_id=study_session_id,
            date_from=date_from,
            date_to=date_to,
        )
