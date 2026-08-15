# Rank & Leaderboard Engine — Specification (Phase 15A)

**Status: SPECIFICATION AND VALIDATION ONLY. The production Rank engine is
NOT implemented and NOT deployed.** This document is the reviewed design for
a later implementation phase. Nothing here modifies production code, the
database, the approved score formula, or Android.

---

## 1. Source of truth

The **approved Your Score engine (Phase 14B) is the ONLY source of score
values.** The leaderboard NEVER calculates its own score:

```
Score Engine  →  score(user, period)  →  Leaderboard Engine  →  rank users
```

Scoring formulas are never duplicated. The leaderboard consumes
`ScoreService.score(user_id, period_type, date)` — the same read-only engine
behind `GET /score/*`.

## 2. Periods

Identical to the Score Engine and Reporting layer (naive-UTC convention):

- **Weekly** — the ISO calendar week (Monday–Sunday) containing the requested
  date (default: the server's current UTC date).
- **Monthly** — the calendar month containing the requested date.

No separate timezone or period interpretation is ever introduced.

## 3. Eligibility

A user appears on the leaderboard ONLY when ALL of the following hold for
the requested period:

1. **Opt-in:** a `leaderboard_settings` row exists with
   `is_opted_in = True` AND `is_enabled = True`. (`is_opted_in` defaults to
   `False`, so nobody is on the leaderboard accidentally.) Users who have not
   opted in — including users with no settings row — are invisible.
2. **Score status:** the user's period score status is `sufficient_data`
   OR `partial_data` (some recorded activity).

Eligibility matrix (by Score Engine status):

| Score status | Eligible? | Notes |
| --- | --- | --- |
| `sufficient_data` | ✅ yes | normal |
| `partial_data` | ✅ yes | coverage-scaled score is already honest |
| `insufficient_data` | ❌ excluded | score 0 / no activity — never ranked |
| not opted in (or no settings row) | ❌ excluded | invisible, never ranked |

A user with missing metrics can never receive a perfect ranking: if there is
no activity at all the score is 0 with `insufficient_data` and the user is
excluded; if there is some activity the score is coverage-scaled and ranks
honestly.

## 4. Ranking method — COMPETITION ranking

Given scores `[100, 100, 99]`, the ranks are **1, 1, 3** (competition /
"1224" ranking): equal scores share a rank and the next rank skips ahead.

- This is the behavior users expect from a Rank screen ("two people tied for
  first, you are third").
- The rank is derived from the score value only.
- **Top 3 / winner** are produced by the SAME ranking pass — there is never a
  separate podium algorithm. Winner = rank #1 of the period (null when there
  are no eligible users).

## 5. Deterministic tie-breaking (ordering)

The rank value itself is determined by score alone (ties share a rank), but
the *ordering* of tied users (podium slots and pagination order) must be
fully deterministic. Exact tie-breaker sequence:

1. `score` descending (primary — this IS the rank).
2. `study` component points descending (rewards productive behavior).
3. `consistency` component points descending (rewards regularity).
4. `user_id` ascending — final stable ordering (never depends on database
   retrieval randomness).

The full ordering key is therefore
`(-score, -study_points, -consistency_points, user_id)`. Sorting is applied
once; pagination slices that ordered list. Running the same data twice
always yields the same order and the same ranks.

## 6. Rank change

`rank_change = previous_period_rank - current_period_rank` (positive =
improved, negative = declined).

- Computed by evaluating the previous equivalent period (previous ISO week /
  previous calendar month) with the SAME eligibility rules.
- **If the user was not eligible / not present on the previous period's
  leaderboard** (including `insufficient_data` or no participation), or the
  previous period has no leaderboard data at all → `rank_change = null`
  (an explicit "cannot determine" state — never an invented value).
- If the current user is not eligible for the current period →
  `rank_change = null`.

## 7. Current user

The response always identifies the current user:

- `your_rank`, `your_score`, `your_score_status` — present when the current
  user is eligible for the period.
- When NOT eligible (opted out, or `insufficient_data`), `your_rank` and
  `your_score` are `null` and `your_score_status` explains why. The user is
  identifiable even when far outside the visible top-N (their rank is
  computed across ALL eligible users, not just the returned page).

## 8. Display fields & privacy

The leaderboard exposes ONLY approved public information per entry:

- `rank`
- `display_name` (from `leaderboard_settings.display_name`; when empty or
  null → deterministic fallback `"User {user_id}"`)
- `score`
- `user_id` — the opaque public identity used to link the current user's row
  (not email, phone, or any private profile field)

**Never exposed:** email, phone, name, gender, profile image URL, device
info, or any other private field.

## 9. Dynamic vs snapshot

**First implementation is DYNAMIC:** the leaderboard is calculated on demand
by running the Score Engine for every eligible user of the period, then
ranking. **`leaderboard_scores` is NOT written** — the existing table
(id, user_id, period_type, period_start, period_end, score, timestamps) is
sufficient for a future snapshot layer, and it has deliberately NO `rank`
column (rank is always derived from score ordering).

Snapshotting (writing period scores to `leaderboard_scores`) is deferred
until a measured performance requirement justifies it. No caching is added
in the first implementation.

## 10. Performance design

The first implementation cost is N eligible users × one period score
(≈ 8 grouped SQL queries per user via the Score Engine's ScoringQueries).
This is acceptable at current scale. Documented scale-out paths (future):
snapshot scores into `leaderboard_scores` once per period and rank from
that table; only then consider caching.

## 11. API contract (proposed)

Two endpoints, one shared handler (path-based periods, matching the Reports
convention):

```
GET /rank/weekly?date=YYYY-MM-DD&page=1&page_size=20
GET /rank/monthly?date=YYYY-MM-DD&page=1&page_size=20
```

Proposed response (final field names follow existing API conventions):

```json
{
  "period": {"type": "weekly", "start_date": "2026-08-10", "end_date": "2026-08-16", "label": "2026-08-10 – 2026-08-16"},
  "your_rank": 12,
  "your_score": 86,
  "your_score_status": "sufficient_data",
  "rank_change": 3,
  "total_participants": 47,
  "winner": {"rank": 1, "display_name": "FocusNinja", "score": 95, "user_id": 7},
  "top_three": [
    {"rank": 1, "display_name": "FocusNinja", "score": 95, "user_id": 7},
    {"rank": 2, "display_name": "StudyBuddy", "score": 91, "user_id": 12},
    {"rank": 3, "display_name": "User 3", "score": 89, "user_id": 3}
  ],
  "entries": [
    {"rank": 11, "display_name": "...", "score": 87, "user_id": 41},
    {"rank": 12, "display_name": "Me", "score": 86, "user_id": 99}
  ],
  "pagination": {"page": 1, "page_size": 20, "total_pages": 3}
}
```

- `entries` = the requested page of the FULL ranked list (rank 1 first).
- `top_three` = first three rows of the SAME ranked list (the same query,
  not a separate algorithm).
- `your_*` fields are `null` when the current user is not eligible.
- This maps directly to the future Android Rank screen: Your Rank / Your
  Score / Rank Change, Top-3 podium, full leaderboard, This Week / This Month.

## 12. Validation / simulation results

`scripts/rank_spec_simulation.py` implements the ranking + eligibility logic
independently and verifies the required cases:

| Case | Setup | Verified result |
| --- | --- | --- |
| A — 10 eligible users, unique scores | 10 opt-ins, scores 50..95 | ranks 1..10, deterministic order |
| B — multiple tied scores | 100, 100, 99 | competition ranks 1, 1, 3 |
| C — current user outside top 10 | user rank 12, page size 10 | your_rank = 12 even though not on page 1; winner/top_three correct |
| D — user opted out | opted-in user removed from eligibility | excluded from entries/ranks; not counted in total |
| E — user with insufficient data | score status `insufficient_data` | excluded (never ranked at 0 / never given a high rank) |
| F — current rank improves | prev week rank 8 → this week rank 3 | rank_change = +5 |
| G — current rank decreases | prev week rank 2 → this week rank 6 | rank_change = −4 |
| H — previous period has no leaderboard data | no eligible users last week | rank_change = null (not invented) |

Determinism: every case is computed twice; the ordered entries and ranks are
byte-identical. Fairness: higher score ⇒ better or equal rank; ties share a
rank; the podium comes from the same pass.

## 13. Required backend changes (FUTURE engine phase — NOT this phase)

1. New `LeaderboardService` (read-only) that: resolves eligible users
   (opt-in + score status), runs the Score Engine per eligible user for the
   period, sorts by `(-score, -study, -consistency, user_id)`, assigns
   competition ranks, computes the current user's rank/change and assembles
   the response.
2. New router `GET /rank/weekly`, `GET /rank/monthly` (shared handler).
3. Optional later: snapshot scores into `leaderboard_scores` + a cache once
   performance demands it.

**No schema change is required.** `leaderboard_settings` already carries
opt-in/enable/display_name; `leaderboard_scores` is untouched in this phase.
**No production Rank code was written. No Android, AWS or Cognito changes.**
