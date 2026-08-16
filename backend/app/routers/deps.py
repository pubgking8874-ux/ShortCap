"""deps.py — ShortsCap backend: shared FastAPI dependencies.

TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
AWS Cognito is implemented in a later phase. Until then every API identifies
the development user from the `X-Dev-User-Id` header. This is DEVELOPMENT
ONLY — it is not a security mechanism, grants no privileges, and must be
removed when real authentication lands.

This module is the single Cognito replacement point for the whole backend:
swap `get_dev_user_id` for real auth later without touching any endpoint.
"""

from fastapi import Header, HTTPException, status
from sqlalchemy.orm import Session

from app.config import settings
from app.models.user import User

# TEMPORARY DEVELOPMENT ONLY — see module docstring.
DEV_USER_ID_HEADER = "X-Dev-User-Id"


def _require_dev_identity() -> None:
    """TEMPORARY DEVELOPMENT ONLY — fail closed when the development
    identity is disabled (production configuration). This is the guard that
    stops X-Dev-User-Id from becoming an authentication bypass in a deployed
    environment; real auth (Cognito) replaces the whole dependency later.
    """
    if not settings.dev_identity_enabled:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Development identity is disabled in this environment.",
        )


def ensure_dev_user(db: Session, user_id: int) -> None:
    """TEMPORARY DEVELOPMENT ONLY — ensure a minimal `users` row exists so
    child-table foreign keys (user_settings.user_id, study_schedules.user_id,
    study_sessions.user_id, … -> users.id) can be satisfied while there is no
    real authentication yet.

    Delete this together with the X-Dev-User-Id header when Cognito is
    integrated — production users will already exist before any API call.
    """
    _require_dev_identity()
    exists = db.query(User.id).filter(User.id == user_id).first() is not None
    if not exists:
        db.add(User(id=user_id))  # all user columns except id are nullable
        db.commit()


def get_dev_user_id(
    x_dev_user_id: str | None = Header(default=None, alias=DEV_USER_ID_HEADER),
) -> int:
    """Resolve the temporary development user ID from the request header.

    Fails closed (403) when the development identity is disabled — see
    [DEV_IDENTITY_ENABLED] in `app/config.py`. Raises 400 for a missing /
    malformed ID. This is a clearly marked development-only seam: swap this
    dependency for real auth (Cognito) later without touching the endpoints.
    """
    _require_dev_identity()
    if x_dev_user_id is None or not x_dev_user_id.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                f"Missing {DEV_USER_ID_HEADER} header. This is a TEMPORARY "
                "development-only user identity — real authentication "
                "(Cognito) replaces it in a later phase."
            ),
        )
    try:
        user_id = int(x_dev_user_id.strip())
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"{DEV_USER_ID_HEADER} must be an integer (temporary dev identity).",
        )
    if user_id <= 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"{DEV_USER_ID_HEADER} must be a positive integer (temporary dev identity).",
        )
    return user_id
