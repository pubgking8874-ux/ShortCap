"""schedule.py — ShortsCap backend: study schedule repository.

StudyScheduleRepository — database operations ONLY (no business rules).
Ownership checks, validation and defaults live in the service layer.
"""

from sqlalchemy.orm import Session

from app.models.study_schedule import StudySchedule


class StudyScheduleRepository:
    """CRUD-style data access for the `study_schedules` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, schedule_id: int) -> StudySchedule | None:
        """Return a schedule by id, or None if it does not exist."""
        return (
            self.db.query(StudySchedule)
            .filter(StudySchedule.id == schedule_id)
            .first()
        )

    def list_user_schedules(self, user_id: int) -> list[StudySchedule]:
        """Return all of a user's schedules, oldest first."""
        return (
            self.db.query(StudySchedule)
            .filter(StudySchedule.user_id == user_id)
            .order_by(StudySchedule.id.asc())
            .all()
        )

    def create(self, user_id: int, data: dict) -> StudySchedule:
        """Insert a schedule row for the user."""
        schedule = StudySchedule(user_id=user_id, **data)
        self.db.add(schedule)
        self.db.commit()
        self.db.refresh(schedule)
        return schedule

    def update(self, schedule: StudySchedule, data: dict) -> StudySchedule:
        """Apply only the supplied, non-None values to an existing row."""
        for key, value in data.items():
            if value is not None:
                setattr(schedule, key, value)
        self.db.commit()
        self.db.refresh(schedule)
        return schedule

    def delete(self, schedule: StudySchedule) -> None:
        """Delete an existing schedule row."""
        self.db.delete(schedule)
        self.db.commit()
