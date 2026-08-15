"""constants.py — ShortsCap backend: approved Your Score constants.

All values are the FINAL approved values from Phase 14A
(`backend/docs/your_score_spec.md`). They must not be changed without a new
spec review. The simulation script (`scripts/score_spec_simulation.py`)
mirrors these exactly.
"""

# Approved component weights (sum = 100).
WEIGHTS = {
    "study": 40,
    "shorts": 25,
    "distraction": 20,
    "web": 10,
    "consistency": 5,
}

# A study session is "meaningful" only when it ended with a terminal status
# and lasted at least this long (primary anti-gaming rule for study).
MIN_MEANINGFUL_SESSION_SEC = 300

# Study volume target per day-equivalent (linear-to-cap; 150/day was adopted
# after simulation because 120/day made the top of the range too easy).
STUDY_TARGET_MIN_PER_DAY = 150

# Fallback daily Shorts limit when the user has no configured limit.
SHORTS_DEFAULT_LIMIT_MIN = 30

# Distraction threshold: no penalty at or below 4 h/day of total phone time.
DISTRACTION_THRESHOLD_MIN = 240

# Inactivity gate: minimum active days per period before full credit.
ACTIVE_DAYS_REQUIRED = {"daily": 1, "weekly": 3, "monthly": 7}

# Consistency participation targets (active days per period).
CONSISTENCY_TARGET_DAYS = {"daily": 1, "weekly": 5, "monthly": 20}
