"""verify_web.py — end-to-end verification for the Phase 12 web data layer.

Exercises the full Web flow (blocked-website CRUD -> duplicate prevention ->
domain normalization -> website events -> filters -> summary) plus
invalid-input and ownership-isolation cases against a RUNNING server and a
live MySQL database, then verifies the rows directly in MySQL and reports
PASS/FAIL per item. Also confirms the existing Settings, Study, Monitoring
and Shorts endpoints still work.

Usage (two terminals, from `backend/`):
    .venv\\Scripts\\python -m uvicorn app.main:app --reload      # terminal 1
    .venv\\Scripts\\python -m scripts.verify_web                  # terminal 2

The script creates its own dev users + devices and cleans them up afterwards.
It never modifies the database schema.
"""

import json
import urllib.error
import urllib.parse
import urllib.request

from sqlalchemy import text

from app.database import SessionLocal
from app.models.blocked_website import BlockedWebsite
from app.models.device import Device
from app.models.user import User
from app.models.website_event import WebsiteEvent

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90412
OTHER_USER_ID = 90413

_results: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    _results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  -> {detail}" if not ok else ""))


def request(method: str, path: str, body: object | None = None, user_id: int = DEV_USER_ID):
    """Return (status, parsed-json-or-None). Never raises on HTTP errors."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", "X-Dev-User-Id": str(user_id)},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            if not raw:
                return resp.status, None
            try:
                return resp.status, json.loads(raw)
            except (ValueError, json.JSONDecodeError):
                return resp.status, raw.decode(errors="replace")
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        try:
            detail = json.loads(raw)
        except Exception:  # noqa: BLE001
            detail = raw.decode(errors="replace")
        return exc.code, detail
    except urllib.error.URLError as exc:
        record("Server reachable", False, f"is uvicorn running on {BASE}? ({exc})")
        raise SystemExit(1)


def expect_status(name: str, got: int, want: int, detail: object | None = None) -> None:
    record(name, got == want, f"expected {want}, got {got} ({detail})")


def setup_devices() -> tuple[int, int]:
    """Insert dev users + one device each; return (dev_device_id, other_device_id)."""
    db = SessionLocal()
    try:
        db.add(User(id=DEV_USER_ID))
        db.add(User(id=OTHER_USER_ID))
        db.flush()
        dev_device = Device(
            user_id=DEV_USER_ID,
            device_uuid=f"verify-web-dev-{DEV_USER_ID}",
            device_name="Verify Dev Device",
            is_active=True,
        )
        other_device = Device(
            user_id=OTHER_USER_ID,
            device_uuid=f"verify-web-other-{OTHER_USER_ID}",
            device_name="Verify Other Device",
            is_active=True,
        )
        db.add_all([dev_device, other_device])
        db.commit()
        return dev_device.id, other_device.id
    finally:
        db.close()


def cleanup(user_ids: list[int]) -> None:
    """Remove rows created by this script (dev users, devices, web rows)."""
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(text("DELETE FROM website_events WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM blocked_websites WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM devices WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def main() -> None:
    # Defensive: clear leftovers from a previously interrupted run first.
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    dev_device_id, other_device_id = setup_devices()

    # ------------------------------------------------------------------
    # 0. Server sanity (FastAPI verification)
    # ------------------------------------------------------------------
    status, body = request("GET", "/")
    expect_status("GET / (server up)", status, 200, body)

    status, body = request("GET", "/health/db")
    ok = status == 200 and isinstance(body, dict) and body.get("status") == "connected"
    record("GET /health/db connected", ok, f"status={status} body={body}")

    status, _ = request("GET", "/docs")
    expect_status("GET /docs (Swagger)", status, 200)

    # ------------------------------------------------------------------
    # 1. Blocked website CRUD + domain normalization
    # ------------------------------------------------------------------
    # "https://www.YouTube.com/" must normalize to youtube.com for storage.
    status, website = request(
        "POST", "/websites/blocked",
        {"domain": "https://www.YouTube.com/", "verification_status": "pending", "is_blocked": True},
    )
    expect_status("POST /websites/blocked -> 201", status, 201, website)
    record(
        "domain normalized (www + scheme + case + trailing slash stripped)",
        status == 201
        and isinstance(website, dict)
        and website.get("normalized_domain") == "youtube.com"
        and website.get("user_id") == DEV_USER_ID
        and website.get("is_blocked") is True
        and website.get("verification_status") == "pending",
        website,
    )
    youtube_id = website.get("id") if status == 201 else None

    # Same logical domain, different spelling -> 409 (no duplicate row).
    status, dup = request(
        "POST", "/websites/blocked", {"domain": "www.youtube.com"}
    )
    expect_status("duplicate normalized domain -> 409", status, 409, dup)

    # Another domain, also normalizing (path/query stripped).
    status, instagram = request(
        "POST", "/websites/blocked",
        {"domain": "https://instagram.com/reels/?tab=shorts"},
    )
    expect_status("POST instagram.com (with path/query) -> 201", status, 201, instagram)
    record(
        "path/query stripped from instagram domain",
        status == 201 and instagram.get("normalized_domain") == "instagram.com",
        instagram,
    )
    instagram_id = instagram.get("id") if status == 201 else None

    status, listing = request("GET", "/websites/blocked")
    expect_status("GET /websites/blocked -> 200", status, 200, listing)
    record(
        "list returns 2 websites (oldest first)",
        status == 200 and isinstance(listing, list) and len(listing) == 2
        and listing[0].get("normalized_domain") == "youtube.com",
        listing,
    )

    status, one = request("GET", f"/websites/blocked/{youtube_id}")
    expect_status("GET /websites/blocked/{id} -> 200", status, 200, one)
    record(
        "get-by-id returns the youtube.com row",
        status == 200 and one.get("normalized_domain") == "youtube.com",
        one,
    )

    # Check endpoint — case/prefix-insensitive.
    status, check = request("GET", "/websites/blocked/check?domain=WWW.YouTube.com")
    expect_status("GET /websites/blocked/check -> 200", status, 200, check)
    record(
        "check answers blocked=true for a normalized variant",
        status == 200
        and check.get("normalized_domain") == "youtube.com"
        and check.get("is_present") is True
        and check.get("is_blocked") is True,
        check,
    )
    status, check = request("GET", "/websites/blocked/check?domain=https://tiktok.com/")
    record(
        "check answers not-present for an unblocked domain",
        status == 200
        and check.get("is_present") is False
        and check.get("is_blocked") is False,
        check,
    )

    # ------------------------------------------------------------------
    # 2. Website events
    # ------------------------------------------------------------------
    status, event = request(
        "POST", "/web/events",
        {
            "device_id": dev_device_id,
            "domain": "https://youtube.com/watch?v=abc",
            "event_type": "BLOCK_ATTEMPT",
        },
    )
    expect_status("POST /web/events BLOCK_ATTEMPT -> 201", status, 201, event)
    record(
        "event created, domain normalized, user + timestamp attached",
        status == 201
        and isinstance(event, dict)
        and event.get("domain") == "youtube.com"
        and event.get("user_id") == DEV_USER_ID
        and event.get("event_type") == "BLOCK_ATTEMPT"
        and event.get("occurred_at") is not None
        and event.get("device_id") == dev_device_id,
        event,
    )

    # With blocked_website_id -> validates ownership of the website too.
    status, event = request(
        "POST", "/web/events",
        {
            "device_id": dev_device_id,
            "blocked_website_id": youtube_id,
            "domain": "youtube.com",
            "event_type": "BLOCKED",
        },
    )
    expect_status("POST /web/events BLOCKED (with blocked_website_id) -> 201", status, 201, event)
    record(
        "blocked event references the user's own website",
        status == 201 and event.get("blocked_website_id") == youtube_id,
        event,
    )

    # Uppercase domain variant normalizes too; no device (device_id optional).
    status, event = request(
        "POST", "/web/events",
        {"domain": "YOUTUBE.COM", "event_type": "UNBLOCKED"},
    )
    expect_status("POST /web/events UNBLOCKED -> 201", status, 201, event)
    record(
        "uppercase domain normalized to youtube.com",
        status == 201 and event.get("domain") == "youtube.com",
        event,
    )

    status, event = request(
        "POST", "/web/events",
        {"device_id": dev_device_id, "domain": "instagram.com", "event_type": "BLOCK_ATTEMPT"},
    )
    expect_status("POST /web/events instagram BLOCK_ATTEMPT -> 201", status, 201, event)

    status, event = request(
        "POST", "/web/events",
        {"domain": "https://instagram.com/", "event_type": "BLOCKED"},
    )
    expect_status("POST /web/events instagram BLOCKED -> 201", status, 201, event)

    # Explicit aware timestamp -> normalized to naive UTC.
    status, tz_event = request(
        "POST", "/web/events",
        {"domain": "instagram.com", "event_type": "UNBLOCKED", "occurred_at": "2026-08-10T09:00:00+05:30"},
    )
    expect_status("POST event with aware timestamp -> 201", status, 201, tz_event)
    record(
        "aware timestamp normalized to naive UTC (03:30:00)",
        status == 201
        and isinstance(tz_event, dict)
        and tz_event.get("occurred_at") == "2026-08-10T03:30:00",
        tz_event,
    )

    status, events = request("GET", "/web/events")
    expect_status("GET /web/events -> 200", status, 200, events)
    record(
        "all 6 events returned (newest first)",
        status == 200 and isinstance(events, list) and len(events) == 6,
        events,
    )

    status, filtered = request("GET", "/web/events?event_type=BLOCKED")
    record(
        "event_type filter -> 2 BLOCKED events",
        status == 200 and isinstance(filtered, list) and len(filtered) == 2,
        filtered,
    )

    status, filtered = request("GET", "/web/events?domain=youtube.com")
    record(
        "domain filter (normalized) -> 3 youtube events",
        status == 200 and isinstance(filtered, list) and len(filtered) == 3,
        filtered,
    )

    status, filtered = request("GET", f"/web/events?device_id={dev_device_id}")
    record(
        "device filter -> 3 events",
        status == 200 and isinstance(filtered, list) and len(filtered) == 3,
        filtered,
    )

    status, filtered = request(
        "GET", "/web/events?start_date=2026-08-10&end_date=2026-08-10"
    )
    record(
        "date-range filter -> 1 event (the normalized 03:30 one)",
        status == 200 and isinstance(filtered, list) and len(filtered) == 1,
        filtered,
    )

    # ------------------------------------------------------------------
    # 3. Web summary
    # ------------------------------------------------------------------
    status, summary = request("GET", "/web/summary")
    expect_status("GET /web/summary -> 200", status, 200, summary)
    # Events: 2 BLOCK_ATTEMPT, 2 BLOCKED, 2 UNBLOCKED; 2 distinct blocked domains.
    summary_ok = (
        status == 200
        and isinstance(summary, dict)
        and summary.get("total_block_attempts") == 2
        and summary.get("total_blocked_events") == 2
        and summary.get("total_unblock_events") == 2
        and summary.get("unique_blocked_domains") == 2
    )
    record("summary values correct", summary_ok, summary)

    # ------------------------------------------------------------------
    # 4. Update + delete
    # ------------------------------------------------------------------
    status, updated = request(
        "PUT", f"/websites/blocked/{youtube_id}", {"is_blocked": False}
    )
    expect_status("PUT /websites/blocked/{id} (is_blocked=false) -> 200", status, 200, updated)
    record(
        "update flips block status, preserves domain",
        status == 200
        and updated.get("is_blocked") is False
        and updated.get("normalized_domain") == "youtube.com",
        updated,
    )
    status, check = request("GET", "/websites/blocked/check?domain=youtube.com")
    record(
        "check reflects updated block status",
        status == 200 and check.get("is_present") is True and check.get("is_blocked") is False,
        check,
    )

    # Re-pointing the domain at an already-blocked domain -> 409.
    status, conflict = request(
        "PUT", f"/websites/blocked/{youtube_id}", {"domain": "https://instagram.com/"}
    )
    expect_status("PUT to colliding domain -> 409", status, 409, conflict)

    status, _ = request("DELETE", f"/websites/blocked/{instagram_id}")
    expect_status("DELETE /websites/blocked/{id} -> 204", status, 204)

    status, gone = request("GET", f"/websites/blocked/{instagram_id}")
    expect_status("GET deleted website -> 404", status, 404, gone)

    # ------------------------------------------------------------------
    # 5. Invalid tests
    # ------------------------------------------------------------------
    status, _ = request("POST", "/websites/blocked", {"domain": ""})
    expect_status("empty domain -> 422", status, 422)

    status, _ = request("POST", "/websites/blocked", {"domain": "   "})
    expect_status("whitespace-only domain -> 422", status, 422)

    status, _ = request("POST", "/websites/blocked", {"domain": "http://"})
    expect_status("malformed domain (no host) -> 422", status, 422)

    status, _ = request("POST", "/websites/blocked", {"domain": "localhost"})
    expect_status("bare label / localhost -> 422", status, 422)

    status, _ = request("POST", "/websites/blocked", {"domain": "192.168.0.1"})
    expect_status("IP address rejected as public domain -> 422", status, 422)

    status, _ = request("GET", "/websites/blocked/999999999")
    expect_status("unknown website id -> 404", status, 404)

    status, _ = request(
        "POST", "/web/events", {"device_id": 999999999, "event_type": "BLOCKED"}
    )
    expect_status("unknown device -> 404", status, 404)

    status, _ = request(
        "POST", "/web/events", {"device_id": other_device_id, "event_type": "BLOCKED"}
    )
    expect_status("another user's device -> 404", status, 404)

    status, _ = request(
        "POST", "/web/events", {"blocked_website_id": youtube_id, "event_type": "BLOCKED"},
        user_id=OTHER_USER_ID,
    )
    expect_status("another user's website reference -> 404", status, 404)

    status, _ = request("POST", "/web/events", {"event_type": "NOT_A_REAL_EVENT"})
    expect_status("invalid event type -> 422", status, 422)

    status, _ = request("POST", "/web/events", {"domain": "not a domain", "event_type": "BLOCKED"})
    expect_status("invalid domain in event -> 422", status, 422)

    status, _ = request("GET", "/web/events?start_date=2026-08-12&end_date=2026-08-10")
    expect_status("inverted event date range -> 422", status, 422)

    # ------------------------------------------------------------------
    # 6. User isolation (another user sees none of this data)
    # ------------------------------------------------------------------
    status, listing = request("GET", "/websites/blocked", user_id=OTHER_USER_ID)
    record(
        "other user sees no blocked websites",
        status == 200 and isinstance(listing, list) and len(listing) == 0,
        listing,
    )
    status, events = request("GET", "/web/events", user_id=OTHER_USER_ID)
    record(
        "other user sees no website events",
        status == 200 and isinstance(events, list) and len(events) == 0,
        events,
    )
    status, summary = request("GET", "/web/summary", user_id=OTHER_USER_ID)
    record(
        "other user summary is all zeros",
        status == 200 and summary.get("total_block_attempts") == 0
        and summary.get("unique_blocked_domains") == 0,
        summary,
    )

    # ------------------------------------------------------------------
    # 7. MySQL persistence (direct check, independent of the API)
    # ------------------------------------------------------------------
    db = SessionLocal()
    try:
        website_rows = db.query(BlockedWebsite).filter(BlockedWebsite.user_id == DEV_USER_ID).all()
        event_rows = db.query(WebsiteEvent).filter(WebsiteEvent.user_id == DEV_USER_ID).all()

        record(
            "blocked_websites rows persisted (youtube.com remains, instagram deleted)",
            len(website_rows) == 1
            and website_rows[0].normalized_domain == "youtube.com"
            and website_rows[0].is_blocked is False,
            f"rows={[(w.normalized_domain, w.is_blocked) for w in website_rows]}",
        )
        record("website_events rows persisted (6)", len(event_rows) == 6, f"count={len(event_rows)}")

        youtube = website_rows[0] if website_rows else None
        record(
            "blocked_website ownership (user) correct",
            youtube is not None
            and youtube.user_id == DEV_USER_ID
            and youtube.domain == "https://www.YouTube.com/",  # raw input preserved
            str(youtube),
        )
        record(
            "no duplicate rows for normalized youtube.com",
            len([w for w in website_rows if w.normalized_domain == "youtube.com"]) == 1,
            f"count={len([w for w in website_rows if w.normalized_domain == 'youtube.com'])}",
        )

        blocked_event = next((e for e in event_rows if e.event_type == "BLOCKED"), None)
        record(
            "event FK (device) + ownership (user) + normalized domain correct",
            blocked_event is not None
            and blocked_event.device_id == dev_device_id
            and blocked_event.user_id == DEV_USER_ID
            and blocked_event.domain == "youtube.com",
            str(blocked_event),
        )
        record(
            "event blocked_website FK points at the user's website",
            blocked_event is not None and blocked_event.blocked_website_id == youtube_id,
            str(blocked_event.blocked_website_id) if blocked_event else "missing",
        )

        tz_event = next(
            (e for e in event_rows if e.occurred_at.strftime("%Y-%m-%dT%H:%M:%S") == "2026-08-10T03:30:00"),
            None,
        )
        record(
            "aware timestamp stored as naive UTC (03:30:00)",
            tz_event is not None,
            "missing 03:30:00 row",
        )

        event_types = {e.event_type for e in event_rows}
        record(
            "events match submitted actions (BLOCK_ATTEMPT/BLOCKED/UNBLOCKED only)",
            {"BLOCK_ATTEMPT", "BLOCKED", "UNBLOCKED"} <= event_types
            and len(event_types) == 3,
            sorted(event_types),
        )
    finally:
        db.close()

    # ------------------------------------------------------------------
    # 8. Regression: existing Settings + Study + Monitoring + Shorts endpoints
    # ------------------------------------------------------------------
    status, body = request("GET", "/settings")
    expect_status("GET /settings -> 200", status, 200, body)
    status, body = request("GET", "/settings/monitoring")
    expect_status("GET /settings/monitoring -> 200", status, 200, body)
    status, body = request("GET", "/settings/shorts")
    expect_status("GET /settings/shorts -> 200", status, 200, body)

    status, body = request("GET", "/study/schedules")
    expect_status("GET /study/schedules -> 200", status, 200, body)
    status, sess = request("POST", "/study/sessions/start", {})
    expect_status("POST /study/sessions/start -> 201", status, 201, sess)
    if status == 201 and isinstance(sess, dict):
        sid = sess.get("id")
        status, ended = request("POST", f"/study/sessions/{sid}/end")
        expect_status("POST /study/sessions/{id}/end -> 200", status, 200, ended)

    status, body = request("GET", "/monitoring/app-usage")
    expect_status("GET /monitoring/app-usage -> 200", status, 200, body)
    status, body = request("GET", "/monitoring/summary")
    expect_status("GET /monitoring/summary -> 200", status, 200, body)

    status, body = request("GET", "/shorts/usage")
    expect_status("GET /shorts/usage -> 200", status, 200, body)
    status, body = request("GET", "/shorts/summary")
    expect_status("GET /shorts/summary -> 200", status, 200, body)

    # ------------------------------------------------------------------
    # 9. Cleanup (rows this script created)
    # ------------------------------------------------------------------
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    print("\nCleaned up verification rows.")

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------
    failed = [r for r in _results if not r[1]]
    total = len(_results)
    print(f"\n{total - len(failed)}/{total} checks passed")
    if failed:
        print("FAILED checks:")
        for name, _, detail in failed:
            print(f"  - {name} ({detail})")
        raise SystemExit(1)
    print("ALL CHECKS PASSED")


if __name__ == "__main__":
    main()
