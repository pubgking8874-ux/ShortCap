"""Database models — ShortsCap backend package.

Import models so SQLAlchemy registers all tables on the shared `Base`.
"""

from app.models.user import User

__all__ = ["User"]