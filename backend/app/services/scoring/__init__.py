"""scoring — ShortsCap backend: Your Score engine package.

API -> ScoreService -> pure component modules -> ScoringQueries -> SQLAlchemy
-> MySQL. The component modules are pure and deterministic (no database
access); ScoringQueries is the read-only SQL aggregation layer; ScoreService
owns period math, the inactivity gate, coverage scaling, weighted assembly
and explanation rules. Uses the EXACT weights/constants approved in Phase 14A.

The batch scoring layer (`app/services/scoring/batch.py`) consumes the same
module-level helpers (`PERIOD_BUILDERS`, `component_values`, `assemble_score`)
so the leaderboard engine (Phase 15B) produces scores identical to the
single-user API — the Score Engine is the single source of truth.
"""

from app.services.scoring.batch import batch_scores
from app.services.scoring.consistency_score import consistency_value
from app.services.scoring.distraction_score import distraction_value
from app.services.scoring.score_service import (
    PERIOD_BUILDERS,
    COMPONENT_ORDER,
    ScoreService,
    assemble_score,
    component_values,
    explain,
    insufficient_response,
)
from app.services.scoring.shorts_score import shorts_value
from app.services.scoring.study_score import study_value
from app.services.scoring.web_score import web_value

__all__ = [
    "ScoreService",
    "PERIOD_BUILDERS",
    "COMPONENT_ORDER",
    "assemble_score",
    "component_values",
    "explain",
    "insufficient_response",
    "batch_scores",
    "study_value",
    "shorts_value",
    "distraction_value",
    "web_value",
    "consistency_value",
]
