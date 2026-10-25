import os
import json
import time
import jwt
import requests

PKG_NAME = "dev.jairoserrano.solardream"
KEY_FILE = "play_service_account.json"
SCREENSHOTS_DIR = "play_store_assets/screenshots"
LANGUAGE = "es-419"

def get_access_token():
    with open(KEY_FILE) as f:
        key_data = json.load(f)

    now = int(time.time())
    payload = {
        "iss": key_data["client_email"],
        "scope": "https://www.googleapis.com/auth/androidpublisher",
        "aud": key_data["token_uri"],
        "exp": now + 3600,
        "iat": now,
    }
    signed_jwt = jwt.encode(payload, key_data["private_key"], algorithm="RS256")
    res = requests.post(key_data["token_uri"], data={
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": signed_jwt,
    })
    res.raise_for_status()
    return res.json()["access_token"]

def main():
    token = get_access_token()
    headers = {"Authorization": f"Bearer {token}"}
    
    # 1. Create a new edit
    print("Creating new Google Play edit...")
    edit_res = requests.post(
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG_NAME}/edits",
        headers=headers,
    )
    edit_res.raise_for_status()
    edit_id = edit_res.json()["id"]
    print(f"Edit created with ID: {edit_id}")

    # 2. Clear old screenshots
    print(f"Clearing existing tvScreenshots for {LANGUAGE}...")
    del_res = requests.delete(
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG_NAME}/edits/{edit_id}/listings/{LANGUAGE}/tvScreenshots",
        headers=headers,
    )
    print(f"Clear status: {del_res.status_code}")

    # 3. Upload all 8 screenshots
    upload_base_url = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG_NAME}/edits/{edit_id}/listings/{LANGUAGE}/tvScreenshots?uploadType=media"
    
    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {token}"})

    for i in range(1, 9):
        file_path = os.path.join(SCREENSHOTS_DIR, f"screenshot_tv_{i}.png")
        print(f"Uploading {file_path} to Google Play...")
        with open(file_path, "rb") as f:
            img_bytes = f.read()
        
        # Retry loop for upload
        max_retries = 3
        for attempt in range(max_retries):
            try:
                up_res = session.post(
                    upload_base_url,
                    headers={"Content-Type": "image/png"},
                    data=img_bytes,
                    timeout=30
                )
                if up_res.status_code == 200:
                    img_info = up_res.json().get("image", {})
                    print(f"Successfully uploaded screenshot {i}! ID: {img_info.get('id')}")
                    break
                else:
                    print(f"Attempt {attempt+1} failed ({up_res.status_code}): {up_res.text}")
                    time.sleep(2)
            except Exception as e:
                print(f"Attempt {attempt+1} exception: {e}")
                time.sleep(2)
                if attempt == max_retries - 1:
                    raise

    # 4. Commit the edit
    print("Committing edit to Google Play Console...")
    commit_res = session.post(
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG_NAME}/edits/{edit_id}:commit",
        timeout=30
    )
    if commit_res.status_code != 200:
        print(f"Failed to commit edit: {commit_res.status_code} - {commit_res.text}")
        commit_res.raise_for_status()
        
    print(f"SUCCESS! All 8 live TV screenshots are published to Google Play Store listing ({LANGUAGE})!")

if __name__ == "__main__":
    main()
