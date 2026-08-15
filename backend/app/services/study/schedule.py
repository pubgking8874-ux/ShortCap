"""schedule.py — ShortsCap backend: study schedule service.

StudyScheduleService — business-level schedule operations. It owns
ownership validation and the days_of_week list <-> comma-separated-string
conversion; all SQL lives in the repository. No HTTP concepts here.
"""

from sqlalchemy.orm import Session

from app.models.study_schedule import StudySchedule
from app.repositories.study import StudyScheduleRepository
from app.services.study.errors import StudyNotFoundError


class StudyScheduleService:
    """Business operations for study schedules."""

    def __init__(self, db: Session) -> None:
        self.repository = StudyScheduleRepository(db)

    def create_schedule(self, user_id: int, data: dict) -> StudySchedule:
        """Create a schedule for the user. The validated `days_of_week`
        list is stored as a comma-separated string."""
        data = dict(data)
        days = data.pop("days_of_week", None)
        if days is not None:
            data["days_of_week"] = ",".join(days)
        return self.repository.create(user_id, data)

    def list_schedules(self, user_id: int) -> list[StudySchedule]:
        """Return all of the user's schedules."""
        return self.repository.list_user_schedules(user_id)

    def get_schedule(self, user_id: int, schedule_id: int) -> StudySchedule:
        """Return one of the user's schedules.

        A schedule that does not exist OR belongs to another user raises
        StudyNotFoundError (404) — other users' records are never exposed.
        """
        schedule = self.repository.get_by_id(schedule_id)
        if schedule is None or schedule.user_id != user_id:
            raise StudyNotFoundError("Schedule not found.")
        return schedule

    def update_schedule(
        self, user_id: int, schedule_id: int, data: dict
    ) -> StudySchedule:
        """Partial update of one of the user's schedules. Only the supplied,
        non-None values are persisted."""
        schedule = self.get_schedule(user_id, schedule_id)
        data = dict(data)
        days = data.pop("days_of_week", None)
        if days is not None:
            data["days_of_week"] = ",".join(days)
        return self.repository.update(schedule, data)

    def delete_schedule(self, user_id: int, schedule_id: int) -> None:
        """Delete one of the user's schedules (404 for other users')."""
        schedule = self.get_schedule(user_id, schedule_id)
        self.repository.delete(schedule)
