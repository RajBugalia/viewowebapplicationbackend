import requests
import sys

BASE_URL = "http://localhost:8765"
TIMEOUT = 30

def test_get_api_tv_sync_with_paired_device():
    session = requests.Session()
    pairing_endpoint = f"{BASE_URL.rstrip('/')}/api/tv/pair"
    sync_endpoint = f"{BASE_URL.rstrip('/')}/api/tv/sync"

    pairing_codes_to_try = ["123456", "000000", "111111", "999999", "654321"]
    payload_key_variants = ["pairing_code", "code", "pairingCode"]
    device_token = None
    screen_id = None
    pair_response_json = None

    try:
        # Attempt to pair with several common code/key variants until successful
        paired = False
        last_error = None
        for code in pairing_codes_to_try:
            for key in payload_key_variants:
                try:
                    resp = session.post(pairing_endpoint, json={key: code}, timeout=TIMEOUT)
                except requests.RequestException as e:
                    last_error = e
                    continue

                # Accept 200 as success. Also consider 201 as possible success.
                if resp.status_code in (200, 201):
                    try:
                        j = resp.json()
                    except ValueError:
                        # invalid json - treat as failure for this payload variant
                        last_error = ValueError("Pair response not JSON")
                        continue

                    # Try to extract device token and screen id using common keys
                    token_keys = ["token", "device_token", "deviceToken", "jwt", "access_token", "accessToken"]
                    id_keys = ["screen_id", "screenId", "id", "screenID"]

                    found_token = None
                    found_id = None
                    for tk in token_keys:
                        if tk in j and j[tk]:
                            found_token = j[tk]
                            break
                    for ik in id_keys:
                        if ik in j and j[ik]:
                            found_id = j[ik]
                            break

                    # Accept pairing even if only token present (screen id optional)
                    if found_token:
                        device_token = found_token
                        screen_id = found_id
                        pair_response_json = j
                        paired = True
                        break
                    else:
                        # It's possible the API returns token nested, try to search deeper
                        def deep_find_token(obj):
                            if isinstance(obj, dict):
                                for k, v in obj.items():
                                    if k in token_keys and v:
                                        return v
                                    result = deep_find_token(v)
                                    if result:
                                        return result
                            elif isinstance(obj, list):
                                for item in obj:
                                    result = deep_find_token(item)
                                    if result:
                                        return result
                            return None
                        found_token = deep_find_token(j)
                        if found_token:
                            device_token = found_token
                            screen_id = None
                            pair_response_json = j
                            paired = True
                            break

                else:
                    # store last error for reporting
                    try:
                        last_error = resp.json()
                    except Exception:
                        last_error = f"Status {resp.status_code}"
            if paired:
                break

        assert paired, f"Failed to pair device using tried codes/keys. Last error: {last_error}"

        assert device_token is not None and isinstance(device_token, str) and device_token.strip() != "", "Paired response did not include a device token"

        # Now call /api/tv/sync with the device token in Authorization header
        headers = {
            "Authorization": f"Bearer {device_token}",
            "Accept": "application/json"
        }
        sync_resp = session.get(sync_endpoint, headers=headers, timeout=TIMEOUT)
        # Allowing 200 as expected success. If 404 returned, it's a valid backend state but fails this test's expectation.
        assert sync_resp.status_code == 200, f"Expected 200 from /api/tv/sync for paired device, got {sync_resp.status_code}: {sync_resp.text}"

        try:
            sync_json = sync_resp.json()
        except ValueError:
            raise AssertionError("Response from /api/tv/sync is not valid JSON")

        # Validate presence of expected pieces: campaign, layout, zones, media URLs (be flexible on key names)
        def has_any_key(d, candidates):
            if not isinstance(d, dict):
                return False
            for c in candidates:
                if c in d and d[c] is not None:
                    return True
            return False

        campaign_keys = ["active_campaign", "activeCampaign", "campaign", "campaigns"]
        layout_keys = ["layout_mode", "layoutMode", "layout"]
        zones_keys = ["zones", "zone", "zones_list"]
        media_keys = ["media_urls", "mediaUrls", "media", "playlist", "assets"]

        has_campaign = has_any_key(sync_json, campaign_keys)
        has_layout = has_any_key(sync_json, layout_keys)
        has_zones = has_any_key(sync_json, zones_keys)
        has_media = has_any_key(sync_json, media_keys)

        # Some implementations may wrap data under a single key like "data" or "payload"
        # try to inspect nested dicts if top-level keys not present
        if not (has_campaign and has_layout and has_zones and has_media):
            # search one level deeper
            for v in list(sync_json.values()) if isinstance(sync_json, dict) else []:
                if isinstance(v, dict):
                    if not has_campaign:
                        has_campaign = has_any_key(v, campaign_keys)
                    if not has_layout:
                        has_layout = has_any_key(v, layout_keys)
                    if not has_zones:
                        has_zones = has_any_key(v, zones_keys)
                    if not has_media:
                        has_media = has_any_key(v, media_keys)
                if has_campaign and has_layout and has_zones and has_media:
                    break

        assert has_campaign, f"/api/tv/sync response missing campaign information. Response: {sync_json}"
        assert has_layout, f"/api/tv/sync response missing layout mode. Response: {sync_json}"
        assert has_zones, f"/api/tv/sync response missing zones definition. Response: {sync_json}"
        assert has_media, f"/api/tv/sync response missing media URLs or playlist. Response: {sync_json}"

        # Additional lightweight validation: zones should be a list if present
        zones_value = None
        for k in zones_keys:
            if k in sync_json:
                zones_value = sync_json[k]
                break
        if zones_value is None:
            # check nested
            for v in sync_json.values():
                if isinstance(v, dict):
                    for k in zones_keys:
                        if k in v:
                            zones_value = v[k]
                            break
                if zones_value is not None:
                    break

        if zones_value is not None:
            assert isinstance(zones_value, (list, dict)), "Zones value should be a list or dict describing zones"

        print("TC008 passed: /api/tv/sync returned campaign, layout, zones, and media information for a paired device.")

    finally:
        # Attempt best-effort cleanup/unpairing of created screen if possible.
        # The PRD does not define an unpair endpoint; try a few plausible ones and ignore failures.
        if screen_id:
            possible_unpair_endpoints = [
                f"{BASE_URL.rstrip('/')}/api/tv/unpair",
                f"{BASE_URL.rstrip('/')}/api/tv/pair/{screen_id}",
                f"{BASE_URL.rstrip('/')}/api/tv/unpair/{screen_id}",
                f"{BASE_URL.rstrip('/')}/api/admin/screens/{screen_id}"
            ]
            for ep in possible_unpair_endpoints:
                try:
                    # try POST unpair or DELETE depending on endpoint
                    # POST with token or id payload
                    if ep.endswith("/unpair") or "unpair" in ep:
                        try:
                            session.post(ep, json={"screen_id": screen_id}, timeout=10)
                        except Exception:
                            try:
                                session.post(ep, json={"id": screen_id}, timeout=10)
                            except Exception:
                                pass
                    elif ep.endswith(str(screen_id)) or ep.endswith(f"/{screen_id}"):
                        try:
                            session.delete(ep, timeout=10)
                        except Exception:
                            pass
                    else:
                        try:
                            session.delete(ep, timeout=10)
                        except Exception:
                            pass
                except Exception:
                    # ignore cleanup errors
                    pass

if __name__ == "__main__":
    try:
        test_get_api_tv_sync_with_paired_device()
    except AssertionError as e:
        print("TEST FAILED:", e)
        sys.exit(1)
    except Exception as exc:
        print("ERROR during test execution:", exc)
        sys.exit(2)
    else:
        sys.exit(0)