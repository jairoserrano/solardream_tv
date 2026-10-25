import os
import subprocess
import time
from PIL import Image

TV_IP = "192.168.88.3:5555"
DEST_DIR = "play_store_assets/screenshots"
os.makedirs(DEST_DIR, exist_ok=True)

def run_adb(cmd):
    full_cmd = f"adb -s {TV_IP} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res

def ensure_connected():
    print(f"Connecting to Android TV at {TV_IP}...")
    subprocess.run(f"adb connect {TV_IP}", shell=True, check=True)

def capture_scene(scene_num):
    print(f"\n--- Capturing Scene {scene_num} ---")
    run_adb("shell am force-stop dev.jairoserrano.solardream")
    time.sleep(0.5)
    run_adb(f"shell am start -n dev.jairoserrano.solardream/.MainActivity --ei demo_scene {scene_num}")
    
    if scene_num == 5:
        time.sleep(2.0) # Wait for settings dialog animation
    else:
        time.sleep(1.8) # Wait for canvas layout and initial animation frame
        
    remote_path = f"/sdcard/screen_{scene_num}.png"
    local_png = os.path.join(DEST_DIR, f"screenshot_tv_{scene_num}.png")
    local_jpg = os.path.join(DEST_DIR, f"screenshot_tv_{scene_num}.jpg")
    
    run_adb(f"shell screencap -p {remote_path}")
    pull_res = run_adb(f"pull {remote_path} {local_png}")
    print(f"Pulled: {local_png} ({pull_res.stdout.strip()})")
    
    # Dismiss dialog if scene 5
    if scene_num == 5:
        run_adb("shell input keyevent 4")
        
    # Convert/save high quality JPEG as well
    with Image.open(local_png) as img:
        rgb_img = img.convert("RGB")
        rgb_img.save(local_jpg, "JPEG", quality=95)
        print(f"Saved: {local_jpg}")

def main():
    ensure_connected()
    
    for i in range(1, 9):
        capture_scene(i)
        
    # Also update tv_screenshot_1920x1080.png / .jpg
    main_shot_png = os.path.join(DEST_DIR, "screenshot_tv_1.png")
    dest_main_png = "play_store_assets/tv_screenshot_1920x1080.png"
    dest_main_jpg = "play_store_assets/tv_screenshot_1920x1080.jpg"
    
    with Image.open(main_shot_png) as img:
        img.save(dest_main_png, "PNG", optimize=True)
        img.convert("RGB").save(dest_main_jpg, "JPEG", quality=95)
        print(f"\nUpdated {dest_main_png} & {dest_main_jpg}")
        
    # Return TV to normal live monitoring mode (scene 0)
    print("\nRestoring TV to live production mode...")
    run_adb("shell am force-stop dev.jairoserrano.solardream")
    time.sleep(0.5)
    run_adb("shell am start -n dev.jairoserrano.solardream/.MainActivity")
    print("Done! All 8 live TV screenshots captured and saved.")

if __name__ == "__main__":
    main()
