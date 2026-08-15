"""score_service.py — ShortsCap backend: Your Score engine.

ScoreService — deterministic, read-only orchestration for the approved
Phase 14A score model:

  * period math (daily / ISO-week / calendar month — same conventions as the
    Phase 13 reports)
  * data gathering through ScoringQueries (SQL aggregation)
  * pure component calculations (study / shorts / distraction / web /
    consistency)
  * approved weights (40/25/20/10/5), approved inactivity gate and
    coverage scaling, final clamp to [0, 100]
  * deterministic explanation rules (no random text, no raw SQL exposed)

The score is computed on the CURRENT user's data only (the caller's id is
always the development identity from the request header). It is NEVER
written to the database and NO leaderboard/rank logic exists here.
"""

from datetime import date, timedelta

from sqlalchemy.orm import Session

from app.services.scoring.consistency_score import consistency_value
from app.services.scoring.constants import ACTIVE_DAYS_REQUIRED, WEIGHTS
from app.services.scoring.distraction_score import distraction_value
from app.services.scoring.queries import ScoringQueries
from app.services.scoring.shorts_score import shorts_value
from app.services.scoring.study_score import study_value
from app.services.scoring.web_score import web_value
from app.utils.datetime import utcnow


def _daily_period(report_date: date) -> dict:
    return {
        "type": "daily",
        "start_date": report_date,
        "end_date": report_date,
        "label": f"{report_date:%Y-%m-%d}",
    }


def _weekly_period(report_date: date) -> dict:
    start = report_date - timedelta(days=report_date.isoweekday() - 1)
    return {
        "type": "weekly",
        "start_date": start,
        "end_date": start + timedelta(days=6),
        "label": f"{start:%Y-%m-%d} – {(start + timedelta(days=6)):%Y-%m-%d}",
    }


def _monthly_period(report_date: date) -> dict:
    start = report_date.replace(day=1)
    next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
    end = next_month - timedelta(days=1)
    return {
        "type": "monthly",
        "start_date": start,
        "end_date": end,
        "label": f"{report_date:%Y-%m}",
    }


_PERIOD_BUILDERS = {
    "daily": _daily_period,
    "weekly": _weekly_period,
    "monthly": _monthly_period,
}

_COMPONENT_ORDER = ("study", "shorts", "distraction", "web", "consistency")


class ScoreService:
    """Computes the current user's Your Score for a period."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.queries = ScoringQueries(db)

    # ------------------------------------------------------------------
    # Public entry points
    # ------------------------------------------------------------------

    def score(
        self,
        user_id: int,
        period_type: str,
        report_date: date | None = None,
    ) -> dict:
        if report_date is None:
            report_date = utcnow().date()
        period = _PERIOD_BUILDERS[period_type](report_date)
        days = (period["end_date"] - period["start_date"]).days + 1

        collected = self._collect(user_id, period["start_date"], period["end_date"])
        active_days = collected["active_days"]

        if active_days == 0:
            return self._insufficient_response(period, period_type, days)

        values = self._component_values(collected, period_type, days)
        raw = sum(WEIGHTS[name] * value for name, (value, _) in values.items())

        required = ACTIVE_DAYS_REQUIRED[period_type]
        if active_days < required:
            coverage = active_days / required
            score = round(raw * coverage)
            status = "partial_data"
        else:
            coverage = 1.0
            score = round(raw)
            status = "sufficient_data"

        # Final normalization guard: the approved range is 0..100.
        score = max(0, min(100, int(score)))

        return {
            "period": period,
            "score": score,
            "status": status,
            "components": [
                {
                    "name": name,
                    "value": value,
                    "status": component_status,
                    "points": round(WEIGHTS[name] * value, 1),
                    "max": WEIGHTS[name],
                }
                for name, (value, component_status) in values.items()
            ],
            "activity": {
                "active_days": active_days,
                "required_days": required,
                "coverage": round(coverage, 4),
            },
            "explanation": self._explain(values, collected, period_type, status, score, coverage),
        }

    # ------------------------------------------------------------------
    # Data + component computation
    # ------------------------------------------------------------------

    def _collect(self, user_id: int, start: date, end: date) -> dict:
        """Gather every input the components need in grouped SQL queries."""
        return {
            "study": self.queries.study_aggregates(user_id, start, end),
            "shorts_days": self.queries.shorts_days(user_id, start, end),
            "shorts_limit": self.queries.shorts_limit_minutes(user_id),
            "app_days": self.queries.app_days(user_id, start, end),
            "enforcement_events": self.queries.enforcement_events(user_id, start, end),
            "blocked_active": self.queries.blocked_active_count(user_id),
            "web_events": self.queries.web_events(user_id, start, end),
            "active_days": self.queries.active_days(user_id, start, end),
        }

    def _component_values(self, collected: dict, period_type: str, days: int) -> dict:
        """Run the five pure components on the collected data."""
        return {
            "study": study_value(collected["study"], days),
            "shorts": shorts_value(
                {"days": collected["shorts_days"]}, collected["shorts_limit"]
            ),
            "distraction": distraction_value(
                {
                    "days": collected["app_days"],
                    "enforcement_events": collected["enforcement_events"],
                }
            ),
            "web": web_value(
                {
                    "blocked_active": collected["blocked_active"],
                    "events": collected["web_events"],
                }
            ),
            "consistency": (
                consistency_value(collected["active_days"], period_type),
                "evaluated",
            ),
        }

    def _insufficient_response(self, period: dict, period_type: str, days: int) -> dict:
        """Approved inactivity outcome: score 0 with `insufficient_data`
        status (never 100, never a fake neutral total)."""
        required = ACTIVE_DAYS_REQUIRED[period_type]
        return {
            "period": period,
            "score": 0,
            "status": "insufficient_data",
            "components": [
                {
                    "name": name,
                    "value": 0.5,
                    "status": "neutral",
                    "points": round(WEIGHTS[name] * 0.5, 1),
                    "max": WEIGHTS[name],
                }
                for name in _COMPONENT_ORDER
            ],
            "activity": {
                "active_days": 0,
                "required_days": required,
                "coverage": 0.0,
            },
            "explanation": {
                "summary": (
                    f"No activity was recorded in this period, so the score is 0 "
                    f"({days}-day {period_type} period). Do something productive — "
                    f"study, stay within your Shorts limit and keep phone time "
                    f"moderate — to earn a score."
                ),
                "positives": [],
                "negatives": ["No recorded activity in the period."],
            },
        }

    # ------------------------------------------------------------------
    # Deterministic explanations
    # ------------------------------------------------------------------

    @staticmethod
    def _explain(
        values: dict,
        collected: dict,
        period_type: str,
        status: str,
        score: int,
        coverage: float,
    ) -> dict:
        """Deterministic explanation rules — same inputs always produce the
        same text. Never exposes SQL or internals."""
        positives: list[str] = []
        negatives: list[str] = []

        study_value_, study_status = values["study"]
        if study_status == "evaluated":
            if study_value_ >= 0.85:
                positives.append("Study completion and volume contributed strongly to your score.")
            elif study_value_ >= 0.5:
                positives.append("Study performance was moderate.")
            else:
                negatives.append(
                    "Study performance was low — complete your study sessions and "
                    "work toward the daily target."
                )
        else:
            negatives.append("Not enough study data to score (treated neutrally).")

        shorts_value_, shorts_status = values["shorts"]
        if shorts_status == "evaluated":
            if shorts_value_ >= 0.9:
                positives.append("Shorts usage stayed within your configured daily limit.")
            elif shorts_value_ < 0.5:
                negatives.append("Shorts usage exceeded your configured daily limit.")
            else:
                negatives.append("Shorts usage was above your configured daily limit.")
        else:
            negatives.append("Not enough Shorts data to score (treated neutrally).")

        distraction_value_, distraction_status = values["distraction"]
        if distraction_status == "evaluated":
            if distraction_value_ >= 0.9:
                positives.append("Phone usage stayed moderate.")
            elif distraction_value_ < 0.5:
                negatives.append("Total phone usage was high — consider reducing daily screen time.")
            else:
                negatives.append("Phone usage was above the moderation threshold on some days.")
        else:
            negatives.append("Not enough app-usage data to score (treated neutrally).")

        web_value_, web_status = values["web"]
        if web_status == "evaluated":
            if web_value_ >= 0.95:
                positives.append("Good web discipline — blocked sites were respected.")
            else:
                negatives.append("Repeated blocked-site attempts or unblocks cost points.")
        else:
            negatives.append("Not enough web data to score (treated neutrally).")

        consistency_value_ = values["consistency"][0]
        if consistency_value_ >= 0.8:
            positives.append("Consistent participation across the period.")
        elif consistency_value_ < 0.5:
            negatives.append("Participation was irregular — more active days would help.")

        summary = f"Your Score: {score} ({status})."
        if status == "partial_data":
            summary += (
                f" Partial data — the score is scaled by coverage "
                f"{round(coverage, 2)} ({collected['active_days']} of "
                f"{ACTIVE_DAYS_REQUIRED[period_type]} required active days)."
            )
        return {
            "summary": summary,
            "positives": positives,
            "negatives": negatives,
        }
