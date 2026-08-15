"""settings.py — ShortsCap backend: FastAPI routes for user settings.

GET /settings  -> current settings (creates app defaults on first use)
PUT /settings  -> partial update (only supplied values are changed)

TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
AWS Cognito is implemented in a later phase. Until then the API reads the
development user ID from the `X-Dev-User-Id` header (see
`app/routers/deps.py` — the single Cognito replacement point). This is
DEVELOPMENT ONLY — it is not a security mechanism, grants no privileges,
and must be removed when real authentication lands.
"""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.routers.deps import ensure_dev_user, get_dev_user_id
from app.schemas.settings import (
    LeaderboardSettingResponse,
    LeaderboardSettingUpdate,
    MonitoringSettingsResponse,
    MonitoringSettingsUpdate,
    NotificationPreferenceResponse,
    NotificationPreferenceUpdate,
    PermissionStateResponse,
    PermissionStateUpdate,
    ShortsSettingsResponse,
    ShortsSettingsUpdate,
    UserSettingsResponse,
    UserSettingsUpdate,
)
from app.services.settings import (
    LeaderboardSettingsService,
    MonitoringSettingsService,
    NotificationPreferenceService,
    PermissionStateService,
    ShortsSettingsService,
    UserSettingsService,
)

router = APIRouter(prefix="/settings", tags=["settings"])

# TEMPORARY DEVELOPMENT ONLY — see app/routers/deps.py.

@router.get("", response_model=UserSettingsResponse, summary="Get current settings")
def get_settings(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> UserSettingsResponse:
    """Return the user's current settings; create app defaults if none exist."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    service = UserSettingsService(db)
    settings = service.get_settings(user_id)
    return UserSettingsResponse.model_validate(settings)


@router.put("", response_model=UserSettingsResponse, summary="Update settings")
def update_settings(
    payload: UserSettingsUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> UserSettingsResponse:
    """Partial update: only the supplied fields change, unspecified fields
    are preserved. Invalid values are rejected with 422 by the schema."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    service = UserSettingsService(db)
    settings = service.update_settings(user_id, payload.model_dump(exclude_unset=True))
    return UserSettingsResponse.model_validate(settings)


# ---------------------------------------------------------------------------
# Monitoring settings
# ---------------------------------------------------------------------------


@router.get("/monitoring", response_model=MonitoringSettingsResponse, summary="Get monitoring settings")
def get_monitoring_settings(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> MonitoringSettingsResponse:
    """Return the user's monitoring settings (creates defaults on first use)."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    settings = MonitoringSettingsService(db).get_settings(user_id)
    return MonitoringSettingsResponse.model_validate(settings)


@router.put("/monitoring", response_model=MonitoringSettingsResponse, summary="Update monitoring settings")
def update_monitoring_settings(
    payload: MonitoringSettingsUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> MonitoringSettingsResponse:
    """Partial update of monitoring settings; unspecified fields preserved."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    settings = MonitoringSettingsService(db).update_settings(
        user_id, payload.model_dump(exclude_unset=True)
    )
    return MonitoringSettingsResponse.model_validate(settings)


# ---------------------------------------------------------------------------
# Shorts settings
# ---------------------------------------------------------------------------


@router.get("/shorts", response_model=ShortsSettingsResponse, summary="Get Shorts settings")
def get_shorts_settings(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortsSettingsResponse:
    """Return the user's Shorts settings (creates defaults on first use)."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    settings = ShortsSettingsService(db).get_settings(user_id)
    return ShortsSettingsResponse.model_validate(settings)


@router.put("/shorts", response_model=ShortsSettingsResponse, summary="Update Shorts settings")
def update_shorts_settings(
    payload: ShortsSettingsUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortsSettingsResponse:
    """Partial update of Shorts settings; unspecified fields preserved."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    settings = ShortsSettingsService(db).update_settings(
        user_id, payload.model_dump(exclude_unset=True)
    )
    return ShortsSettingsResponse.model_validate(settings)


# ---------------------------------------------------------------------------
# Notification preferences
# ---------------------------------------------------------------------------


@router.get("/notifications", response_model=NotificationPreferenceResponse, summary="Get notification preferences")
def get_notification_preferences(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> NotificationPreferenceResponse:
    """Return the user's notification preferences (defaults on first use)."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    prefs = NotificationPreferenceService(db).get_preferences(user_id)
    return NotificationPreferenceResponse.model_validate(prefs)


@router.put("/notifications", response_model=NotificationPreferenceResponse, summary="Update notification preferences")
def update_notification_preferences(
    payload: NotificationPreferenceUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> NotificationPreferenceResponse:
    """Partial update of notification preferences; unspecified fields preserved."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    prefs = NotificationPreferenceService(db).update_preferences(
        user_id, payload.model_dump(exclude_unset=True)
    )
    return NotificationPreferenceResponse.model_validate(prefs)


# ---------------------------------------------------------------------------
# Leaderboard settings
# ---------------------------------------------------------------------------


@router.get("/leaderboard", response_model=LeaderboardSettingResponse, summary="Get leaderboard settings")
def get_leaderboard_settings(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> LeaderboardSettingResponse:
    """Return the user's leaderboard participation settings (defaults on first use)."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    setting = LeaderboardSettingsService(db).get_settings(user_id)
    return LeaderboardSettingResponse.model_validate(setting)


@router.put("/leaderboard", response_model=LeaderboardSettingResponse, summary="Update leaderboard settings")
def update_leaderboard_settings(
    payload: LeaderboardSettingUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> LeaderboardSettingResponse:
    """Partial update of leaderboard participation settings; no ranking logic."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    setting = LeaderboardSettingsService(db).update_settings(
        user_id, payload.model_dump(exclude_unset=True)
    )
    return LeaderboardSettingResponse.model_validate(setting)


# ---------------------------------------------------------------------------
# Permission states (last-known sync mirror only — Android is the authority)
# ---------------------------------------------------------------------------


@router.get("/permissions", response_model=list[PermissionStateResponse], summary="Get permission states")
def get_permission_states(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[PermissionStateResponse]:
    """Return the synced permission states for the user (may be empty)."""
    states = PermissionStateService(db).get_states(user_id)
    return [PermissionStateResponse.model_validate(s) for s in states]


@router.put("/permissions", response_model=list[PermissionStateResponse], summary="Sync permission states")
def update_permission_states(
    payload: list[PermissionStateUpdate],
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[PermissionStateResponse]:
    """Upsert the supplied permission states, then return the full list.

    This is a sync mirror only — the Android system remains the real source
    of truth for permission state.
    """
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    states = PermissionStateService(db).update_states(
        user_id, [item.model_dump(exclude_unset=True) for item in payload]
    )
    return [PermissionStateResponse.model_validate(s) for s in states]
