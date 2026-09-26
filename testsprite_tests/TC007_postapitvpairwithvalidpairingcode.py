import requests
import sys
import random
import string
import time

BASE_URL = "http://localhost:8765"
TIMEOUT = 30.0

def _extract_token_from_login_response(resp_json):
    # common token keys
    for key in ("token", "accessToken", "access_token", "jwt", "access_token"):
        if key in resp_json and isinstance(resp_json[key], str) and resp_json[key]:
            return resp_json[key]
    # nested data.token etc.
    if "data" in resp_json and isinstance(resp_json["data"], dict):
        for key in ("token", "accessToken", "access_token", "jwt"):
            if key in resp_json["data"] and isinstance(resp_json["data"][key], str):
                return resp_json["data"][key]
    return None

def _extract_pairing_code_from_resp(resp_json):
    for key in ("code", "pairingCode", "pairing_code"):
        if key in resp_json and isinstance(resp_json[key], str) and resp_json[key]:
            return resp_json[key]
    if "data" in resp_json and isinstance(resp_json["data"], dict):
        for key in ("code", "pairingCode", "pairing_code"):
            if key in resp_json["data"] and isinstance(resp_json["data"][key], str):
                return resp_json["data"][key]
    return None

def test_post_api_tv_pair_with_valid_pairing_code():
    admin_email = "admin@pixl.com"
    admin_password = "admin123"

    admin_token = None
    created_pairing_resource = None
    paired_screen_id = None

    try:
        # 1) Login as admin to obtain JWT (if needed to create pairing codes / cleanup)
        login_url = f"{BASE_URL.rstrip('/')}/api/auth/login"
        login_payload = {"email": admin_email, "password": admin_password}
        r = requests.post(login_url, json=login_payload, timeout=TIMEOUT)
        assert r.status_code == 200, f"Admin login failed with status {r.status_code}, body: {r.text}"
        login_json = r.json()
        admin_token = _extract_token_from_login_response(login_json)
        assert admin_token, f"Could not extract admin token from login response: {login_json}"

        headers_admin = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}

        # 2) Create a pairing code via a plausible admin endpoint to get a valid 6-digit code.
        # There is no canonical pairing-code creation endpoint in the PRD, try a common candidate.
        pairing_code = None
        candidate_endpoints = [
            "/api/admin/pairing-codes",
            "/api/admin/screens/pairing-codes",
            "/api/admin/screens/pair-code",
            "/api/admin/pair-code",
            "/api/admin/screens/pairing-code"
        ]
        for ep in candidate_endpoints:
            try:
                url = f"{BASE_URL.rstrip('/')}{ep}"
                payload = {"expiresMinutes": 15}
                r = requests.post(url, json=payload, headers=headers_admin, timeout=TIMEOUT)
            except requests.RequestException:
                continue
            if r.status_code in (200, 201):
                try:
                    j = r.json()
                except Exception:
                    continue
                pc = _extract_pairing_code_from_resp(j)
                if pc and pc.isdigit() and len(pc) == 6:
                    pairing_code = pc
                    created_pairing_resource = {"endpoint": ep, "response": j}
                    break
                # Sometimes response might include code under data or as int
                if "code" in j and isinstance(j["code"], int):
                    pc = str(j["code"])
                    if len(pc) == 6:
                        pairing_code = pc
                        created_pairing_resource = {"endpoint": ep, "response": j}
                        break
        # If no admin endpoint worked, fall back to a default test code (commonly used in dev)
        if not pairing_code:
            # generate a deterministic 6-digit code for testing
            pairing_code = "123456"

        # 3) Attempt pairing with the obtained pairing code
        pair_url = f"{BASE_URL.rstrip('/')}/api/tv/pair"
        pair_payload = {"pairingCode": pairing_code}
        r = requests.post(pair_url, json=pair_payload, timeout=TIMEOUT)
        # Accept 200 as success; if the default fallback was used and server rejects it, fail the test.
        assert r.status_code == 200, f"Pairing failed with status {r.status_code}, body: {r.text}"
        pair_json = r.json()
        # Expecting device JWT token and screen ID in response
        device_token = None
        screen_id = None
        for key in ("deviceJwt", "token", "accessToken", "device_token", "deviceToken"):
            if key in pair_json and isinstance(pair_json[key], str) and pair_json[key]:
                device_token = pair_json[key]
                break
        if not device_token and "data" in pair_json and isinstance(pair_json["data"], dict):
            for key in ("deviceJwt", "token", "accessToken", "device_token", "deviceToken"):
                if key in pair_json["data"] and isinstance(pair_json["data"][key], str):
                    device_token = pair_json["data"][key]
                    break
        for key in ("screenId", "id", "screen_id"):
            if key in pair_json and pair_json[key]:
                screen_id = str(pair_json[key])
                break
        if not screen_id and "data" in pair_json and isinstance(pair_json["data"], dict):
            for key in ("screenId", "id", "screen_id"):
                if key in pair_json["data"] and pair_json["data"][key]:
                    screen_id = str(pair_json["data"][key])
                    break

        assert device_token, f"Device token not found in pairing response: {pair_json}"
        assert screen_id, f"Screen ID not found in pairing response: {pair_json}"

        paired_screen_id = screen_id

        # Additional sanity checks
        assert isinstance(device_token, str) and len(device_token) > 10
        assert isinstance(paired_screen_id, str) and len(paired_screen_id) > 0

        print("Pairing successful. pairing_code:", pairing_code, "screen_id:", paired_screen_id)

    finally:
        # Cleanup: if we created a screen via pairing, attempt to delete it using admin token
        if paired_screen_id and admin_token:
            try:
                del_url = f"{BASE_URL.rstrip('/')}/api/admin/screens/{paired_screen_id}"
                headers = {"Authorization": f"Bearer {admin_token}"}
                r = requests.delete(del_url, headers=headers, timeout=TIMEOUT)
                # Accept 200, 204, or 202 as deletion success
                if r.status_code not in (200, 204, 202, 404):
                    print(f"Warning: unable to delete paired screen {paired_screen_id}, status {r.status_code}, body: {r.text}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"Warning: exception during cleanup deleting screen {paired_screen_id}: {e}", file=sys.stderr)

        # If we created a pairing-code resource via admin endpoint, attempt to remove it if possible
        if created_pairing_resource and admin_token:
            ep = created_pairing_resource.get("endpoint")
            # Try to parse an id from the response to delete specific pairing-code resource
            resp = created_pairing_resource.get("response", {})
            pairing_resource_id = None
            for key in ("id", "pairingId", "pairing_id"):
                if key in resp:
                    pairing_resource_id = resp[key]
                    break
            if pairing_resource_id:
                try:
                    url = f"{BASE_URL.rstrip('/')}{ep.rstrip('/')}/{pairing_resource_id}"
                    headers = {"Authorization": f"Bearer {admin_token}"}
                    r = requests.delete(url, headers=headers, timeout=TIMEOUT)
                    if r.status_code not in (200, 204, 202, 404):
                        print(f"Warning: unable to delete pairing resource {pairing_resource_id} at {ep}, status {r.status_code}", file=sys.stderr)
                except requests.RequestException as e:
                    print(f"Warning: exception during cleanup deleting pairing resource: {e}", file=sys.stderr)

if __name__ == "__main__":
    test_post_api_tv_pair_with_valid_pairing_code()