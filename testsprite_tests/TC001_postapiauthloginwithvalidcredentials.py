import requests
import json
import sys

BASE_URL = "http://localhost:8765"
TIMEOUT = 30

def test_post_api_auth_login_with_valid_credentials_TC001():
    """
    Test POST /api/auth/login with valid admin credentials to verify successful authentication
    and receipt of a JWT token.
    """
    url = f"{BASE_URL.rstrip('/')}/api/auth/login"
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    payload = {
        "email": "admin@pixl.com",
        "password": "admin123"
    }

    try:
        resp = requests.post(url, headers=headers, data=json.dumps(payload), timeout=TIMEOUT)
    except requests.exceptions.RequestException as e:
        raise AssertionError(f"HTTP request to {url} failed: {e}")

    # Basic HTTP status assertion
    assert resp is not None, "No response received from the server"
    assert resp.status_code == 200, f"Expected status code 200, got {resp.status_code}. Response text: {resp.text}"

    # Parse JSON body
    try:
        body = resp.json()
    except ValueError:
        raise AssertionError(f"Response is not valid JSON. Response text: {resp.text}")

    # Check for common JWT token keys
    possible_keys = ["token", "accessToken", "access_token", "jwt", "idToken", "access-token"]
    found_key = None
    for k in possible_keys:
        if k in body and isinstance(body[k], str) and body[k].strip():
            found_key = (k, body[k].strip())
            break

    assert found_key is not None, f"No JWT token found in response JSON. Keys present: {list(body.keys())}"

    token_value = found_key[1]
    # Basic JWT structure check: three '.'-separated parts
    parts = token_value.split(".")
    assert len(parts) == 3 and all(part.strip() for part in parts), "Token does not appear to be a JWT (expected three dot-separated parts)"

    # If we reach here, test passed
    print(f"TC001 passed: received JWT in key '{found_key[0]}'")

if __name__ == "__main__":
    try:
        test_post_api_auth_login_with_valid_credentials_TC001()
    except AssertionError as e:
        print(f"TC001 failed: {e}")
        sys.exit(1)
    except Exception as ex:
        print(f"TC001 encountered an unexpected error: {ex}")
        sys.exit(2)
    sys.exit(0)