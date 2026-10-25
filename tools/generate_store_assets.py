import os
import shutil
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_base_icon(size=2048):
    # Render at 4x supersampling (2048x2048)
    img = Image.new("RGBA", (size, size), (8, 16, 28, 255))
    draw = ImageDraw.Draw(img)

    cx, cy = size // 2, size // 2

    # Background Radial Glow
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    for r in range(int(size * 0.48), 0, -8):
        alpha = int(35 * (1 - r / (size * 0.48)))
        glow_draw.ellipse(
            [cx - r, cy - r + int(size * 0.05), cx + r, cy + r + int(size * 0.05)],
            fill=(40, 90, 140, alpha)
        )
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Solar glow in upper-middle
    sun_cy = int(size * 0.40)
    sun_glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sun_glow_draw = ImageDraw.Draw(sun_glow)
    for r in range(int(size * 0.35), 0, -6):
        alpha = int(45 * (1 - r / (size * 0.35)))
        sun_glow_draw.ellipse(
            [cx - r, sun_cy - r, cx + r, sun_cy + r],
            fill=(235, 196, 119, alpha)
        )
    img = Image.alpha_composite(img, sun_glow)
    draw = ImageDraw.Draw(img)

    # Sun core
    sun_r = int(size * 0.16)
    draw.ellipse(
        [cx - sun_r, sun_cy - sun_r, cx + sun_r, sun_cy + sun_r],
        fill=(245, 204, 120, 255),
        outline=(255, 230, 160, 255),
        width=int(size * 0.008)
    )

    # Sun rays (geometric elegant lines)
    num_rays = 12
    ray_inner = sun_r + int(size * 0.035)
    ray_outer = sun_r + int(size * 0.09)
    for i in range(num_rays):
        angle = i * (2 * math.pi / num_rays)
        x1 = cx + ray_inner * math.cos(angle)
        y1 = sun_cy + ray_inner * math.sin(angle)
        x2 = cx + ray_outer * math.cos(angle)
        y2 = sun_cy + ray_outer * math.sin(angle)
        draw.line([x1, y1, x2, y2], fill=(235, 196, 119, 210), width=int(size * 0.015))

    # Energy arcs (flowing orbits around the sun)
    arc_box = [cx - int(size * 0.38), sun_cy - int(size * 0.28), cx + int(size * 0.38), sun_cy + int(size * 0.28)]
    draw.arc(arc_box, start=200, end=340, fill=(105, 221, 181, 230), width=int(size * 0.012))
    draw.arc(arc_box, start=20, end=160, fill=(131, 201, 229, 230), width=int(size * 0.012))

    # Flow particles (dots)
    for ang_deg, color in [(220, (105, 221, 181, 255)), (280, (105, 221, 181, 255)), (330, (105, 221, 181, 255)),
                           (40, (131, 201, 229, 255)), (90, (131, 201, 229, 255)), (140, (131, 201, 229, 255))]:
        rad = math.radians(ang_deg)
        rx = int(size * 0.38)
        ry = int(size * 0.28)
        px = cx + rx * math.cos(rad)
        py = sun_cy + ry * math.sin(rad)
        pr = int(size * 0.02)
        draw.ellipse([px - pr, py - pr, px + pr, py + pr], fill=color)

    # House & Solar panels silhouette at bottom
    house_y = int(size * 0.58)
    roof_w = int(size * 0.32)
    roof_h = int(size * 0.16)
    wall_w = int(size * 0.24)
    wall_h = int(size * 0.18)

    # Roof triangle
    roof_points = [
        (cx, house_y),
        (cx - roof_w, house_y + roof_h),
        (cx + roof_w, house_y + roof_h)
    ]
    draw.polygon(roof_points, fill=(28, 55, 78, 255), outline=(131, 201, 229, 240))

    # Solar panel grid on left roof slope
    panel_pts = [
        (cx - int(roof_w * 0.15), house_y + int(roof_h * 0.2)),
        (cx - int(roof_w * 0.75), house_y + int(roof_h * 0.8)),
        (cx - int(roof_w * 0.55), house_y + int(roof_h * 0.95)),
        (cx - int(roof_w * 0.05), house_y + int(roof_h * 0.35)),
    ]
    draw.polygon(panel_pts, fill=(235, 196, 119, 230), outline=(255, 220, 140, 255))

    # House body
    body_box = [cx - wall_w, house_y + roof_h, cx + wall_w, house_y + roof_h + wall_h]
    draw.rectangle(body_box, fill=(18, 38, 56, 255), outline=(60, 105, 135, 200), width=int(size * 0.006))

    # Glowing window
    win_w = int(size * 0.06)
    win_h = int(size * 0.07)
    win_y = house_y + roof_h + int(wall_h * 0.25)
    draw.rounded_rectangle(
        [cx - win_w, win_y, cx + win_w, win_y + win_h],
        radius=int(size * 0.015),
        fill=(105, 221, 181, 240),
        outline=(200, 255, 230, 255),
        width=int(size * 0.005)
    )

    # Base line
    ground_y = house_y + roof_h + wall_h + int(size * 0.01)
    draw.line([cx - int(size * 0.40), ground_y, cx + int(size * 0.40), ground_y],
              fill=(41, 65, 82, 220), width=int(size * 0.008))

    return img

def create_round_icon(base_img):
    size = base_img.width
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([0, 0, size, size], fill=255)
    
    round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_img.paste(base_img, (0, 0), mask=mask)
    return round_img

def create_banner(icon_img):
    # TV Banner: 320 x 180 (Leanback TV Banner)
    # Render at 4x (1280 x 720) then downscale
    bw, bh = 1280, 720
    banner = Image.new("RGBA", (bw, bh), (5, 11, 20, 255))
    draw = ImageDraw.Draw(banner)

    # Subtle gradient glow across background
    glow = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    for r in range(400, 0, -8):
        alpha = int(30 * (1 - r / 400))
        glow_draw.ellipse([180 - r, bh // 2 - r, 180 + r, bh // 2 + r], fill=(235, 196, 119, alpha))
        glow_draw.ellipse([bw - 200 - r, bh // 2 - r, bw - 200 + r, bh // 2 + r], fill=(44, 145, 192, alpha))
    banner = Image.alpha_composite(banner, glow)
    draw = ImageDraw.Draw(banner)

    # Place resized icon on the left
    icon_scaled = icon_img.resize((480, 480), Image.Resampling.LANCZOS)
    banner.paste(icon_scaled, (60, (bh - 480) // 2), mask=icon_scaled)

    # Typography
    try:
        font_title = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 94)
        font_sub = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 40)
        font_badge = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 28)
    except Exception:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        font_badge = ImageFont.load_default()

    text_x = 560
    # Title
    draw.text((text_x, 210), "Solar Dream", font=font_title, fill=(232, 240, 244, 255))
    
    # TV Badge
    draw.rounded_rectangle([text_x + 560, 230, text_x + 650, 280], radius=12, fill=(41, 65, 82, 255))
    draw.text((text_x + 580, 238), "TV", font=font_badge, fill=(105, 221, 181, 255))

    # Subtitle
    draw.text((text_x, 330), "MONITOR SOLAR LOCAL · ANDROID TV", font=font_sub, fill=(134, 153, 171, 255))
    draw.text((text_x, 390), "Flujo de energía en tiempo real sin nube", font=font_sub, fill=(105, 221, 181, 230))

    # Downscale to 320 x 180
    return banner.resize((320, 180), Image.Resampling.LANCZOS)

def create_feature_graphic(icon_img):
    # Feature Graphic: 1024 x 500
    # Render at 2x (2048 x 1000) then downscale
    fw, fh = 2048, 1000
    feat = Image.new("RGBA", (fw, fh), (5, 11, 20, 255))
    draw = ImageDraw.Draw(feat)

    # Ambient radial glows
    glow = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    for r in range(650, 0, -10):
        alpha = int(35 * (1 - r / 650))
        gdraw.ellipse([450 - r, fh // 2 - r, 450 + r, fh // 2 + r], fill=(235, 196, 119, alpha))
        gdraw.ellipse([1550 - r, fh // 2 - r, 1550 + r, fh // 2 + r], fill=(44, 145, 192, alpha))
    feat = Image.alpha_composite(feat, glow)
    draw = ImageDraw.Draw(feat)

    # Icon on the left
    icon_scaled = icon_img.resize((660, 660), Image.Resampling.LANCZOS)
    feat.paste(icon_scaled, (180, (fh - 660) // 2), mask=icon_scaled)

    # Typography
    try:
        font_brand = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 110)
        font_tagline = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 52)
        font_pill = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 36)
    except Exception:
        font_brand = ImageFont.load_default()
        font_tagline = ImageFont.load_default()
        font_pill = ImageFont.load_default()

    tx = 920
    draw.text((tx, 270), "SolarDream TV", font=font_brand, fill=(232, 240, 244, 255))
    draw.text((tx, 410), "Protector de pantalla y monitor de energía", font=font_tagline, fill=(134, 153, 171, 255))
    draw.text((tx, 480), "Tiempo real · DTU Hoymiles local · Sin nube", font=font_tagline, fill=(105, 221, 181, 255))

    # Pill tags
    pills = [("ENERGÍA SOLAR", (235, 196, 119)), ("RED ELÉCTRICA", (235, 165, 117)), ("CONEXIÓN LOCAL", (105, 221, 181))]
    px = tx
    py = 580
    for text, color in pills:
        draw.rounded_rectangle([px, py, px + 280, py + 64], radius=32, fill=(14, 28, 44, 255), outline=color, width=3)
        draw.text((px + 28, py + 12), text, font=font_pill, fill=color)
        px += 305

    return feat.resize((1024, 500), Image.Resampling.LANCZOS)

def main():
    print("Generating master assets...")
    master = create_base_icon(2048)
    master_round = create_round_icon(master)

    # 1. 512x512 Store Icon
    icon_512 = master.resize((512, 512), Image.Resampling.LANCZOS)
    os.makedirs("play_store_assets", exist_ok=True)
    icon_512.save("play_store_assets/app_icon_512.png", "PNG")
    print("Saved play_store_assets/app_icon_512.png")

    # 2. TV Banner (320x180)
    banner_320 = create_banner(master)
    banner_320.save("play_store_assets/tv_banner_320x180.png", "PNG")
    
    # Save into android resources
    res_drawable = "app/src/main/res/drawable"
    os.makedirs(res_drawable, exist_ok=True)
    banner_320.save(os.path.join(res_drawable, "banner.png"), "PNG")
    print("Saved drawable/banner.png")

    # 3. Feature Graphic (1024x500)
    feat_1024 = create_feature_graphic(master)
    feat_1024.save("play_store_assets/feature_graphic_1024x500.png", "PNG")
    print("Saved play_store_assets/feature_graphic_1024x500.png")

    # 4. Android Mipmap densities
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, px in densities.items():
        dir_path = os.path.join("app/src/main/res", folder)
        os.makedirs(dir_path, exist_ok=True)
        
        ic = master.resize((px, px), Image.Resampling.LANCZOS)
        ic.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
        
        ic_round = master_round.resize((px, px), Image.Resampling.LANCZOS)
        ic_round.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")
        print(f"Saved {folder}/ic_launcher.png ({px}x{px})")

    # 5. Copy TV screenshots
    shots_dir = "play_store_assets/screenshots"
    os.makedirs(shots_dir, exist_ok=True)
    for i, shot in enumerate(["tv-dream.png", "tv-dream2.png", "tv-dream3.png"], 1):
        src = os.path.join("captures", shot)
        if os.path.exists(src):
            shutil.copy(src, os.path.join(shots_dir, f"screenshot_tv_{i}.png"))
            print(f"Copied {shot} -> {shots_dir}/screenshot_tv_{i}.png")

    print("\nMaster graphics generated successfully!")

if __name__ == "__main__":
    main()
