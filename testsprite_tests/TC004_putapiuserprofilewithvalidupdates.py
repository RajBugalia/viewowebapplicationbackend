import requests
import sys
import json

BASE_URL = "http://localhost:8765"
LOGIN_PATH = "/api/auth/login"
PROFILE_PATH = "/api/user/profile"
TIMEOUT = 30

MASTER_EMAIL = "master@pixl.com"
MASTER_PASSWORD = "master123"

def _obtain_jwt(email, password):
    url = BASE_URL.rstrip('/') + LOGIN_PATH
    payload = {"email": email, "password": password}
    headers = {"Content-Type": "application/json"}
    resp = requests.post(url, json=payload, headers=headers, timeout=TIMEOUT)
    try:
        resp_json = resp.json()
    except ValueError:
        resp_json = None

    assert resp.status_code == 200, f"Login failed: status {resp.status_code}, body: {resp.text}"
    if not resp_json:
        raise AssertionError(f"Login returned non-JSON response: {resp.text}")

    # Common token keys
    for key in ("accessToken", "access_token", "token", "jwt"):
        token = resp_json.get(key)
        if token:
            return token

    # Some APIs nest token under data or result
    for container in ("data", "result"):
        if container in resp_json and isinstance(resp_json[container], dict):
            for key in ("accessToken", "access_token", "token", "jwt"):
                token = resp_json[container].get(key)
                if token:
                    return token

    raise AssertionError(f"JWT token not found in login response: {json.dumps(resp_json)}")

def test_put_api_user_profile_with_valid_updates():
    # Authenticate to obtain JWT
    token = _obtain_jwt(MASTER_EMAIL, MASTER_PASSWORD)
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }

    # Prepare valid profile update payload
    update_payload = {
        "firstName": "TestMaster",
        "lastName": "User",
        "displayName": "Master Tester",
        "phone": "+15551234567",
        "company": "PixL Testing"
    }

    url = BASE_URL.rstrip('/') + PROFILE_PATH

    try:
        resp = requests.put(url, json=update_payload, headers=headers, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Request to update profile failed: {e}")

    # Expect 200 OK on successful update
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}. Body: {resp.text}"

    try:
        resp_json = resp.json()
    except ValueError:
        raise AssertionError(f"Response is not valid JSON: {resp.text}")

    # If API returns the updated user object, verify fields match
    mismatches = []
    for key, expected in update_payload.items():
        if key in resp_json:
            if resp_json.get(key) != expected:
                mismatches.append(f"Field '{key}' expected '{expected}' but got '{resp_json.get(key)}'")
        else:
            # Try alternative key styles (snake_case)
            alt_key = ''.join(['_'+c.lower() if c.isupper() else c for c in key]).lstrip('_')
            if alt_key in resp_json:
                if resp_json.get(alt_key) != expected:
                    mismatches.append(f"Field '{alt_key}' expected '{expected}' but got '{resp_json.get(alt_key)}'")
            else:
                # Not present directly; will check presence of expected value anywhere in response
                pass

    # If direct field checks produced mismatches, fail
    if mismatches:
        raise AssertionError("Profile update returned unexpected field values: " + "; ".join(mismatches))

    # As a fallback, ensure at least one of the updated values is visible somewhere in the response JSON
    resp_text = json.dumps(resp_json)
    found_any = any(str(v) in resp_text for v in update_payload.values())
    assert found_any, f"None of the updated values appear in response: {resp_text}"

    print("test_put_api_user_profile_with_valid_updates: PASSED")

if __name__ == "__main__":
    try:
        test_put_api_user_profile_with_valid_updates()
    except AssertionError as e:
        print("test_put_api_user_profile_with_valid_updates: FAILED")
        print(e)
        sys.exit(1)
    except Exception as ex:
        print("test_put_api_user_profile_with_valid_updates: ERROR")
        print(ex)
        sys.exit(2)
    sys.exit(0)