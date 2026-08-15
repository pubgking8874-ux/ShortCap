# Your Score — Mathematical Specification (Phase 14A)

**Status: SPECIFICATION ONLY. The production score engine is NOT implemented
and NOT deployed.** This document is the reviewed design that a later phase
will implement. Nothing here modifies the database, Android, or the
leaderboard.

---

## 1. Objective

Your Score is a 0–100 number that summarizes a user's **productive
behavior** in a period: study performance, study consistency, Shorts
discipline, distracting-app control and web discipline.

Two hard principles:

1. The score must **reward productive behavior** — a user who studies
   effectively must be able to outperform a user who merely uses the phone
   very little. The score is NOT "less phone time = higher score".
2. **Inactivity must never produce a perfect score.** A user who does
   nothing receives 0 with an explicit `insufficient_data` status, never a
   high neutral value.

## 2. Score range and bands

Primary range **0–100** (integer, rounded). Candidate bands:

| Band | Range | Meaning |
| --- | --- | --- |
| Excellent | 90–100 | Outstanding productive period |
| Strong | 75–89 | Consistently good behavior |
| Moderate | 60–74 | Acceptable, room to improve |
| Needs improvement | 40–59 | Weak period |
| Poor | 0–39 | Very weak / insufficient activity |

**Band recommendation (after validation):** keep these bands for now, with
one documented caveat — because missing components contribute a neutral 0.5,
a partially-active user with no Shorts/Web data can land in "Moderate" even
with good study. Re-evaluate the band edges after real Android-synced data
exists (see §15.4). No change is recommended today; the neutral rule and the
inactivity gate already prevent the worst artifacts.

## 3. Candidate weights (validated, final for this phase)

| Component | Weight | Rationale |
| --- | --- | --- |
| Study performance | **40** | Core product purpose; largest share |
| Shorts discipline | **25** | Primary distraction surface; large share |
| Distraction control | **20** | App-usage moderation; see §7 limitation |
| Web discipline | **10** | Blocking behavior; smaller share |
| Consistency | **5** | Regularity bonus on top of performance |

Total = 100.

**Recommendation on weights:** the 40/25/20/10/5 candidate is kept after
simulation — it produces useful separation (see §15.2) and no component can
dominate or be trivially maxed. One **deferred recommendation**: when an
app-categorization phase lands (see §7), consider shifting 10 points from
"Distraction control" to "Study performance" (→ 50/25/10/10/5) because a
category-aware distraction metric will then be reliable enough to earn its
weight. Not applied today.

## 4. Notation

- Period P = [d_start, d_end]; days(P) = number of calendar days in P.
- For a metric m, `m(P)` = its value aggregated over P.
- Each component produces a value v ∈ [0, 1] plus a status:
  `evaluated` (enough data) or `neutral` (insufficient data).
- Component points: p_i = w_i · v_i. Total S = round(Σ p_i) ∈ [0, 100].

## 5. Inactivity gate and coverage (applies to every report period)

Let **A(P)** = number of days in P with at least one recorded observation
(any study session ended, any `app_usage` row, any `shorts_usage` row, any
`website_event`, any `monitoring_event`). Days count once regardless of how
many rows they contain.

- If A(P) = 0 → **S = 0**, status `insufficient_data`.
- Else if A(P) < a_min(P) → coverage c = A(P) / a_min(P), and
  **S = round(c · Σ p_i)**, status `partial_data`.
- Else S = round(Σ p_i), status `sufficient_data`.

| Period | a_min(P) | Meaning |
| --- | --- | --- |
| daily | 1 | a day with any activity is fully covered |
| weekly | 3 | < 3 active days → partial credit |
| monthly | 7 | < 7 active days → partial credit |

This is the primary anti-inactivity rule: doing nothing = 0, and one good
day cannot fabricate a good week.

## 6. Study performance (w = 40)

**Inputs:** `study_sessions` (started_at, ended_at, status,
actual_duration_seconds), `break_sessions`, `study_schedules`.

**Definitions.** A session is *meaningful* when it ended in P, has a
terminal status (completed | cancelled), and
`actual_duration_seconds ≥ MIN_MEANINGFUL = 300` (5 minutes). Tiny sessions
are excluded entirely — this is the primary anti-gaming rule for study.

Let M = meaningful sessions; M_c = completed subset.

- **Completion** c = |M_c| / |M| (0 when |M| = 0).
- **Volume** q = min(1, total_study_min(P) / (150 · days(P))), where
  total_study_min = Σ actual_duration_seconds / 60 over M.

  The target is 150 minutes (2.5 h) of meaningful study per day-equivalent;
  the linear-to-cap curve means studying 12 h gives **no more credit than
  the target** (documented saturation — no "12 hours = automatically
  maximum score" inflation). The initial 120 min/day candidate was
  rejected after simulation because the idealized profile reached 100 and
  the top of the distribution clustered too easily; 150 min/day keeps
  "Excellent" meaningful (see §15.2/§15.4).

- **v_study = 0.6 · c + 0.4 · q** — finishing what you start is weighted
  slightly above raw volume, and volume is capped.
- **Missing data:** |M| = 0 → v_study = 0.5 (neutral). No study data is
  never treated as perfect study.

**Planned-vs-actual** and **schedule adherence** are NOT included today:
`planned_duration_seconds` is optional (sessions started without a
schedule), and computing schedule "due-ness" from `days_of_week` recurrence
is out of scope until the scheduling system is used at scale. Both are
listed as future refinements.

**Breaks:** not part of the study formula (breaks are counted in reports);
a future refinement may add a small penalty for excessive break ratio
(break_min / session_min above ~20%).

## 7. Distraction control (w = 20)

**Data limitation (explicit):** the current schema has **no reliable app
categorization** — `app_usage` stores `package_name` / `app_name` only, with
no social / entertainment / productivity / study category. Per the phase
constraints we do NOT invent categories, and we do NOT label YouTube or
Instagram as "distracting" (users may use them for study).

**Reliable metric used today — usage moderation:**

For each day d with app_usage rows, x_d = total phone minutes that day.

- Threshold T = 240 minutes (4 h) per day.
- e_d = max(0, x_d − T); s_d = 1 if x_d ≤ T, else max(0, 1 − e_d / T).
  (8 h → 0.0; 6 h → 0.5; 4 h → 1.0.)
- Enforcement signal: let n = count of `monitoring_events` of type
  `LIMIT_REACHED` or `APP_RESTRICTED` in P; apply
  s_d = s_d · (1 − min(0.10, 0.02 · n)).

- **v_distraction** = usage-weighted mean of s_d over days with app data
  (Σ s_d·x_d / Σ x_d) — heavy-usage days dominate, which is the honest
  signal.
- **Missing data:** no app-usage rows in P → v_distraction = 0.5 (neutral).

This component penalizes **excessive** phone time but never rewards minimal
usage beyond the full mark (everyone under 4 h/day earns the same 1.0) —
respecting "not simply reward using the phone less". A future
**app-categorization phase** is required to target specific distracting apps.

## 8. Shorts discipline (w = 25)

**Inputs:** `shorts_usage` (per-day duration_seconds, warning_triggered,
limit_reached), `shorts_settings` (daily_limit_minutes).

For each day d with shorts usage u_d (minutes):

- limit_d = the user's `shorts_settings.daily_limit_minutes`, else the
  documented product default **DEFAULT_LIMIT = 30 minutes/day** (used when
  the user has not configured a limit; Android's shipped default may
  differ — revisit when sync lands).
- over_d = max(0, u_d − limit_d).
- s_d = 1 if u_d ≤ limit_d, else max(0, 1 − over_d / limit_d).
  (At the limit → 1.0; 1.5× limit → 0.5; 2× → 0.0; slightly over → mild
  penalty — matches the intended "little/no penalty in limit, moderate
  penalty slightly above, stronger penalty far above" relationship.)
- Flag penalties: if `limit_reached` on d → s_d ×= 0.9; else if
  `warning_triggered` on d → s_d ×= 0.95 (approaching the limit also costs a
  little).

- **v_shorts** = usage-weighted mean of s_d over days with shorts data.
- **Missing data:** no shorts usage in P → v_shorts = 0.5 (neutral) — no
  Shorts data is deliberately NOT treated as perfect discipline (prevents
  "do nothing → perfect Shorts"). **Documented limitation:** an active user
  who genuinely avoids short-form platforms entirely is scored neutrally
  here rather than rewarded; a future "avoidance credit" (active in P but
  zero shorts) is the recommended refinement once we can distinguish
  avoidance from non-participation.

## 9. Web discipline (w = 10)

**Inputs:** `website_events` (BLOCK_ATTEMPT, BLOCKED, UNBLOCKED),
`blocked_websites`.

Principle: **a blocked attempt is not a sin** — it can mean the enforcement
system worked. We only deduct for *persistence* and *giving in*.

Let A_e = BLOCK_ATTEMPT count, U_e = UNBLOCKED count, R = number of distinct
domains with ≥ 3 attempts in P.

- If the user has **no** `blocked_websites` rows and no web events in P →
  v_web = 0.5 (neutral).
- If the user has ≥ 1 blocked website configured and A_e = 0 → v_web = 1.0
  (perfect avoidance — the config is itself data).
- Otherwise:
  - p_attempts = min(0.15, 0.05 · A_e)   (3 attempts → 0.15 cap)
  - p_unblocks = min(0.10, 0.05 · U_e)   (2 unblocks → 0.10 cap)
  - p_repeat = min(0.10, 0.05 · R)       (2 repeat domains → 0.10 cap)
  - **v_web = max(0.5, 1 − (p_attempts + p_unblocks + p_repeat))**

Floor 0.5 → worst case 5/10 points; single encounters barely register.

## 10. Consistency (w = 5)

- A(P) = active days (see §5 — days with any activity, counted once per day,
  never per session — anti-gaming: 50 tiny sessions in one day = 1 day).
- Target t(P): daily 1, weekly 5, monthly 20.
- **v_consistency = min(1, A(P) / t(P)).**
- This component is evaluable whenever A(P) > 0 (the gate handles A = 0).

## 11. Daily / weekly / monthly aggregation

- **Daily:** every component computed over the single UTC day. Missing →
  neutral; gate as §5.
- **Weekly:** every component computed over the 7-day ISO week (Mon–Sun)
  aggregates **directly** — NOT the average of 7 daily scores. Because
  Σ w_i = 100 and each v_i ≤ 1, the result is already normalized to
  [0, 100]; nothing is summed above 100.
- **Monthly:** same direct aggregation over the calendar month.
- Period boundaries and date interpretation follow the backend's naive-UTC
  convention (as in Phase 13 Reports).

## 12. Score explanation output

Every score must be explainable. The engine (future) returns:

```json
{
  "score": 82,
  "status": "sufficient_data",
  "activity": {"active_days": 6, "required_days": 3, "coverage": 1.0},
  "components": {
    "study":       {"points": 34, "max": 40, "value": 0.85, "status": "evaluated"},
    "shorts":      {"points": 21, "max": 25, "value": 0.84, "status": "evaluated"},
    "distraction": {"points": 16, "max": 20, "value": 0.80, "status": "evaluated"},
    "web":         {"points": 7,  "max": 10, "value": 0.70, "status": "evaluated"},
    "consistency": {"points": 4,  "max": 5,  "value": 0.80, "status": "evaluated"}
  }
}
```

`points` = round(w_i · v_i); `value` is the raw component value. Neutral
components show `value: 0.5` and `status: "neutral"`.

## 13. Anti-gaming rules (design-time; enforcement later)

1. **Tiny sessions excluded:** sessions < 300 s do not count toward study at
   all ("open and immediately complete" yields nothing).
2. **Volume capped:** studying beyond the 120 min/day target earns no extra
   credit ("12 h study ≠ automatic maximum").
3. **Consistency counts days, not sessions:** 50 one-second sessions in one
   day = 1 active day.
4. **Completion ratio on meaningful sessions only** — cannot be inflated by
   junk sessions.
5. **No credit for inactivity:** the gate (§5) makes "do nothing" = 0.
6. **Shorts discipline is limit-relative:** fabricating usage cannot help;
   going over the user's own configured limit always reduces the score.
7. **Web persistence costs points; single blocked encounters are near-free**
   — spamming blocked sites backfires only via the repeat/unblock terms.
8. **Distraction is a moderation penalty, not a low-usage reward** — there
   is no bonus for extreme non-use beyond the full mark.
9. **Future fraud rules (flagged, NOT built):** session-creation rate limits,
   session-open-without-activity detection (started_at ≈ ended_at), and
   detection of clients that suppress warning/limit flags while reporting
   usage.

## 14. Leaderboard compatibility

- Weekly score → `leaderboard_scores` row with `period_type='week'`,
  `period_start`/`period_end` = ISO week bounds; monthly → `period_type='month'`.
- The existing `leaderboard_scores` table is **sufficient** (id, user_id,
  period_type, period_start, period_end, score, timestamps). No schema
  change is required, and none is made.
- Rank is derived later by ordering scores per period — the schema has
  deliberately NO `rank` column. **No rank logic is implemented in this
  phase.**

## 15. Data availability and validation results

### 15.1 Data availability

**Result: PASS (with a caveat).** All source tables exist and are wired to
the Phase 13 reporting aggregations. The current development database holds
**zero** domain rows because every verification script cleans up after
itself; therefore validation in this phase uses **controlled realistic
simulations** (profiles below). Real-data calibration must be re-run once
the Android sync boundary lands and real usage accumulates.

### 15.2 Profile simulation (see `backend/scripts/score_spec_simulation.py`)

| Profile | Study | Shorts | Distraction (phone) | Web | Consistency | **Score** |
| --- | --- | --- | --- | --- | --- | --- |
| A — high study, low Shorts, low distraction | 6/6 sessions, 150 min/day | within 20-min limit | 90 min/day | 0 attempts, blocks configured | 7/7 days | **98 (Excellent)** |
| B — low study, very low phone usage | 1 session/wk | none | 40 min/day | none | 1/7 days | **21 (Poor)** |
| C — high Shorts, low study | 1 session | 120 min/day vs 30 limit | 200 min/day | none | 2/7 days | **34 (Poor)** |
| D — high study, moderate Shorts | 6 sessions, 140 min/day | 45 min/day vs 30 limit | 150 min/day | 1 attempt | 6/7 days | **83 (Strong)** |
| E — no meaningful activity | none | none | none | none | 0/7 | **0 (insufficient_data)** |
| F — heavy study, extreme distraction | 7 sessions, 200 min/day | within limit | 480 min/day + 3 limit events | none | 7/7 days | **75 (Strong)** |

**Fairness read:** A > D > F > C > B > E. Studying well genuinely beats
"using the phone less" (A beats B even though B uses the phone less), heavy
distraction drags an otherwise strong studier down (F < D — the entire
20-point distraction component is lost, visible in the explanation as
`distraction: 0/20`), inactivity is 0 (E), and heavy Shorts usage with
little study ranks near the bottom (C). The ordering matches the product
intent.

### 15.3 Sensitivity (bounded deltas)

Measured deltas (from the simulation script, on weekly profiles):

| Change | Measured Δ score | Bound |
| --- | --- | --- |
| +30 min study (one day, below cap) | **+1** | ≤ +4 |
| −20 min Shorts (45 → 25, crossing the 30-min limit) | **+13** | ≤ +15 |
| +1 completed meaningful session | **+3** | ≤ +5 |
| +1 limit-violation day (60 min vs 30 limit) | **−8** | ≤ −9 |
| +2 more blocked attempts at one domain | **−1** | ≤ −3 |

All changes are bounded. The one deliberate step-change is crossing the
configured Shorts limit (a full discipline-category improvement), which is
explainable: "you got back under your daily limit". If real data shows this
feels jarring, soften the shorts curve (e.g. `1 − over/(2·limit)`); the
spec keeps the current formula until then.

### 15.4 Distribution

A grid sweep over study minutes × Shorts minutes × phone minutes (150
combinations, see the simulation script) produced:

```text
n=150   min=30   p10=50   median=69   p90=89   max=95
% >= 90: 8%   % <= 39: 3%
```

Scores spread across the range with a healthy mid-heavy concentration
(median 69, i.e. "Moderate"), the top is capped at 95 (no combos hit 100 in
the grid — 100 requires maxing every component), and the bottom never
reaches 0 for active profiles (inactivity alone maps to 0 via the gate).
This satisfies the "meaningful distribution" target without manipulating
the formula.

## 16. Required backend changes (for the FUTURE engine phase — NOT this phase)

1. New `ScoreService` (read-only) computing daily/weekly/monthly scores from
   the existing reporting aggregations + `shorts_settings` limits.
2. New read endpoints `GET /score/daily|weekly|monthly` (or one parameterized
   endpoint) returning the §12 explanation structure.
3. Optional write-back of weekly/monthly scores into `leaderboard_scores`
   (schema already sufficient).
4. A future app-categorization phase to make the distraction component
   app-aware (recommended weight shift in §3).
5. Recalibration of band edges and the `DEFAULT_LIMIT`/threshold constants
   against real data once Android sync is live.

**No schema change is required for this phase. No production score code was
written. No rank/leaderboard logic was implemented. Android, AWS and Cognito
are untouched.**
