"""web services — ShortsCap backend package.

Router -> Pydantic Schema -> Service -> Repository -> SQLAlchemy -> MySQL.
Services hold validation + business rules; repositories hold database access.
"""

from app.services.web.blocked_website import BlockedWebsiteService
from app.services.web.errors import (
    WebConflictError,
    WebError,
    WebNotFoundError,
    WebValidationError,
)
from app.services.web.event import WebsiteEventService

__all__ = [
    "BlockedWebsiteService",
    "WebsiteEventService",
    "WebError",
    "WebNotFoundError",
    "WebConflictError",
    "WebValidationError",
]
