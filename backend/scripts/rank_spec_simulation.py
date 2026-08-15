"""rank_spec_simulation.py — Phase 15A validation for the Rank / Leaderboard
spec.

Implements the ranking logic from `backend/docs/rank_leaderboard_spec.md`
(competition ranking, deterministic tie-breaking, eligibility rules,
rank-change and winner logic) in pure Python and verifies the required cases
A–H plus determinism and fairness.

This is a SPEC-VALIDATION TOOL ONLY. It is NOT the production Rank engine:
it writes nothing, changes no schema, and produces no persisted data.
"""

import json


def eligible(entry: dict) -> bool:
    """Eligibility: opted in AND enabled AND score status has data."""
    return (
        entry.get("opted_in", False)
        and entry.get("enabled", True)
        and entry.get("score_status") in ("sufficient_data", "partial_data")
    )


def sort_key(entry: dict) -> tuple:
    """Deterministic ordering: -score, -study points, -consistency points,
    user_id ascending (final stable tie-break)."""
    return (
        -entry["score"],
        -entry.get("study_points", 0),
        -entry.get("consistency_points", 0),
        entry["user_id"],
    )


def build_leaderboard(entries: list[dict], page: int = 1, page_size: int = 10,
                      current_user_id: int | None = None) -> dict:
    """Full ranking pass: filter eligible -> sort -> competition ranks ->
    winner / top 3 / paginated entries / current-user info."""
    ranked = sorted((e for e in entries if eligible(e)), key=sort_key)

    # Competition ranking on score only (1, 1, 3 for 100, 100, 99).
    for i, entry in enumerate(ranked):
        if i == 0 or entry["score"] != ranked[i - 1]["score"]:
            entry["rank"] = i + 1
        else:
            entry["rank"] = ranked[i - 1]["rank"]

    total = len(ranked)
    start = (page - 1) * page_size
    page_entries = ranked[start:start + page_size]

    winner = ranked[0] if ranked else None
    top_three = ranked[:3]

    your = None
    if current_user_id is not None:
        your = next((e for e in ranked if e["user_id"] == current_user_id), None)
        if your is None:
            your = next((e for e in entries if e["user_id"] == current_user_id), None)

    return {
        "ranked": ranked,
        "total_participants": total,
        "winner": winner,
        "top_three": top_three,
        "entries": page_entries,
        "your": your,
    }


def rank_change(previous: dict, current: dict, user_id: int) -> int | None:
    """current - previous rank delta; null when the user was not eligible on
    the previous period (or it had no data)."""
    prev_entry = next((e for e in previous.get("ranked", []) if e["user_id"] == user_id), None)
    cur_entry = next((e for e in current.get("ranked", []) if e["user_id"] == user_id), None)
    if prev_entry is None or cur_entry is None:
        return None
    return prev_entry["rank"] - cur_entry["rank"]


def mk(user_id: int, score: int, status: str = "sufficient_data",
       opted_in: bool = True, study: float = 30.0, consistency: float = 4.0,
       display: str | None = None) -> dict:
    return {
        "user_id": user_id,
        "display_name": display or f"User {user_id}",
        "score": score,
        "score_status": status,
        "opted_in": opted_in,
        "enabled": True,
        "study_points": study,
        "consistency_points": consistency,
    }


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  -> {detail}" if not ok else ""))
    if not ok:
        raise SystemExit(f"FAILED: {name} ({detail})")


def main() -> None:
    print("=== CASE A — 10 eligible users with unique scores ===")
    users_a = [mk(uid, 50 + uid) for uid in range(1, 11)]  # scores 51..60
    board_a = build_leaderboard(users_a, current_user_id=5)
    ranks_a = [e["rank"] for e in board_a["ranked"]]
    check("ranks are exactly 1..10 in score-descending order",
          ranks_a == list(range(1, 11)),
          str(ranks_a))
    check("deterministic order (user_id asc within unique scores)",
          [e["user_id"] for e in board_a["ranked"]] == list(reversed(range(1, 11))),
          str([e["user_id"] for e in board_a["ranked"]]))
    check("total participants = 10", board_a["total_participants"] == 10)
    check("winner is the top scorer", board_a["winner"]["user_id"] == 10)

    print("\n=== CASE B — multiple tied scores (100, 100, 99) ===")
    users_b = [
        mk(1, 100, study=32.0), mk(2, 100, study=36.0), mk(3, 99),
        mk(4, 98), mk(5, 98), mk(6, 97),
    ]
    board_b = build_leaderboard(users_b)
    ranks_b = [e["rank"] for e in board_b["ranked"]]
    check("competition ranks are 1,1,3,4,4,6", ranks_b == [1, 1, 3, 4, 4, 6], str(ranks_b))
    tied = [e for e in board_b["ranked"] if e["score"] == 100]
    check("tie broken deterministically by study points (user 2 first)",
          [e["user_id"] for e in tied] == [2, 1],
          str([e["user_id"] for e in tied]))
    check("top_three comes from the same ranked list",
          [e["user_id"] for e in board_b["top_three"]] == [2, 1, 3],
          str([e["user_id"] for e in board_b["top_three"]]))

    print("\n=== CASE C — current user outside top 10 ===")
    # Scores 94..80 (decreasing with uid) -> user 15 is the lowest, rank 15.
    users_c = [mk(uid, 95 - uid) for uid in range(1, 16)]
    board_c = build_leaderboard(users_c, page=1, page_size=10, current_user_id=15)
    check("your_rank = 15 (identifiable outside the visible page)",
          board_c["your"]["rank"] == 15, str(board_c["your"]))
    check("page 1 returns exactly 10 entries",
          len(board_c["entries"]) == 10 and board_c["entries"][0]["rank"] == 1)
    check("winner still correct on paginated request",
          board_c["winner"]["user_id"] == 1)

    print("\n=== CASE D — user opted out ===")
    users_d = [mk(1, 95), mk(2, 90, opted_in=False), mk(3, 85)]
    board_d = build_leaderboard(users_d, current_user_id=2)
    check("opted-out user excluded from entries and totals",
          board_d["total_participants"] == 2
          and all(e["user_id"] != 2 for e in board_d["ranked"]),
          str([e["user_id"] for e in board_d["ranked"]]))
    check("opted-out current user gets no rank (not invented)",
          board_d["your"] is not None and "rank" not in board_d["your"]
          and board_d["your"]["opted_in"] is False,
          str(board_d["your"]))

    print("\n=== CASE E — user with insufficient data ===")
    users_e = [mk(1, 95), mk(2, 0, status="insufficient_data"), mk(3, 85)]
    board_e = build_leaderboard(users_e, current_user_id=2)
    check("insufficient-data user excluded (never ranked at 0)",
          board_e["total_participants"] == 2
          and all(e["user_id"] != 2 for e in board_e["ranked"]),
          str([e["user_id"] for e in board_e["ranked"]]))
    check("partial-data user IS eligible",
          build_leaderboard([mk(1, 95), mk(2, 40, status="partial_data")])["total_participants"] == 2)

    print("\n=== CASE F — current rank improves ===")
    prev_f = build_leaderboard([mk(1, 99), mk(2, 95), mk(3, 90), mk(4, 85), mk(5, 80),
                                mk(6, 75), mk(7, 70), mk(8, 65)])
    # User 8 jumps from 65 to 94 -> rank 8 -> rank 3.
    cur_f = build_leaderboard([mk(1, 99), mk(2, 95), mk(8, 94), mk(3, 90), mk(4, 85),
                               mk(5, 80), mk(6, 75), mk(7, 70)])
    check("rank improved from 8 to 3 -> rank_change +5",
          rank_change(prev_f, cur_f, 8) == 5, str(rank_change(prev_f, cur_f, 8)))

    print("\n=== CASE G — current rank decreases ===")
    prev_g = build_leaderboard([mk(1, 99), mk(2, 98), mk(3, 97), mk(4, 96), mk(5, 95), mk(6, 94)])
    # User 4 falls from 96 to 92 (behind users 5 and 6) -> rank 4 -> rank 6.
    cur_g = build_leaderboard([mk(1, 99), mk(2, 98), mk(3, 97), mk(4, 92), mk(5, 96), mk(6, 95)])
    check("rank declined from 4 to 6 -> rank_change -2",
          rank_change(prev_g, cur_g, 4) == -2, str(rank_change(prev_g, cur_g, 4)))

    print("\n=== CASE H — previous period has no leaderboard data ===")
    cur_h = build_leaderboard([mk(1, 95), mk(2, 90), mk(3, 85)])
    empty_h = build_leaderboard([])
    change = rank_change(empty_h, cur_h, 2)
    check("rank_change is null (not invented) when previous period has no data",
          change is None, str(change))

    print("\n=== DETERMINISM (every case computed twice, identical output) ===")
    def snapshot(board: dict) -> str:
        return json.dumps(
            [(e.get("rank"), e["user_id"], e["score"]) for e in board["ranked"]],
            sort_keys=True,
        )

    for label, entries, page, size in [
        ("A", users_a, 1, 10), ("B", users_b, 1, 10), ("C", users_c, 1, 10),
        ("D", users_d, 1, 10), ("E", users_e, 1, 10),
    ]:
        first = snapshot(build_leaderboard(entries, page, size))
        second = snapshot(build_leaderboard(entries, page, size))
        check(f"case {label} deterministic", first == second)

    print("\n=== FAIRNESS ===")
    board = build_leaderboard(users_b)
    ok_fair = all(
        board["ranked"][i]["score"] >= board["ranked"][i + 1]["score"]
        for i in range(len(board["ranked"]) - 1)
    )
    check("higher score always ranks at-or-above lower score", ok_fair)

    print("\nALL CHECKS PASSED")


if __name__ == "__main__":
    main()
