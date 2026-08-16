"""limit_cycle.py — ShortsCap backend: 24-hour Shorts limit cycle service.

ShortsLimitCycleService — the authoritative backend rules for the 24-hour
Shorts enforcement window:

  * ACTIVATE — creates the cycle ONLY when no active cycle exists (an active
    cycle is returned unchanged; a second one is never silently created).
  * EXPIRY   — timestamp-driven (never a background timer): every read/write
    compares `now` against `cycle_expires_at` and marks EXPIRED when past.
  * COUNT    — reconciled FROM synchronized usage (never a second counter):
    after Android syncs usage summaries, `current_count` is recomputed as the
    sum of the user's `shorts_usage` rows whose usage_date falls inside the
    active window. Re-syncing the same day is idempotent (the usage layer
    upserts), so the cycle count can never double-increment for one event.
  * LIMIT    — `current_count >= limit_count` flips the cycle to
    LIMIT_REACHED (limit_reached=True) and persists; it is never reset by
    app restarts, leaving Shorts or HUD changes — only expiry clears it.
  * WARNING  — uses the existing `warning_count` setting (count-based); the
    flag is set once per cycle, never repeatedly re-triggered. The existing
    `warning_minutes` (time-based) setting is respected as-is by Android.
  * LIMIT CHANGE — changing the limit updates ONLY the threshold: the count
    and the 24-hour timer of the existing window are preserved.

No raw SQL here; all persistence goes through the repository. Ownership is
enforced by scoping every query to the caller's user_id.
"""

from datetime import datetime, timedelta

from sqlalchemy import func
from sqlalchemy.orm import Session

from app.models.device import Device
from app.models.shorts_limit_cycle import ShortsLimitCycle
from app.models.shorts_usage import ShortsUsage
from app.repositories.shorts import ShortsLimitCycleRepository
from app.services.settings import ShortsSettingsService
from app.services.shorts.errors import ShortsNotFoundError, ShortsValidationError
from app.utils.datetime import utcnow

CYCLE_DURATION = timedelta(hours=24)

STATUS_ACTIVE = "ACTIVE"
STATUS_LIMIT_REACHED = "LIMIT_REACHED"
STATUS_EXPIRED = "EXPIRED"
STATUS_DISABLED = "DISABLED"


class ShortsLimitCycleService:
    """Business operations for the 24-hour Shorts limit cycle."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = ShortsLimitCycleRepository(db)

    # ------------------------------------------------------------------
    # Ownership
    # ------------------------------------------------------------------

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
            raise ShortsNotFoundError("Device not found.")

    # ------------------------------------------------------------------
    # Expiry (timestamp-driven — no background timer)
    # ------------------------------------------------------------------

    def _mark_expired(self, cycle: ShortsLimitCycle, now: datetime) -> ShortsLimitCycle:
        """Transition an expired cycle to EXPIRED and release the active slot."""
        return self.repository.update(
            cycle,
            {
                "status": STATUS_EXPIRED,
                "is_active": None,
            },
        )

    def get_active(self, user_id: int, now: datetime | None = None) -> ShortsLimitCycle | None:
        """Return the user's current window, handling expiry inline.

        If the active cycle's expiry has passed, it is marked EXPIRED (and
        the active slot freed) before returning None — a new cycle is only
        ever created by an explicit activation.
        """
        cycle = self.repository.get_active(user_id)
        if cycle is None:
            return None
        now = now or utcnow()
        if now >= cycle.cycle_expires_at:
            self._mark_expired(cycle, now)
            return None  # the window is over — no active cycle remains
        return cycle

    # ------------------------------------------------------------------
    # Activation / disable
    # ------------------------------------------------------------------

    def activate(
        self,
        user_id: int,
        limit_count: int,
        device_id: int | None = None,
        now: datetime | None = None,
    ) -> ShortsLimitCycle:
        """Activate a 24-hour cycle with the given limit.

        If an active cycle already exists, it is returned UNCHANGED (no
        second cycle is ever silently created — approved conflict behavior).
        The cycle persists immediately; the count starts at zero and the
        window is [now, now + 24h].
        """
        if limit_count <= 0:
            raise ShortsValidationError("limit_count must be greater than zero.")
        self._validate_device_owner(user_id, device_id)
        now = now or utcnow()

        existing = self.repository.get_active(user_id)
        if existing is not None:
            if now >= existing.cycle_expires_at:
                self._mark_expired(existing, now)
            else:
                return existing

        return self.repository.create(
            user_id,
            limit_count=limit_count,
            cycle_started_at=now,
            cycle_expires_at=now + CYCLE_DURATION,
            device_id=device_id,
            current_count=0,
            status=STATUS_ACTIVE,
        )

    def disable(self, user_id: int) -> ShortsLimitCycle | None:
        """Disable Shorts control: the active cycle becomes DISABLED (a
        historical row; usage history is never deleted). Returns None when
        no active cycle exists."""
        cycle = self.repository.get_active(user_id)
        if cycle is None:
            return None
        now = utcnow()
        if now >= cycle.cycle_expires_at:
            self._mark_expired(cycle, now)
            return None
        return self.repository.update(
            cycle,
            {"status": STATUS_DISABLED, "is_active": None},
        )

    # ------------------------------------------------------------------
    # Count synchronization (reconciled from synchronized usage)
    # ------------------------------------------------------------------

    def reconcile_from_usage(self, user_id: int, now: datetime | None = None) -> ShortsLimitCycle | None:
        """Recompute the active cycle's count from the user's SYNCHRONIZED
        usage summaries and update status (warning / limit reached).

        `current_count` = SUM(shorts_usage.shorts_count) over every row whose
        usage_date falls inside the active window. Because the usage layer is
        idempotent per (user, device, platform, surface, day), re-syncing or
        retrying can never double-increment the cycle count. The backend
        never trusts a client-supplied cycle count — it derives the value
        from the same data the client already syncs.
        """
        cycle = self.repository.get_active(user_id)
        if cycle is None:
            return None
        now = now or utcnow()
        if now >= cycle.cycle_expires_at:
            self._mark_expired(cycle, now)
            return None

        total = (
            self.db.query(func.coalesce(func.sum(ShortsUsage.shorts_count), 0))
            .filter(
                ShortsUsage.user_id == user_id,
                ShortsUsage.usage_date >= cycle.cycle_started_at.date(),
            )
            .scalar()
            or 0
        )
        total = int(total)

        settings = ShortsSettingsService(self.db).get_settings(user_id)
        data: dict = {"current_count": total}

        if cycle.limit_count > 0 and total >= cycle.limit_count:
            data["status"] = STATUS_LIMIT_REACHED
            data["limit_reached"] = True
        else:
            # The count may have been edited below the limit again (e.g. a
            # limit raise) — reflect the current truth.
            if total < cycle.limit_count:
                data["status"] = STATUS_ACTIVE

        # Count-based warning from the existing setting — fired once per cycle.
        if (
            not cycle.warning_triggered
            and settings.warning_count is not None
            and settings.warning_count > 0
            and total >= settings.warning_count
        ):
            data["warning_triggered"] = True

        return self.repository.update(cycle, data)

    # ------------------------------------------------------------------
    # Limit change (threshold only — count + timer preserved)
    # ------------------------------------------------------------------

    def apply_limit_change(
        self,
        user_id: int,
        limit_count: int,
        now: datetime | None = None,
    ) -> ShortsLimitCycle | None:
        """Update the active cycle's threshold without resetting the count
        or the 24-hour timer (approved behavior: 130/200 -> 130/250, same
        window). Re-evaluates status against the new threshold. Returns None
        when no cycle is active (the setting is still persisted by the
        caller)."""
        cycle = self.repository.get_active(user_id)
        if cycle is None:
            return None
        now = now or utcnow()
        if now >= cycle.cycle_expires_at:
            self._mark_expired(cycle, now)
            return None
        # Only the threshold changes — count and window timestamps are kept.
        self.repository.update(cycle, {"limit_count": limit_count})
        # Re-evaluate status against the new threshold (count unchanged).
        return self.reconcile_from_usage(user_id, now)
