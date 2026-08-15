"""rank.py — ShortsCap backend: Rank / Leaderboard engine.

Implements the APPROVED Phase 15A specification exactly
(`backend/docs/rank_leaderboard_spec.md`):

  * the Score Engine (Phase 14B, via `batch_scores`) is the ONLY source of
    score values — this service never computes or copies score formulas
  * eligibility: opted-in AND enabled (`leaderboard_settings`) AND score
    status `sufficient_data` / `partial_data` (Phase 15A §3)
  * COMPETITION ranking: scores [100, 100, 99] -> ranks 1, 1, 3 (Phase 15A §4)
  * deterministic tie-break ordering (-score, -study, -consistency,
    user_id asc) — ties share a rank value but podium/pagination order is
    stable (Phase 15A §5)
  * winner = rank #1 of the same pass; top three = first three rows of the
    same ranked list (never a separate algorithm, Phase 15A §4/§10)
  * rank_change vs the previous equivalent period, null when unavailable
    (Phase 15A §6)
  * DYNAMIC board: `leaderboard_scores` is NOT written; no caching
    (Phase 15A §9)

Only approved public fields are exposed per entry (rank, display_name,
score, user_id) — never email/phone/private profile data (Phase 15A §8).
"""

import math
from datetime import date, timedelta

from sqlalchemy.orm import Session

from app.repositories.rank import RankRepository
from app.services.scoring.batch import batch_scores
from app.services.scoring.score_service import PERIOD_BUILDERS
from app.utils.datetime import utcnow

# Score statuses that are eligible for the leaderboard (Phase 15A §3).
_ELIGIBLE_STATUSES = ("sufficient_data", "partial_data")


class RankService:
    """Builds the weekly / monthly leaderboard for the current user."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = RankRepository(db)

    # ------------------------------------------------------------------
    # Public entry point
    # ------------------------------------------------------------------

    def leaderboard(
        self,
        period_type: str,
        current_user_id: int,
        report_date: date | None = None,
        page: int = 1,
        page_size: int = 20,
    ) -> dict:
        if report_date is None:
            report_date = utcnow().date()
        period = PERIOD_BUILDERS[period_type](report_date)

        eligible_ids = self.repository.eligible_user_ids()
        scores = batch_scores(self.db, eligible_ids, period_type, report_date)
        ranked = self._rank(scores)

        # Current user (identifiable even when outside the visible page).
        your = self._your_entry(ranked, current_user_id, eligible_ids, scores)

        # Winner + top three from the SAME ranked pass (Phase 15A §10).
        winner = ranked[0] if ranked else None
        top_three = ranked[:3]

        # Pagination over the full ranked list with global ranks.
        total = len(ranked)
        start = (page - 1) * page_size
        page_entries = ranked[start : start + page_size]

        names = self.repository.display_names(
            [e["user_id"] for e in ranked] + ([current_user_id] if your else [])
        )

        return {
            "period": period,
            "your_rank": your["rank"] if your else None,
            "your_score": your["score"] if your else None,
            "your_score_status": your["status"] if your else None,
            "rank_change": (
                self._rank_change(period_type, report_date, current_user_id, your)
                if your
                else None
            ),
            "total_participants": total,
            "winner": self._entry_view(winner, names) if winner else None,
            "top_three": [self._entry_view(e, names) for e in top_three],
            "entries": [self._entry_view(e, names) for e in page_entries],
            "pagination": {
                "page": page,
                "page_size": page_size,
                "total_pages": math.ceil(total / page_size) if total else 0,
            },
        }

    # ------------------------------------------------------------------
    # Ranking (Phase 15A §4 / §5)
    # ------------------------------------------------------------------

    @staticmethod
    def _rank(scores: dict[int, dict]) -> list[dict]:
        """Competition ranking of the eligible scores.

        Order is deterministic: (-score, -study points, -consistency points,
        user_id asc). Rank values come from score alone: equal scores share a
        rank and the next rank skips (1, 1, 3).
        """
        eligible = [
            s for s in scores.values() if s["status"] in _ELIGIBLE_STATUSES
        ]
        points = {
            s["user_id"]: {c["name"]: c["points"] for c in s["components"]}
            for s in eligible
        }
        eligible.sort(
            key=lambda s: (
                -s["score"],
                -points[s["user_id"]]["study"],
                -points[s["user_id"]]["consistency"],
                s["user_id"],
            )
        )

        ranked: list[dict] = []
        previous_score: int | None = None
        for index, s in enumerate(eligible, start=1):
            if previous_score is not None and s["score"] == previous_score:
                rank = ranked[-1]["rank"]  # shared rank (competition)
            else:
                rank = index
            ranked.append(
                {
                    "rank": rank,
                    "user_id": s["user_id"],
                    "score": s["score"],
                    "status": s["status"],
                }
            )
            previous_score = s["score"]
        return ranked

    # ------------------------------------------------------------------
    # Current user (Phase 15A §7)
    # ------------------------------------------------------------------

    def _your_entry(
        self,
        ranked: list[dict],
        current_user_id: int,
        eligible_ids: list[int],
        scores: dict[int, dict],
    ) -> dict | None:
        """The current user's rank/score/status, or None when not eligible."""
        for entry in ranked:
            if entry["user_id"] == current_user_id:
                return entry
        # Not on the board: explain why (Phase 15A §7).
        if current_user_id in eligible_ids:
            status = scores.get(current_user_id, {}).get("status", "insufficient_data")
            return {"rank": None, "score": None, "status": status}
        return {"rank": None, "score": None, "status": "not_opted_in"}

    # ------------------------------------------------------------------
    # Rank change (Phase 15A §6)
    # ------------------------------------------------------------------

    def _rank_change(
        self,
        period_type: str,
        report_date: date,
        current_user_id: int,
        your: dict,
    ) -> int | None:
        """Previous-period rank minus current rank (positive = improved).
        Null when the previous period has no leaderboard data or the user
        was not eligible then (never an invented value)."""
        if your["rank"] is None:
            return None
        prev_date = self._previous_period_date(period_type, report_date)
        prev_scores = batch_scores(
            self.db, self.repository.eligible_user_ids(), period_type, prev_date
        )
        prev_ranked = self._rank(prev_scores)
        previous_rank = next(
            (e["rank"] for e in prev_ranked if e["user_id"] == current_user_id), None
        )
        if previous_rank is None:
            return None
        return previous_rank - your["rank"]

    @staticmethod
    def _previous_period_date(period_type: str, report_date: date) -> date:
        """A date inside the previous equivalent period (previous ISO week /
        previous calendar month), so PERIOD_BUILDERS resolves the correct
        boundaries — identical period interpretation to Score/Reports."""
        if period_type == "weekly":
            return report_date - timedelta(days=7)
        # monthly: any date in the previous calendar month
        first_of_month = report_date.replace(day=1)
        return first_of_month - timedelta(days=1)

    # ------------------------------------------------------------------
    # Privacy-safe views (Phase 15A §8)
    # ------------------------------------------------------------------

    @staticmethod
    def _entry_view(entry: dict, names: dict[int, str | None]) -> dict:
        """Only approved public fields; deterministic display-name fallback."""
        display_name = names.get(entry["user_id"])
        return {
            "rank": entry["rank"],
            "display_name": display_name if display_name else f"User {entry['user_id']}",
            "score": entry["score"],
            "user_id": entry["user_id"],
        }
