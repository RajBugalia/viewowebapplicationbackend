import requests
import uuid
import sys

BASE_URL = "http://localhost:8765"
TIMEOUT = 30


def _extract_token(json_data):
    # common token keys
    for key in ("token", "accessToken", "access_token", "jwt", "id_token"):
        if isinstance(json_data, dict) and key in json_data and json_data[key]:
            return json_data[key]
    # sometimes nested under 'data'
    if isinstance(json_data, dict) and "data" in json_data and isinstance(json_data["data"], dict):
        for key in ("token", "accessToken", "access_token", "jwt", "id_token"):
            if key in json_data["data"] and json_data["data"][key]:
                return json_data["data"][key]
    return None


def test_post_api_auth_register_with_valid_details():
    email = f"testuser+{uuid.uuid4().hex[:8]}@example.com"
    password = "TestPass!23"
    register_payload = {
        "email": email,
        "password": password,
        "firstName": "Test",
        "lastName": "User"
    }
    headers = {"Content-Type": "application/json"}

    created_user_id = None
    user_token = None

    try:
        # 1) Register new user
        try:
            resp = requests.post(
                f"{BASE_URL}/api/auth/register",
                json=register_payload,
                headers=headers,
                timeout=TIMEOUT,
            )
        except requests.RequestException as e:
            raise AssertionError(f"Request to register endpoint failed: {e}")

        assert resp is not None, "No response received from register endpoint"
        assert resp.status_code in (200, 201), f"Expected 200 or 201, got {resp.status_code}; body: {resp.text}"

        try:
            reg_json = resp.json()
        except ValueError:
            raise AssertionError(f"Register response is not JSON: {resp.text}")

        # If API returns created user id or email, validate
        if isinstance(reg_json, dict):
            # check email echo or id
            reg_email = reg_json.get("email") or (reg_json.get("data", {}) or {}).get("email")
            if reg_email:
                assert reg_email.lower() == email.lower(), f"Registered email mismatch: expected {email}, got {reg_email}"
            created_user_id = reg_json.get("id") or (reg_json.get("data", {}) or {}).get("id")

        # 2) Login with the new credentials to ensure account usable
        try:
            login_resp = requests.post(
                f"{BASE_URL}/api/auth/login",
                json={"email": email, "password": password},
                headers=headers,
                timeout=TIMEOUT,
            )
        except requests.RequestException as e:
            raise AssertionError(f"Request to login endpoint failed: {e}")

        assert login_resp is not None, "No response received from login endpoint"
        assert login_resp.status_code == 200, f"Expected 200 on login, got {login_resp.status_code}; body: {login_resp.text}"

        try:
            login_json = login_resp.json()
        except ValueError:
            raise AssertionError(f"Login response is not JSON: {login_resp.text}")

        token = _extract_token(login_json)
        assert token, f"No JWT token found in login response: {login_json}"
        user_token = token

        # 3) GET /api/user/me to confirm profile and obtain user id if not returned earlier
        try:
            me_resp = requests.get(
                f"{BASE_URL}/api/user/me",
                headers={"Authorization": f"Bearer {user_token}"},
                timeout=TIMEOUT,
            )
        except requests.RequestException as e:
            raise AssertionError(f"Request to /api/user/me failed: {e}")

        assert me_resp is not None, "No response from /api/user/me"
        assert me_resp.status_code == 200, f"Expected 200 from /api/user/me, got {me_resp.status_code}; body: {me_resp.text}"

        try:
            me_json = me_resp.json()
        except ValueError:
            raise AssertionError(f"/api/user/me response is not JSON: {me_resp.text}")

        me_email = me_json.get("email") or (me_json.get("data", {}) or {}).get("email")
        assert me_email and me_email.lower() == email.lower(), f"/api/user/me returned unexpected email: {me_email}"

        if not created_user_id:
            created_user_id = me_json.get("id") or (me_json.get("data", {}) or {}).get("id")

        # Final assertion that we have at least an identifier or email confirmed
        assert created_user_id or me_email, "Could not determine created user id or email after registration/login"

        print(f"User registered and verified successfully: email={email}, id={created_user_id}")

    finally:
        # Cleanup: attempt to delete the created user using admin privileges if we have an id.
        if created_user_id:
            try:
                # login as admin
                try:
                    admin_login = requests.post(
                        f"{BASE_URL}/api/auth/login",
                        json={"email": "admin@pixl.com", "password": "admin123"},
                        headers=headers,
                        timeout=TIMEOUT,
                    )
                except requests.RequestException as e:
                    print(f"Admin login request failed during cleanup: {e}", file=sys.stderr)
                    return

                if admin_login is None or admin_login.status_code != 200:
                    print(f"Admin login failed during cleanup: status={getattr(admin_login, 'status_code', None)}, body={getattr(admin_login, 'text', None)}", file=sys.stderr)
                    return

                try:
                    admin_json = admin_login.json()
                except ValueError:
                    print(f"Admin login response not JSON: {admin_login.text}", file=sys.stderr)
                    return

                admin_token = _extract_token(admin_json)
                if not admin_token:
                    print(f"No admin token available for cleanup, admin login response: {admin_json}", file=sys.stderr)
                    return

                # Try several possible delete endpoints that may be used by the backend
                delete_paths = [
                    f"/api/admin/users/{created_user_id}",
                    f"/api/admin/user/{created_user_id}",
                    f"/api/users/{created_user_id}",
                    f"/api/user/{created_user_id}",
                ]
                deleted = False
                for path in delete_paths:
                    try:
                        del_resp = requests.delete(
                            f"{BASE_URL}{path}",
                            headers={"Authorization": f"Bearer {admin_token}"},
                            timeout=TIMEOUT,
                        )
                    except requests.RequestException as e:
                        print(f"Delete request to {path} failed: {e}", file=sys.stderr)
                        continue

                    if del_resp.status_code in (200, 204):
                        print(f"Cleanup: successfully deleted user via {path}")
                        deleted = True
                        break
                    else:
                        # log and try next path
                        print(f"Cleanup: delete via {path} returned status {del_resp.status_code}; body: {del_resp.text}", file=sys.stderr)

                if not deleted:
                    print("Cleanup: could not delete created user - endpoint may not exist or user may require different cleanup.", file=sys.stderr)

            except Exception as e:
                print(f"Unexpected error during cleanup: {e}", file=sys.stderr)


if __name__ == "__main__":
    test_post_api_auth_register_with_valid_details()