import requests
import datetime
import time
import sys
import traceback

BASE_URL = "http://localhost:8765"
LOGIN_PATH = "/api/auth/login"
CAMPAIGNS_PATH = "/api/admin/campaigns"
TIMEOUT = 30

ADMIN_EMAIL = "admin@pixl.com"
ADMIN_PASSWORD = "admin123"

def test_post_api_admin_campaigns_with_valid_payload():
    session = requests.Session()
    token = None
    campaign_id = None

    try:
        # 1) Authenticate as admin to obtain JWT
        login_url = BASE_URL.rstrip("/") + LOGIN_PATH
        login_payload = {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}
        headers = {"Accept": "application/json", "Content-Type": "application/json"}

        try:
            r = session.post(login_url, json=login_payload, headers=headers, timeout=TIMEOUT)
        except requests.RequestException as e:
            raise AssertionError(f"Login request failed: {e}")

        assert r.status_code == 200, f"Expected 200 for login, got {r.status_code}, body: {r.text}"
        try:
            login_json = r.json()
        except ValueError:
            raise AssertionError(f"Login response is not JSON: {r.text}")

        # Extract token from common fields
        token = (
            login_json.get("token")
            or login_json.get("accessToken")
            or login_json.get("access_token")
            or login_json.get("jwt")
            or (login_json.get("data") and login_json["data"].get("token"))
            or (login_json.get("data") and login_json["data"].get("accessToken"))
        )
        assert token, f"JWT token not found in login response: {login_json}"

        auth_headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        }

        # 2) Prepare a valid campaign payload
        now = datetime.datetime.utcnow()
        start_at = now.isoformat() + "Z"
        end_at = (now + datetime.timedelta(days=7)).isoformat() + "Z"
        unique_suffix = str(int(time.time()))
        campaign_payload = {
            # Minimal, commonly-accepted fields for campaign creation.
            "name": f"Automated Test Campaign {unique_suffix}",
            "description": "Created by automated test case TC006",
            "startAt": start_at,
            "endAt": end_at,
            # Typical fields that backends might accept; if some are ignored it's fine.
            "active": True,
            "type": "single",      # single or multiscreen - backend may accept
            "playlists": [],       # empty list is acceptable if optional
            "metadata": {"source": "test-suite", "tc": "TC006"}
        }

        # 3) Create campaign
        create_url = BASE_URL.rstrip("/") + CAMPAIGNS_PATH
        try:
            resp = session.post(create_url, json=campaign_payload, headers=auth_headers, timeout=TIMEOUT)
        except requests.RequestException as e:
            raise AssertionError(f"Campaign creation request failed: {e}")

        assert resp.status_code in (200, 201), f"Expected 200/201 for campaign creation, got {resp.status_code}, body: {resp.text}"

        try:
            resp_json = resp.json()
        except ValueError:
            raise AssertionError(f"Campaign creation response is not JSON: {resp.text}")

        # 4) Extract created campaign ID from common structures
        campaign_id = (
            resp_json.get("id")
            or resp_json.get("campaign", {}).get("id")
            or resp_json.get("data", {}).get("id")
            or (resp_json.get("data") and resp_json["data"].get("campaign") and resp_json["data"]["campaign"].get("id"))
        )
        assert campaign_id, f"Created campaign ID not found in response: {resp_json}"

        # Additional sanity checks on returned payload
        returned_name = (
            resp_json.get("name")
            or resp_json.get("campaign", {}).get("name")
            or (resp_json.get("data") and resp_json["data"].get("name"))
            or None
        )
        assert returned_name is not None, "Created campaign returned payload missing 'name'"
        assert returned_name == campaign_payload["name"] or campaign_payload["name"] in returned_name, "Returned campaign name does not match requested name"

        print(f"Campaign created successfully with ID: {campaign_id}")

    finally:
        # 5) Clean up: delete the created campaign if present
        if token and campaign_id:
            delete_url = BASE_URL.rstrip("/") + CAMPAIGNS_PATH.rstrip("/") + f"/{campaign_id}"
            delete_headers = {
                "Authorization": f"Bearer {token}",
                "Accept": "application/json"
            }
            try:
                del_resp = session.delete(delete_url, headers=delete_headers, timeout=TIMEOUT)
                assert del_resp.status_code in (200, 204), f"Expected 200/204 for campaign deletion, got {del_resp.status_code}, body: {del_resp.text}"
                print(f"Campaign with ID {campaign_id} deleted successfully.")
            except requests.RequestException as e:
                print(f"Campaign deletion request failed: {e}", file=sys.stderr)
            except AssertionError as ae:
                print(f"Assertion during cleanup: {ae}", file=sys.stderr)

if __name__ == "__main__":
    try:
        test_post_api_admin_campaigns_with_valid_payload()
        print("TC006 passed.")
    except AssertionError as e:
        print("TC006 failed:", e)
        traceback.print_exc()
        sys.exit(1)
    except Exception as exc:
        print("TC006 encountered an unexpected error:", exc)
        traceback.print_exc()
        sys.exit(2)