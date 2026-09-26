import requests
import sys

BASE_URL = "http://localhost:8765"
TIMEOUT = 30.0

def test_get_admin_campaigns_with_admin_token():
    login_url = f"{BASE_URL}/api/auth/login"
    campaigns_url = f"{BASE_URL}/api/admin/campaigns"
    admin_credentials = {
        "email": "admin@pixl.com",
        "password": "admin123"
    }

    try:
        # Login as admin to obtain JWT
        resp = requests.post(login_url, json=admin_credentials, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Login request failed: {e}")

    assert resp is not None, "No response received from login endpoint"
    assert resp.status_code == 200, f"Expected 200 from login, got {resp.status_code}. Response body: {resp.text}"

    try:
        login_json = resp.json()
    except ValueError:
        raise AssertionError(f"Login response is not valid JSON: {resp.text}")

    # Attempt to extract token from common fields
    def extract_token(obj):
        if not isinstance(obj, dict):
            return None
        for key in ("token", "accessToken", "access_token", "jwt", "id_token", "access-token"):
            if key in obj and isinstance(obj[key], str) and obj[key].strip():
                return obj[key].strip()
        # check nested structures
        for v in obj.values():
            if isinstance(v, dict):
                for key in ("token", "accessToken", "access_token", "jwt", "id_token"):
                    if key in v and isinstance(v[key], str) and v[key].strip():
                        return v[key].strip()
        return None

    token = extract_token(login_json)
    assert token, f"JWT token not found in login response JSON: {login_json}"

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/json"
    }

    try:
        resp_campaigns = requests.get(campaigns_url, headers=headers, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"GET /api/admin/campaigns request failed: {e}")

    # Success case: expecting 200 OK with list or structured JSON containing campaigns
    assert resp_campaigns is not None, "No response received from /api/admin/campaigns"
    if resp_campaigns.status_code != 200:
        # Provide detailed failure information
        raise AssertionError(f"Expected 200 from GET /api/admin/campaigns, got {resp_campaigns.status_code}. Response body: {resp_campaigns.text}")

    try:
        campaigns_json = resp_campaigns.json()
    except ValueError:
        raise AssertionError(f"/api/admin/campaigns response is not valid JSON: {resp_campaigns.text}")

    # Validate payload shape: accept a direct list or a dict with a list under common keys
    valid = False
    if isinstance(campaigns_json, list):
        valid = True
        assert isinstance(campaigns_json, list)
    elif isinstance(campaigns_json, dict):
        list_keys = ("campaigns", "data", "items", "content", "results")
        for k in list_keys:
            if k in campaigns_json and isinstance(campaigns_json[k], list):
                valid = True
                break
        # It's acceptable for an empty result structure (e.g., dict with metadata). Consider that valid as long as it's JSON.
        if not valid:
            # If dict and contains at least keys indicating a campaigns response, accept it
            if any(k in campaigns_json for k in ("total", "page", "size", "meta")):
                valid = True

    assert valid, f"Unexpected /api/admin/campaigns response structure: {campaigns_json}"

    # If we've reached here, test passed
    print("TC005 passed: Retrieved admin campaigns successfully and response structure is valid.")

if __name__ == "__main__":
    try:
        test_get_admin_campaigns_with_admin_token()
    except AssertionError as e:
        print(f"TC005 failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC005 encountered an unexpected error: {e}")
        sys.exit(2)
    sys.exit(0)