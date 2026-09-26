import requests
import uuid
import sys

BASE_URL = "http://localhost:8765"
TIMEOUT = 30.0
ADMIN_EMAIL = "admin@pixl.com"
ADMIN_PASSWORD = "admin123"


def _extract_token(json_data):
    # common token fields
    for key in ("accessToken", "token", "jwt", "access_token"):
        if isinstance(json_data, dict) and key in json_data:
            return json_data[key]
    # sometimes token is nested under data
    if isinstance(json_data, dict) and "data" in json_data and isinstance(json_data["data"], dict):
        for key in ("accessToken", "token", "jwt", "access_token", "accessToken"):
            if key in json_data["data"]:
                return json_data["data"][key]
    return None


def _extract_id(obj):
    if not isinstance(obj, dict):
        return None
    for key in ("id", "playlistId", "_id", "uuid", "id_str"):
        if key in obj:
            return obj[key]
    # sometimes resource returned as {"data": {...}}
    if "data" in obj and isinstance(obj["data"], dict):
        return _extract_id(obj["data"])
    return None


def test_post_api_admin_playlists_with_valid_payload():
    session = requests.Session()
    headers = {"Content-Type": "application/json"}

    # 1) Login as admin to get JWT
    login_url = f"{BASE_URL}/api/auth/login"
    login_payload = {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}
    try:
        r = session.post(login_url, json=login_payload, headers=headers, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Login request failed: {e}")
    if r.status_code != 200:
        raise AssertionError(f"Expected 200 from login, got {r.status_code}: {r.text}")
    try:
        login_json = r.json()
    except ValueError:
        raise AssertionError(f"Login response is not valid JSON: {r.text}")
    token = _extract_token(login_json)
    assert token, f"JWT token not found in login response: {login_json}"
    auth_headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    # 2) Get existing media to reference in playlist
    media_url = f"{BASE_URL}/api/admin/media"
    try:
        r = session.get(media_url, headers=auth_headers, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"GET /api/admin/media failed: {e}")
    if r.status_code != 200:
        raise AssertionError(f"Expected 200 from GET /api/admin/media, got {r.status_code}: {r.text}")
    try:
        media_json = r.json()
    except ValueError:
        raise AssertionError(f"Media list response is not valid JSON: {r.text}")

    # media_json may be a list or a dict with 'data' list
    media_list = []
    if isinstance(media_json, list):
        media_list = media_json
    elif isinstance(media_json, dict):
        if "data" in media_json and isinstance(media_json["data"], list):
            media_list = media_json["data"]
        elif "items" in media_json and isinstance(media_json["items"], list):
            media_list = media_json["items"]
        elif "media" in media_json and isinstance(media_json["media"], list):
            media_list = media_json["media"]
    if not media_list:
        raise AssertionError("No media items available to reference for playlist. Ensure media exists before running this test.")

    # extract a media id from the first item
    first_media = media_list[0]
    media_id = None
    if isinstance(first_media, dict):
        for k in ("id", "mediaId", "_id", "uuid"):
            if k in first_media:
                media_id = first_media[k]
                break
        # sometimes media item is nested under 'data'
        if not media_id and "data" in first_media and isinstance(first_media["data"], dict):
            for k in ("id", "mediaId", "_id", "uuid"):
                if k in first_media["data"]:
                    media_id = first_media["data"][k]
                    break
    if not media_id:
        raise AssertionError(f"Could not extract media id from media item: {first_media}")

    # 3) Create playlist
    playlists_url = f"{BASE_URL}/api/admin/playlists"
    playlist_name = f"Test Playlist {uuid.uuid4()}"
    # payload includes common shapes: mediaIds and items
    payload = {
        "name": playlist_name,
        "mediaIds": [media_id],
        "items": [
            {"mediaId": media_id, "duration": 10}
        ]
    }

    created_playlist_id = None
    try:
        try:
            r = session.post(playlists_url, json=payload, headers=auth_headers, timeout=TIMEOUT)
        except requests.RequestException as e:
            raise AssertionError(f"POST /api/admin/playlists request failed: {e}")

        if r.status_code not in (200, 201):
            # include response body for debugging
            raise AssertionError(f"Expected 200 or 201 from POST /api/admin/playlists, got {r.status_code}: {r.text}")

        try:
            playlist_json = r.json()
        except ValueError:
            raise AssertionError(f"Playlist creation response is not valid JSON: {r.text}")

        # attempt to extract created id and name
        created_playlist_id = _extract_id(playlist_json)
        # if API returns created resource under 'data'
        if not created_playlist_id and isinstance(playlist_json, dict) and "data" in playlist_json:
            created_playlist_id = _extract_id(playlist_json["data"])

        assert created_playlist_id, f"Created playlist id not found in response: {playlist_json}"

        # verify returned name matches
        returned_name = None
        if isinstance(playlist_json, dict):
            # common name fields
            for key in ("name", "title"):
                if key in playlist_json:
                    returned_name = playlist_json[key]
                    break
            if not returned_name and "data" in playlist_json and isinstance(playlist_json["data"], dict):
                for key in ("name", "title"):
                    if key in playlist_json["data"]:
                        returned_name = playlist_json["data"][key]
                        break
        assert returned_name == playlist_name, f"Returned playlist name '{returned_name}' does not match expected '{playlist_name}'"

    finally:
        # cleanup: delete created playlist if present
        if created_playlist_id:
            delete_url = f"{BASE_URL}/api/admin/playlists/{created_playlist_id}"
            try:
                dr = session.delete(delete_url, headers=auth_headers, timeout=TIMEOUT)
                # accept 200/202/204 as successful deletion
                if dr.status_code not in (200, 202, 204):
                    # Print warning but do not fail here to preserve original assertion errors if any
                    print(f"Warning: deleting playlist returned status {dr.status_code}: {dr.text}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"Warning: failed to delete playlist {created_playlist_id}: {e}", file=sys.stderr)


if __name__ == "__main__":
    test_post_api_admin_playlists_with_valid_payload()
    print("TC010 passed.")