"""verify_security.py — Phase 19 security audit verification.

Two sections:

  STATIC (no server needed):
    - development identity fails closed in production configuration
    - CORS wildcard rejected outside development
    - secrets absent from tracked source (private keys / AWS / tokens /
      hardcoded credentials) and .env is git-ignored
    - insecure HTTP production configuration rejected (Android release
      network config blocks cleartext; staging/production URLs are HTTPS)
    - Android release boundary: dev identity header gated on BuildConfig.DEBUG

  LIVE (requires a running server + live MySQL, like the other verify
  scripts — see their docstrings for the two-terminal usage):
    - security headers present on every response
    - no CORS allowance for unconfigured origins
    - dev identity header required (400 without it) in development
    - cross-user access denied (other users' records are 404)
    - invalid input rejected (422)
    - excessive pagination rejected (422)
    - malformed domain rejected (422)
    - sensitive error information absent (no stack traces / internals)
    - usage sync is idempotent (no duplicate rows on replay)

The script creates its own dev users + devices and cleans them up. It never
prints secret values and never modifies the database schema.

Usage (from `backend/`):
    .venv\\Scripts\\python -m uvicorn app.main:app   # terminal 1
    .venv\\Scripts\\python -m scripts.verify_security  # terminal 2
"""

import json
import re
import urllib.error
import urllib.request
from pathlib import Path

from sqlalchemy import text

from app.config import Settings
from app.database import SessionLocal
from app.models.app_usage import AppUsage
from app.models.device import Device
from app.models.study_schedule import StudySchedule
from app.models.user import User

BASE = "http://127.0.0.1:8000"
DEV_USER_ID = 90901
OTHER_USER_ID = 90902

ROOT = Path(__file__).resolve().parents[1]

_results: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    _results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  -> {detail}" if not ok else ""))


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------


def request_headers(
    method: str, path: str, body: object | None = None, user_id: int = DEV_USER_ID,
    extra_headers: dict[str, str] | None = None,
):
    """Return (status, parsed-json-or-None, dict-of-lowercase-headers)."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json", "X-Dev-User-Id": str(user_id)}
    headers.update(extra_headers or {})
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            parsed = None
            if raw:
                try:
                    parsed = json.loads(raw)
                except (ValueError, json.JSONDecodeError):
                    parsed = raw.decode(errors="replace")
            return resp.status, parsed, dict(
                (k.lower(), v) for k, v in resp.headers.items()
            )
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        parsed = None
        if raw:
            try:
                parsed = json.loads(raw)
            except (ValueError, json.JSONDecodeError):
                parsed = raw.decode(errors="replace")
        return exc.code, parsed, dict((k.lower(), v) for k, v in exc.headers.items())
    except Exception as exc:  # noqa: BLE001 - surface reachability only
        raise RuntimeError(f"Server unreachable: {exc}")


def cleanup(user_ids: list[int]) -> None:
    """Remove rows this script created (children first for FK safety)."""
    db = SessionLocal()
    try:
        for uid in user_ids:
            db.execute(
                text("DELETE FROM study_schedules WHERE user_id = :uid"), {"uid": uid}
            )
            db.execute(
                text("DELETE FROM app_usage WHERE user_id = :uid"), {"uid": uid}
            )
            db.execute(text("DELETE FROM devices WHERE user_id = :uid"), {"uid": uid})
            db.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": uid})
        db.commit()
    finally:
        db.close()


def setup_device(user_id: int) -> int:
    db = SessionLocal()
    try:
        # The devices FK requires the users row to exist (the server-side
        # ensure_dev_user creates it on first API call — create it here too
        # so device setup works before any endpoint is hit).
        exists = db.query(User.id).filter(User.id == user_id).first() is not None
        if not exists:
            db.add(User(id=user_id))
            db.commit()
        device = Device(user_id=user_id, device_uuid=f"verify-security-{user_id}")
        db.add(device)
        db.commit()
        return device.id
    finally:
        db.close()


# ---------------------------------------------------------------------------
# STATIC checks (no server)
# ---------------------------------------------------------------------------


def static_checks() -> None:
    # 1. Dev identity fails closed in production.
    prod = Settings(APP_ENV="production", _env_file=None)
    dev = Settings(APP_ENV="development", _env_file=None)
    overridden = Settings(
        APP_ENV="production", DEV_IDENTITY_ENABLED=True, _env_file=None
    )
    record(
        "dev identity DISABLED in production config (fail closed)",
        prod.dev_identity_enabled is False,
        f"dev_identity_enabled={prod.dev_identity_enabled}",
    )
    record(
        "dev identity ENABLED in development config",
        dev.dev_identity_enabled is True,
        f"dev_identity_enabled={dev.dev_identity_enabled}",
    )
    record(
        "dev identity explicit override honored",
        overridden.dev_identity_enabled is True,
    )

    # 2. CORS wildcard rejected outside development; allowed in development.
    # The guard is a lazy property that fails fast at startup (main.py reads
    # it when wiring CORSMiddleware) — access it to trigger the check.
    try:
        prod_cors = Settings(APP_ENV="production", CORS_ALLOW_ORIGINS="*", _env_file=None)
        prod_cors.cors_allow_origins
        wildcard_prod_rejected = False
    except ValueError:
        wildcard_prod_rejected = True
    record(
        "CORS '*' rejected outside development",
        wildcard_prod_rejected,
        "Settings(APP_ENV=production, CORS_ALLOW_ORIGINS='*') must raise",
    )
    try:
        dev_cors = Settings(
            APP_ENV="development", CORS_ALLOW_ORIGINS="*", _env_file=None
        )
        record(
            "CORS '*' allowed in development only",
            dev_cors.cors_allow_origins == ["*"],
        )
    except ValueError:
        record("CORS '*' allowed in development only", False, "unexpectedly rejected")

    # 3. Secrets absent from tracked source.
    secret_patterns = [
        re.compile(r"AKIA[0-9A-Z]{16}"),  # AWS access key id
        re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),  # private keys
        re.compile(r"(sk|pk)_(live|test)_[a-zA-Z0-9]{16,}"),  # Stripe keys
        re.compile(r"ghp_[a-zA-Z0-9]{20,}"),  # GitHub tokens
        re.compile(r"xox[baprs]-[a-zA-Z0-9-]{10,}"),  # Slack tokens
        re.compile(
            r"(DB_PASSWORD|password|api[_-]?key|secret)\s*=\s*[\"'][^\"']{8,}[\"']"
        ),
    ]
    scan_dirs = [
        ROOT / "app",
        ROOT / "scripts",
        ROOT.parent / "app" / "src" / "main" / "java",
        ROOT.parent / "app" / "src" / "main" / "res",
        ROOT.parent / "app" / "build.gradle.kts",
        ROOT.parent / "app" / "proguard-rules.pro",
    ]
    hits: list[str] = []
    for directory in scan_dirs:
        if directory.is_file():
            candidates = [directory]
        elif directory.is_dir():
            candidates = [
                p for p in directory.rglob("*")
                if p.is_file()
                and "__pycache__" not in p.parts
                and p.suffix in {".py", ".kt", ".kts", ".xml", ".gradle"}
            ]
        else:
            continue
        for path in candidates:
            try:
                content = path.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for pattern in secret_patterns:
                match = pattern.search(content)
                if match:
                    # Report file + secret TYPE only — never the value.
                    hits.append(f"{path.relative_to(ROOT.parent)} ({match.group(0)[:8]}...)")
                    break
    record(
        "no secrets in tracked source (private keys/AWS/tokens/credentials)",
        not hits,
        "; ".join(hits) if hits else "clean",
    )

    # 4. .env is git-ignored.
    gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8") if (ROOT / ".gitignore").exists() else ""
    record(
        ".env is git-ignored",
        ".env" in gitignore.splitlines(),
        "backend/.gitignore must contain .env",
    )

    # 5. Insecure HTTP production configuration rejected (Android).
    backend_config = (
        ROOT.parent / "app" / "src" / "main" / "java" / "com" / "shortscap" / "app" / "network" / "BackendConfig.kt"
    ).read_text(encoding="utf-8")
    record(
        "Android staging/production URLs are HTTPS",
        "https://staging.shortscap.example" in backend_config
        and "https://api.shortscap.example" in backend_config,
        "BackendConfig STAGING/PRODUCTION must use https://",
    )
    netsec = (
        ROOT.parent / "app" / "src" / "main" / "res" / "xml" / "network_security_config.xml"
    ).read_text(encoding="utf-8")
    record(
        "Android release network config blocks cleartext",
        'cleartextTrafficPermitted="false"' in netsec,
        "main/res/xml/network_security_config.xml must block cleartext",
    )
    record(
        "Android dev identity gated on BuildConfig.DEBUG",
        "BuildConfig.DEBUG" in backend_config
        and "devIdentityEnabled" in backend_config,
        "BackendConfig must gate dev identity on BuildConfig.DEBUG",
    )
    http_api = (
        ROOT.parent / "app" / "src" / "main" / "java" / "com" / "shortscap" / "app" / "network" / "HttpBackendApi.kt"
    ).read_text(encoding="utf-8")
    record(
        "Android sends dev identity header only when enabled",
        "devIdentityEnabled" in http_api,
        "HttpBackendApi must check devIdentityEnabled",
    )


# ---------------------------------------------------------------------------
# LIVE checks (running server)
# ---------------------------------------------------------------------------


def live_checks() -> None:
    # Server sanity.
    status, body, headers = request_headers("GET", "/")
    record("GET / (server up)", status == 200, f"status={status}")

    status, body, headers = request_headers("GET", "/health/db")
    record(
        "GET /health/db connected",
        status == 200 and isinstance(body, dict) and body.get("status") == "connected",
        f"status={status} body={body}",
    )
    status, _, _ = request_headers("GET", "/docs")
    record("GET /docs (Swagger)", status == 200, f"status={status}")

    # 6. Security headers on every response.
    status, body, headers = request_headers("GET", "/")
    record(
        "security headers present (nosniff / frame / referrer)",
        headers.get("x-content-type-options", "").lower() == "nosniff"
        and headers.get("x-frame-options", "").lower() == "deny"
        and headers.get("referrer-policy", "").lower() == "no-referrer",
        f"x-content-type-options={headers.get('x-content-type-options')}",
    )

    # 7. No CORS allowance for unconfigured origins (native clients unaffected).
    status, body, headers = request_headers(
        "GET", "/", extra_headers={"Origin": "https://evil.example"}
    )
    record(
        "no Access-Control-Allow-Origin for unconfigured origin",
        "access-control-allow-origin" not in headers,
        "CORS must not open up for arbitrary origins",
    )

    # 8. Dev identity required in development (400 without header).
    req = urllib.request.Request(BASE + "/settings", method="GET")
    try:
        urllib.request.urlopen(req, timeout=10)
        missing_status = 200
    except urllib.error.HTTPError as exc:
        missing_status = exc.code
    record(
        "missing dev identity header -> 400 (not silently accepted)",
        missing_status == 400,
        f"status={missing_status}",
    )

    # 9. Cross-user access denied.
    dev_device = setup_device(DEV_USER_ID)
    other_device = setup_device(OTHER_USER_ID)
    status, created, _ = request_headers(
        "POST", "/study/schedules",
        {"title": "Security audit schedule", "duration_minutes": 30},
        user_id=DEV_USER_ID,
    )
    schedule_id = created.get("id") if isinstance(created, dict) else None
    record("schedule created for user A", status == 201 and schedule_id, f"status={status}")
    status, body, _ = request_headers(
        "GET", f"/study/schedules/{schedule_id}", user_id=OTHER_USER_ID
    )
    record(
        "cross-user schedule read -> 404",
        status == 404,
        f"status={status} body={body}",
    )
    status, body, _ = request_headers(
        "PUT", f"/study/schedules/{schedule_id}",
        {"title": "hijack"}, user_id=OTHER_USER_ID,
    )
    record(
        "cross-user schedule update -> 404",
        status == 404,
        f"status={status}",
    )
    status, body, _ = request_headers(
        "GET", "/study/schedules", user_id=OTHER_USER_ID
    )
    record(
        "user B list does not expose user A schedules",
        status == 200 and body == [],
        f"status={status} body={body}",
    )
    # Device ownership: user B cannot sync usage against user A's device.
    status, body, _ = request_headers(
        "POST", "/monitoring/app-usage/sync",
        {
            "device_id": dev_device,
            "package_name": "com.example.app",
            "usage_date": "2026-08-16",
            "duration_seconds": 60,
            "launch_count": 1,
        },
        user_id=OTHER_USER_ID,
    )
    record(
        "sync rejects another user's device -> 404",
        status == 404,
        f"status={status} body={body}",
    )

    # 10. Invalid input rejected (422).
    status, body, _ = request_headers(
        "POST", "/study/schedules", {"title": "", "duration_minutes": 30},
        user_id=DEV_USER_ID,
    )
    record("empty title rejected -> 422", status == 422, f"status={status}")
    status, body, _ = request_headers(
        "PUT", "/settings", {"theme": "neon"}, user_id=DEV_USER_ID
    )
    record("unsupported theme value rejected -> 422", status == 422, f"status={status}")

    # 11. Excessive pagination rejected (422).
    status, _, _ = request_headers("GET", "/shorts/usage?page_size=1000", user_id=DEV_USER_ID)
    record("page_size=1000 rejected -> 422", status == 422, f"status={status}")
    status, _, _ = request_headers("GET", "/rank/weekly?page_size=100000", user_id=DEV_USER_ID)
    record("rank page_size=100000 rejected -> 422", status == 422, f"status={status}")

    # 12. Malformed domain rejected (422).
    status, body, _ = request_headers(
        "POST", "/websites/blocked", {"domain": "ht tp://not a domain !!"},
        user_id=DEV_USER_ID,
    )
    record("malformed domain rejected -> 422", status == 422, f"status={status}")

    # 13. Sensitive error information absent (no stack / internals leaked).
    status, body, _ = request_headers("GET", "/definitely-not-a-route", user_id=DEV_USER_ID)
    raw = json.dumps(body) if body is not None else ""
    record(
        "unknown route 404 without internal details",
        status == 404
        and "Traceback" not in raw
        and "sqlalchemy" not in raw.lower()
        and 'File "' not in raw,
        f"status={status} body={str(body)[:120]}",
    )

    # 14. Usage sync idempotent on replay (no duplicate rows).
    db = SessionLocal()
    try:
        before = db.query(AppUsage).filter(
            AppUsage.user_id == DEV_USER_ID,
            AppUsage.device_id == dev_device,
            AppUsage.package_name == "com.example.security",
            AppUsage.usage_date == "2026-08-16",
        ).count()
    finally:
        db.close()
    payload = {
        "device_id": dev_device,
        "package_name": "com.example.security",
        "usage_date": "2026-08-16",
        "duration_seconds": 300,
        "launch_count": 3,
    }
    status, _, _ = request_headers("POST", "/monitoring/app-usage/sync", payload, user_id=DEV_USER_ID)
    status2, _, _ = request_headers("POST", "/monitoring/app-usage/sync", payload, user_id=DEV_USER_ID)
    db = SessionLocal()
    try:
        after = db.query(AppUsage).filter(
            AppUsage.user_id == DEV_USER_ID,
            AppUsage.device_id == dev_device,
            AppUsage.package_name == "com.example.security",
            AppUsage.usage_date == "2026-08-16",
        ).count()
    finally:
        db.close()
    record(
        "duplicate sync does not create duplicate rows (idempotent)",
        status == 200 and status2 == 200 and after == before + 1,
        f"before={before} after={after} status={status},{status2}",
    )


def main() -> None:
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    static_checks()

    print("\n-- LIVE checks (server on %s) --" % BASE)
    try:
        live_checks()
    except RuntimeError as exc:
        record("server reachable", False, str(exc))

    # Cleanup rows this script created.
    cleanup([DEV_USER_ID, OTHER_USER_ID])
    print("\nCleaned up verification rows.")

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
