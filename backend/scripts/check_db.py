"""check_db.py — ShortsCap backend: real MySQL connectivity check (Phase 3).

Reports the ACTUAL connection state. It never fabricates success:
- success        -> a real `SELECT 1` round-trip against MySQL succeeded
- not_configured -> MySQL is unreachable / credentials not set in `.env`

Run from the `backend/` directory:
    .venv/Scripts/python -m scripts.check_db
"""

import sys

from app.database import check_database_connection


def main() -> int:
    result = check_database_connection()
    print("ShortsCap backend — MySQL connectivity (Phase 3)")
    print(f"  status      : {result['status']}")
    print(f"  message     : {result['message']}")
    print(f"  database URL: {result['database']}")
    print("\nNext step if NOT CONFIGURED: create backend/.env from backend/.env.example")
    print("with valid DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME, then rerun this check.")
    return 0 if result["status"] == "success" else 1


if __name__ == "__main__":
    sys.exit(main())