"""Database models — ShortsCap backend package.

Importing this package registers every model on the shared `Base` metadata
(see `app.database`). Adding a model = one new file + one import here; no
other wiring is needed.
"""

from app.models.app_usage import AppUsage
from app.models.auth_identity import AuthIdentity
from app.models.blocked_website import BlockedWebsite
from app.models.break_session import BreakSession
from app.models.device import Device
from app.models.feedback import Feedback
from app.models.leaderboard_score import LeaderboardScore
from app.models.leaderboard_setting import LeaderboardSetting
from app.models.monitoring_event import MonitoringEvent
from app.models.monitoring_settings import MonitoringSettings
from app.models.notification_event import NotificationEvent
from app.models.notification_preference import NotificationPreference
from app.models.otp_verification import OtpVerification
from app.models.permission_state import PermissionState
from app.models.shorts_event import ShortsEvent
from app.models.shorts_limit_cycle import ShortsLimitCycle
from app.models.shorts_settings import ShortsSettings
from app.models.shorts_usage import ShortsUsage
from app.models.study_event import StudyEvent
from app.models.study_schedule import StudySchedule
from app.models.study_session import StudySession
from app.models.user import User
from app.models.user_profile import UserProfile
from app.models.user_settings import UserSettings
from app.models.website_event import WebsiteEvent

__all__ = [
    "AppUsage",
    "AuthIdentity",
    "BlockedWebsite",
    "BreakSession",
    "Device",
    "Feedback",
    "LeaderboardScore",
    "LeaderboardSetting",
    "MonitoringEvent",
    "MonitoringSettings",
    "NotificationEvent",
    "NotificationPreference",
    "OtpVerification",
    "PermissionState",
    "ShortsEvent",
    "ShortsLimitCycle",
    "ShortsSettings",
    "ShortsUsage",
    "StudyEvent",
    "StudySchedule",
    "StudySession",
    "User",
    "UserProfile",
    "UserSettings",
    "WebsiteEvent",
]
