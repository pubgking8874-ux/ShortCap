"""control.py — ShortsCap backend: Shorts Control orchestration service.

ShortsControlService — assembles the combined Shorts Control response for
`GET /shorts/control` (and post-update `PUT /shorts/control`):

  * applications  — the canonical short-form platform catalog (configuration
                    options; runtime per-platform toggles remain Android-local
                    until the settings sync phase — no fabricated verification)
  * limit_cycle   — the current 24-hour window from ShortsLimitCycleService
                    (remaining seconds + usage ratio DERIVED at request time)
  * hud           — the persisted HUD appearance preference (shorts_settings)
  * insights      — read-only Yesterday / Today / This Week / This Month
                    summaries aggregated from REAL stored `shorts_usage` rows
                    (reusing the existing ReportingRepository — no new report
                    tables, no fabricated platforms)

The Android app remains the real-time enforcement authority. This service
only orchestrates synchronized configuration + state; it performs no
detection, no counting and no HTTP.
"""

from datetime import date, timedelta

from sqlalchemy.orm import Session

from app.models.shorts_usage import ShortsUsage
from app.repositories.reports import ReportingRepository
from app.services.settings import ShortsSettingsService
from app.services.shorts.limit_cycle import ShortsLimitCycleService
from app.utils.datetime import utcnow

# Canonical short-form platform catalog (Short Applications). `enabled` is the
# default configuration state only — these flags never claim real-device
# verification. IDs mirror the Android ShortVideoPlatform ids where they exist.
CANONICAL_SHORT_PLATFORMS: list[dict] = [
    {"id": "youtube_shorts", "name": "YouTube Shorts", "domain": "youtube.com", "enabled": True},
    {"id": "instagram_reels", "name": "Instagram Reels", "domain": "instagram.com", "enabled": True},
    {"id": "tiktok_short_feed", "name": "TikTok", "domain": "tiktok.com", "enabled": False},
    {"id": "snapchat_spotlight", "name": "Snapchat Spotlight", "domain": "snapchat.com", "enabled": False},
    {"id": "facebook_reels", "name": "Facebook Reels", "domain": "facebook.com", "enabled": False},
    {"id": "moj_short_video", "name": "Moj", "domain": "mojapp.in", "enabled": False},
    {"id": "x_short_video", "name": "X", "domain": "x.com", "enabled": False},
    {"id": "linkedin_short_video", "name": "LinkedIn", "domain": "linkedin.com", "enabled": False},
]


class ShortsControlService:
    """Orchestrates the combined Shorts Control state for one user."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.cycle_service = ShortsLimitCycleService(db)
        self.settings_service = ShortsSettingsService(db)
        self.reporting = ReportingRepository(db)

    # ------------------------------------------------------------------
    # Period helpers (same conventions as ReportingService: ISO week from
    # Monday, calendar month, all naive-UTC dates)
    # ------------------------------------------------------------------

    @staticmethod
    def _today() -> date:
        return utcnow().date()

    @classmethod
    def _weekly_period(cls, day: date) -> tuple[date, date]:
        start = day - timedelta(days=day.isoweekday() - 1)  # Monday
        return start, start + timedelta(days=6)  # Sunday

    @classmethod
    def _monthly_period(cls, day: date) -> tuple[date, date]:
        start = day.replace(day=1)
        next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
        return start, next_month - timedelta(days=1)

    # ------------------------------------------------------------------
    # Insights (read-only, aggregated from real shorts_usage rows)
    # ------------------------------------------------------------------

    @staticmethod
    def _period_summary(aggregates: dict) -> dict:
        return {
            "total_shorts_count": aggregates["total_shorts_count"],
            "total_duration_seconds": aggregates["total_duration_seconds"],
            "warning_count": aggregates["warning_count"],
            "limit_reached_count": aggregates["limit_reached_count"],
            "platform_breakdown": aggregates["platform_breakdown"],
        }

    def insights(self, user_id: int) -> dict:
        today = self._today()
        yesterday = today - timedelta(days=1)
        week_start, week_end = self._weekly_period(today)
        month_start, month_end = self._monthly_period(today)
        return {
            "yesterday": self._period_summary(
                self.reporting.shorts_aggregates(user_id, yesterday, yesterday)
            ),
            "today": self._period_summary(
                self.reporting.shorts_aggregates(user_id, today, today)
            ),
            "this_week": self._period_summary(
                self.reporting.shorts_aggregates(user_id, week_start, week_end)
            ),
            "this_month": self._period_summary(
                self.reporting.shorts_aggregates(user_id, month_start, month_end)
            ),
        }

    # ------------------------------------------------------------------
    # Limit cycle block (derived, never persisted as decreasing values)
    # ------------------------------------------------------------------

    def limit_cycle_block(self, user_id: int) -> dict | None:
        cycle = self.cycle_service.get_active(user_id)
        if cycle is None:
            return None
        now = utcnow()
        remaining = max(0, int((cycle.cycle_expires_at - now).total_seconds()))
        ratio = (
            round(cycle.current_count / cycle.limit_count, 3)
            if cycle.limit_count > 0
            else 0.0
        )
        return {
            "limit_count": cycle.limit_count,
            "current_count": cycle.current_count,
            "status": cycle.status,
            "cycle_started_at": cycle.cycle_started_at,
            "cycle_expires_at": cycle.cycle_expires_at,
            "remaining_seconds": remaining,
            "usage_ratio": ratio,
            "warning_triggered": cycle.warning_triggered,
            "limit_reached": cycle.limit_reached,
        }

    # ------------------------------------------------------------------
    # Assembly
    # ------------------------------------------------------------------

    def control(self, user_id: int) -> dict:
        """The combined Shorts Control state (GET /shorts/control)."""
        settings = self.settings_service.get_settings(user_id)
        return {
            "applications": {"platforms": list(CANONICAL_SHORT_PLATFORMS)},
            "limit_cycle": self.limit_cycle_block(user_id),
            "hud": {"appearance": settings.hud_appearance or "BRAIN"},
            "insights": self.insights(user_id),
        }

    def update_control(self, user_id: int, data: dict) -> dict:
        """Apply a partial Shorts Control update (PUT /shorts/control).

        Persists configuration through the existing Shorts settings service
        (limit -> daily_limit_count, warning, enable/disable, strict mode,
        HUD appearance). Changing the limit NEVER resets an active cycle's
        count or timer — only the threshold changes (apply_limit_change).
        Returns the refreshed control state.
        """
        settings_update: dict = {}
        if "limit_count" in data:
            settings_update["daily_limit_count"] = data["limit_count"]
        if "warning_count" in data:
            settings_update["warning_count"] = data["warning_count"]
        if "warning_minutes" in data:
            settings_update["warning_minutes"] = data["warning_minutes"]
        if "is_enabled" in data:
            settings_update["is_enabled"] = data["is_enabled"]
        if "strict_mode_enabled" in data:
            settings_update["strict_mode_enabled"] = data["strict_mode_enabled"]
        if "hud_appearance" in data:
            settings_update["hud_appearance"] = data["hud_appearance"]

        if settings_update:
            self.settings_service.update_settings(user_id, settings_update)

        # Limit change: update the active cycle's threshold only (count +
        # 24-hour timer preserved), then re-evaluate status.
        if data.get("limit_count") is not None:
            self.cycle_service.apply_limit_change(user_id, data["limit_count"])

        return self.control(user_id)


# Re-export for callers that need to reconcile the cycle after a usage sync.
def reconcile_cycle_after_usage_sync(db: Session, user_id: int) -> None:
    """After Android syncs usage summaries, reconcile the active cycle's
    count from the synchronized data (idempotent — see
    ShortsLimitCycleService.reconcile_from_usage)."""
    ShortsLimitCycleService(db).reconcile_from_usage(user_id)
