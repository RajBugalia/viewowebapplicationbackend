import requests
import sys
from typing import Any, Dict, Optional

BASE_URL = "http://localhost:8765"
LOGIN_PATH = "/api/auth/login"
USER_ME_PATH = "/api/user/me"
TIMEOUT = 30.0

ADMIN_EMAIL = "admin@pixl.com"
ADMIN_PASSWORD = "admin123"


def _extract_token_from_login_response(data: Dict[str, Any]) -> Optional[str]:
    # Common token fields used by various implementations
    if not isinstance(data, dict):
        return None
    # Direct token fields
    for key in ("token", "jwt", "access_token", "accessToken"):
        if key in data and isinstance(data[key], str) and data[key].strip():
            return data[key].strip()
    # Some APIs return tokenType + accessToken separately
    if "accessToken" in data and "tokenType" in data and isinstance(data["accessToken"], str):
        return data["accessToken"].strip()
    # nested under data or user
    for parent in ("data", "user", "result"):
        if parent in data and isinstance(data[parent], dict):
            nested = _extract_token_from_login_response(data[parent])
            if nested:
                return nested
    return None


def _find_email_in_response(data: Any) -> Optional[str]:
    # Search common locations for an email field
    if isinstance(data, dict):
        if "email" in data and isinstance(data["email"], str):
            return data["email"]
        # common nested containers
        for key in ("data", "user", "result", "profile"):
            if key in data:
                found = _find_email_in_response(data[key])
                if found:
                    return found
        # sometimes the user object is directly under 'user'
        for k, v in data.items():
            if isinstance(v, dict):
                found = _find_email_in_response(v)
                if found:
                    return found
    return None


def test_get_api_user_me_with_valid_token_TC003():
    login_url = BASE_URL.rstrip("/") + LOGIN_PATH
    me_url = BASE_URL.rstrip("/") + USER_ME_PATH

    # Step 1: Login to obtain JWT
    login_payload = {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    try:
        resp = requests.post(login_url, json=login_payload, headers=headers, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Login request failed: {e}")

    assert resp is not None, "No response received from login endpoint"
    assert resp.status_code == 200, f"Expected 200 from login, got {resp.status_code}: {resp.text}"

    try:
        login_json = resp.json()
    except ValueError:
        raise AssertionError(f"Login response is not valid JSON: {resp.text}")

    token_value = _extract_token_from_login_response(login_json)
    assert token_value, f"JWT token not found in login response: {login_json}"

    # If token_value already contains "Bearer " prefix, use it; else prefix with Bearer when setting header
    auth_header_value = token_value if token_value.lower().startswith("bearer ") else f"Bearer {token_value}"

    # Step 2: GET /api/user/me with Authorization header
    me_headers = {"Accept": "application/json", "Authorization": auth_header_value}
    try:
        me_resp = requests.get(me_url, headers=me_headers, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Request to {USER_ME_PATH} failed: {e}")

    assert me_resp is not None, "No response received from /api/user/me endpoint"
    assert me_resp.status_code == 200, f"Expected 200 from /api/user/me, got {me_resp.status_code}: {me_resp.text}"

    try:
        me_json = me_resp.json()
    except ValueError:
        raise AssertionError(f"/api/user/me response is not valid JSON: {me_resp.text}")

    email = _find_email_in_response(me_json)
    assert email, f"Email not found in /api/user/me response JSON: {me_json}"
    assert email.lower() == ADMIN_EMAIL.lower(), f"Expected email '{ADMIN_EMAIL}' in profile, got '{email}'"

    # Basic sanity checks for profile keys (optional but useful)
    # Accept presence of at least one identifying field: id or email or username
    has_id = False
    if isinstance(me_json, dict):
        if "id" in me_json:
            has_id = True
        for key in ("data", "user", "result", "profile"):
            if key in me_json and isinstance(me_json[key], dict) and "id" in me_json[key]:
                has_id = True
                break
    assert has_id or email, "Profile response lacks identifying fields (id/email)"

    print("TC003 passed: GET /api/user/me returned authenticated user's profile with valid JWT token.")


if __name__ == "__main__":
    try:
        test_get_api_user_me_with_valid_token_TC003()
    except AssertionError as e:
        print(f"TC003 failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC003 encountered an unexpected error: {e}")
        sys.exit(2)
    sys.exit(0)