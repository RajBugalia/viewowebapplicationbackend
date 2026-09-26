import requests
import sys

BASE_URL = "http://localhost:8765"
TIMEOUT = 30.0

ADMIN_EMAIL = "admin@pixl.com"
ADMIN_PASSWORD = "admin123"

def test_get_api_admin_media_with_admin_token():
    session = requests.Session()
    login_url = f"{BASE_URL.rstrip('/')}/api/auth/login"
    media_url = f"{BASE_URL.rstrip('/')}/api/admin/media"

    try:
        # 1) Authenticate as admin
        login_payload = {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}
        headers = {"Content-Type": "application/json", "Accept": "application/json"}

        try:
            resp = session.post(login_url, json=login_payload, headers=headers, timeout=TIMEOUT)
        except requests.exceptions.RequestException as e:
            raise AssertionError(f"Login request failed: {e}")

        assert resp is not None, "No response received from login endpoint"
        assert resp.status_code == 200, f"Expected 200 from login, got {resp.status_code}, body: {resp.text}"

        try:
            login_json = resp.json()
        except ValueError:
            raise AssertionError(f"Login response is not valid JSON: {resp.text}")

        # Extract JWT token from common fields
        token = None
        for key in ("accessToken", "access_token", "token", "jwt", "id_token"):
            if key in login_json and isinstance(login_json[key], str):
                token = login_json[key]
                break
        # Some APIs return nested object like { "data": { "token": "..." } }
        if not token:
            if isinstance(login_json.get("data"), dict):
                for key in ("accessToken", "access_token", "token", "jwt", "id_token"):
                    if key in login_json["data"] and isinstance(login_json["data"][key], str):
                        token = login_json["data"][key]
                        break

        assert token, f"JWT token not found in login response JSON: {login_json}"

        auth_headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}

        # 2) GET /api/admin/media with valid admin token (success case)
        try:
            media_resp = session.get(media_url, headers=auth_headers, timeout=TIMEOUT)
        except requests.exceptions.RequestException as e:
            raise AssertionError(f"GET /api/admin/media request failed: {e}")

        assert media_resp is not None, "No response received from media endpoint with token"
        assert media_resp.status_code == 200, f"Expected 200 from GET /api/admin/media, got {media_resp.status_code}, body: {media_resp.text}"

        try:
            media_json = media_resp.json()
        except ValueError:
            raise AssertionError(f"Media response is not valid JSON: {media_resp.text}")

        # Accept either a JSON list or an object containing media list under common keys
        if isinstance(media_json, list):
            # OK: list of media items
            assert True
        elif isinstance(media_json, dict):
            # Look for list under common container keys
            list_found = False
            for key in ("media", "items", "data", "content", "results"):
                if key in media_json and isinstance(media_json[key], list):
                    list_found = True
                    break
            # If dict itself represents a single media or metadata, still acceptable as long as JSON returned
            assert list_found or ("total" in media_json and isinstance(media_json.get("total"), int)) or ("count" in media_json and isinstance(media_json.get("count"), int)) or ("media" in media_json), \
                f"Media response JSON structure unexpected: {media_json}"
        else:
            raise AssertionError(f"Unexpected media response JSON type: {type(media_json)}")

        # 3) Negative case: request without token should be unauthorized (401) or forbidden (403)
        try:
            noauth_resp = session.get(media_url, headers={"Accept": "application/json"}, timeout=TIMEOUT)
        except requests.exceptions.RequestException as e:
            raise AssertionError(f"GET /api/admin/media (no auth) request failed: {e}")

        assert noauth_resp is not None, "No response received from media endpoint without token"
        assert noauth_resp.status_code in (401, 403), f"Expected 401/403 for unauthenticated request, got {noauth_resp.status_code}, body: {noauth_resp.text}"

        print("TC009 passed: GET /api/admin/media with admin token succeeded and unauthenticated access blocked as expected.")

    finally:
        session.close()

if __name__ == "__main__":
    try:
        test_get_api_admin_media_with_admin_token()
    except AssertionError as e:
        print(f"Test failed: {e}")
        sys.exit(1)
    except Exception as ex:
        print(f"Unexpected error during test execution: {ex}")
        sys.exit(2)
    sys.exit(0)