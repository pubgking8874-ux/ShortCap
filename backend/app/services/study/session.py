"""session.py — ShortsCap backend: study session service.

StudySessionService — business-level session operations:

  * ownership validation (sessions/schedules/devices belong to the caller)
  * safe state transitions (active -> completed/cancelled; nothing else)
  * server-side duration calculation (ended_at - started_at, never the
    client-supplied duration)
  * STUDY_STARTED / STUDY_ENDED / STUDY_CANCELLED event creation

The backend is NOT a real-time timer: it only persists session state and
history. Android remains responsible for real-time timing and the UI.
No HTTP concepts live here — domain errors (app/services/study/errors.py)
are mapped to status codes by the router.
"""

from datetime import datetime

from sqlalchemy.orm import Session

from app.models.device import Device
from app.models.study_session import StudySession
from app.repositories.study import (
    BreakSessionRepository,
    StudyEventRepository,
    StudyScheduleRepository,
    StudySessionRepository,
)
from app.services.study.errors import StudyNotFoundError, StudyStateError
from app.utils.datetime import utcnow


class StudySessionService:
    """Business operations for study sessions."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = StudySessionRepository(db)
        self.schedule_repository = StudyScheduleRepository(db)
        self.break_repository = BreakSessionRepository(db)
        self.event_repository = StudyEventRepository(db)

    # -- helpers ---------------------------------------------------------

    def _get_owned_session(self, user_id: int, session_id: int) -> StudySession:
        """Return the session if it exists AND belongs to the user, else 404.

        Cross-user access is treated as not-found so other users' records are
        never revealed.
        """
        session = self.repository.get_by_id(session_id)
        if session is None or session.user_id != user_id:
            raise StudyNotFoundError("Study session not found.")
        return session

    def _validate_schedule_owner(self, user_id: int, schedule_id: int | None) -> None:
        """Reject schedule references that don't exist or aren't the user's."""
        if schedule_id is None:
            return
        schedule = self.schedule_repository.get_by_id(schedule_id)
        if schedule is None or schedule.user_id != user_id:
            raise StudyNotFoundError("Schedule not found.")

    def _validate_device_owner(self, user_id: int, device_id: int | None) -> None:
        """Reject device references that don't exist or aren't the user's."""
        if device_id is None:
            return
        device = (
            self.db.query(Device)
            .filter(Device.id == device_id, Device.user_id == user_id)
            .first()
        )
        if device is None:
            raise StudyNotFoundError("Device not found.")

    @staticmethod
    def _duration_seconds(started_at: datetime | None, ended_at: datetime) -> int | None:
        """Server-side duration: ended_at - started_at, clamped at zero."""
        if started_at is None:
            return None
        return max(0, int((ended_at - started_at).total_seconds()))

    # -- start / end / cancel -------------------------------------------

    def start_session(self, user_id: int, data: dict) -> StudySession:
        """Start a study session: create the session row (status active,
        started_at = server now) and a STUDY_STARTED event.

        Accepts an optional schedule_id and optional device_id; both must
        belong to the user. No background timer is started — the backend
        only persists state.
        """
        data = dict(data)
        schedule_id = data.get("schedule_id")
        device_id = data.get("device_id")
        self._validate_schedule_owner(user_id, schedule_id)
        self._validate_device_owner(user_id, device_id)

        now = utcnow()
        session = self.repository.create(
            user_id,
            {
                "schedule_id": schedule_id,
                "device_id": device_id,
                "planned_duration_seconds": data.get("planned_duration_seconds"),
                "started_at": now,
                "status": "active",
            },
        )
        metadata: dict | None = None
        if session.planned_duration_seconds is not None:
            metadata = {"planned_duration_seconds": session.planned_duration_seconds}
        self.event_repository.create(
            user_id,
            "STUDY_STARTED",
            now,
            study_session_id=session.id,
            metadata_json=metadata,
        )
        return session

    def end_session(
        self, user_id: int, session_id: int, cancelled: bool = False
    ) -> StudySession:
        """End (or cancel) an ACTIVE study session.

        Sets ended_at = server now, actual_duration_seconds from server
        timestamps, status = completed (or cancelled), and writes a
        STUDY_ENDED / STUDY_CANCELLED event. A session that is not active
        (already completed/cancelled) cannot be ended again.
        """
        session = self._get_owned_session(user_id, session_id)
        if session.status != "active":
            raise StudyStateError(
                f"Cannot end a {session.status} session — only active sessions can be ended."
            )

        now = utcnow()
        actual = self._duration_seconds(session.started_at, now)
        status = "cancelled" if cancelled else "completed"
        self.repository.update(
            session,
            {
                "ended_at": now,
                "actual_duration_seconds": actual,
                "status": status,
            },
        )
        metadata = {"actual_duration_seconds": actual} if actual is not None else None
        self.event_repository.create(
            user_id,
            "STUDY_CANCELLED" if cancelled else "STUDY_ENDED",
            now,
            study_session_id=session.id,
            metadata_json=metadata,
        )
        return session

    def cancel_session(self, user_id: int, session_id: int) -> StudySession:
        """Cancel an ACTIVE study session (status cancelled, STUDY_CANCELLED)."""
        return self.end_session(user_id, session_id, cancelled=True)

    # -- reads -----------------------------------------------------------

    def get_session(self, user_id: int, session_id: int) -> StudySession:
        """Return one of the user's sessions (404 for other users')."""
        return self._get_owned_session(user_id, session_id)

    def list_sessions(
        self,
        user_id: int,
        status: str | None = None,
        schedule_id: int | None = None,
        date_from: datetime | None = None,
        date_to: datetime | None = None,
    ) -> list[StudySession]:
        """Return the user's session history, newest first, with optional
        filters (status, schedule_id, date range on started_at)."""
        return self.repository.list_user_sessions(
            user_id,
            status=status,
            schedule_id=schedule_id,
            date_from=date_from,
            date_to=date_to,
        )
