"""break_session.py — ShortsCap backend: break session service.

BreakSessionService — business-level break operations:

  * the break's study session must exist, belong to the caller and be ACTIVE
  * no overlapping active breaks for the same study session
  * server-side duration calculation (ended_at - started_at)
  * BREAK_STARTED / BREAK_ENDED event creation

No HTTP concepts here — domain errors are mapped by the router.
"""

from sqlalchemy.orm import Session

from app.models.break_session import BreakSession
from app.repositories.study import (
    BreakSessionRepository,
    StudyEventRepository,
    StudySessionRepository,
)
from app.services.study.errors import StudyNotFoundError, StudyStateError
from app.utils.datetime import utcnow


class BreakSessionService:
    """Business operations for break sessions."""

    def __init__(self, db: Session) -> None:
        self.repository = BreakSessionRepository(db)
        self.session_repository = StudySessionRepository(db)
        self.event_repository = StudyEventRepository(db)

    def start_break(self, user_id: int, study_session_id: int) -> BreakSession:
        """Start a break inside an ACTIVE study session of the caller.

        Rejects a session that is not active (including completed sessions)
        and overlapping active breaks for the same session. Writes a
        BREAK_STARTED event.
        """
        session = self.session_repository.get_by_id(study_session_id)
        if session is None or session.user_id != user_id:
            raise StudyNotFoundError("Study session not found.")
        if session.status != "active":
            raise StudyStateError(
                f"Cannot start a break on a {session.status} study session — "
                "breaks are only allowed while the session is active."
            )
        if self.repository.get_active_break_for_session(study_session_id) is not None:
            raise StudyStateError(
                "A break is already active for this study session — end it before starting a new one."
            )

        now = utcnow()
        break_session = self.repository.create(
            {
                "study_session_id": study_session_id,
                "started_at": now,
                "status": "active",
            }
        )
        self.event_repository.create(
            user_id,
            "BREAK_STARTED",
            now,
            study_session_id=study_session_id,
            break_session_id=break_session.id,
        )
        return break_session

    def end_break(self, user_id: int, break_id: int) -> BreakSession:
        """End an ACTIVE break owned (via its study session) by the caller.

        Sets ended_at = server now, duration_seconds from server timestamps,
        status = completed, and writes a BREAK_ENDED event. A break that is
        not active (already completed) cannot be ended twice.
        """
        break_session = self.repository.get_by_id(break_id)
        if break_session is None:
            raise StudyNotFoundError("Break session not found.")
        session = self.session_repository.get_by_id(break_session.study_session_id)
        if session is None or session.user_id != user_id:
            raise StudyNotFoundError("Break session not found.")
        if break_session.status != "active":
            raise StudyStateError(
                f"Cannot end a {break_session.status} break — only active breaks can be ended."
            )

        now = utcnow()
        duration = None
        if break_session.started_at is not None:
            duration = max(0, int((now - break_session.started_at).total_seconds()))
        self.repository.update(
            break_session,
            {
                "ended_at": now,
                "duration_seconds": duration,
                "status": "completed",
            },
        )
        metadata = {"duration_seconds": duration} if duration is not None else None
        self.event_repository.create(
            user_id,
            "BREAK_ENDED",
            now,
            study_session_id=break_session.study_session_id,
            break_session_id=break_session.id,
            metadata_json=metadata,
        )
        return break_session

    def list_breaks_for_session(self, user_id: int, study_session_id: int) -> list[BreakSession]:
        """Return all breaks of one of the caller's study sessions."""
        session = self.session_repository.get_by_id(study_session_id)
        if session is None or session.user_id != user_id:
            raise StudyNotFoundError("Study session not found.")
        return self.repository.list_for_session(study_session_id)
