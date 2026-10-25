import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_base_icon(size=1024):
    img = Image.new("RGBA", (size, size), (8, 16, 28, 255))
    draw = ImageDraw.Draw(img)
    cx, cy = size // 2, size // 2

    # Background Radial Glow
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    for r in range(int(size * 0.48), 0, -6):
        alpha = int(35 * (1 - r / (size * 0.48)))
        glow_draw.ellipse(
            [cx - r, cy - r + int(size * 0.05), cx + r, cy + r + int(size * 0.05)],
            fill=(40, 90, 140, alpha)
        )
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Solar glow
    sun_cy = int(size * 0.40)
    sun_glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sun_glow_draw = ImageDraw.Draw(sun_glow)
    for r in range(int(size * 0.35), 0, -5):
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

    # Sun rays
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

    # Energy arcs
    arc_box = [cx - int(size * 0.38), sun_cy - int(size * 0.28), cx + int(size * 0.38), sun_cy + int(size * 0.28)]
    draw.arc(arc_box, start=200, end=340, fill=(105, 221, 181, 230), width=int(size * 0.012))
    draw.arc(arc_box, start=20, end=160, fill=(131, 201, 229, 230), width=int(size * 0.012))

    # Flow particles
    for ang_deg, color in [(220, (105, 221, 181, 255)), (280, (105, 221, 181, 255)), (330, (105, 221, 181, 255)),
                           (40, (131, 201, 229, 255)), (90, (131, 201, 229, 255)), (140, (131, 201, 229, 255))]:
        rad = math.radians(ang_deg)
        rx = int(size * 0.38)
        ry = int(size * 0.28)
        px = cx + rx * math.cos(rad)
        py = sun_cy + ry * math.sin(rad)
        pr = int(size * 0.02)
        draw.ellipse([px - pr, py - pr, px + pr, py + pr], fill=color)

    # House & Solar panels
    house_y = int(size * 0.58)
    roof_w = int(size * 0.32)
    roof_h = int(size * 0.16)
    wall_w = int(size * 0.24)
    wall_h = int(size * 0.18)

    # Roof
    roof_points = [
        (cx, house_y),
        (cx - roof_w, house_y + roof_h),
        (cx + roof_w, house_y + roof_h)
    ]
    draw.polygon(roof_points, fill=(28, 55, 78, 255), outline=(131, 201, 229, 240))

    # Solar panel
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

    # Window
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

    # Ground line
    ground_y = house_y + roof_h + wall_h + int(size * 0.01)
    draw.line([cx - int(size * 0.40), ground_y, cx + int(size * 0.40), ground_y],
              fill=(41, 65, 82, 220), width=int(size * 0.008))

    return img

def generate_play_tv_banner(width=1280, height=720):
    # Google Play Store Android TV Banner requirement:
    # 1280 x 720 px, 16:9 aspect ratio, 24-bit PNG or JPEG (no transparency)
    
    # Render at 2x (2560 x 1440) for supersampled razor-sharp quality
    w, h = width * 2, height * 2
    banner = Image.new("RGBA", (w, h), (5, 11, 20, 255))
    draw = ImageDraw.Draw(banner)

    # Ambient radial glows
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    for r in range(900, 0, -12):
        alpha = int(32 * (1 - r / 900))
        gdraw.ellipse([480 - r, h // 2 - r, 480 + r, h // 2 + r], fill=(235, 196, 119, alpha))
        gdraw.ellipse([w - 400 - r, h // 2 - r, w - 400 + r, h // 2 + r], fill=(44, 145, 192, alpha))
    banner = Image.alpha_composite(banner, glow)
    draw = ImageDraw.Draw(banner)

    # Subtle horizontal energy grid lines
    for y_line in [h // 4, 3 * h // 4]:
        draw.line([0, y_line, w, y_line], fill=(18, 38, 56, 100), width=2)

    # Render emblem on left
    emblem_size = int(h * 0.76)
    icon_master = create_base_icon(1024)
    emblem_scaled = icon_master.resize((emblem_size, emblem_size), Image.Resampling.LANCZOS)
    emblem_x = int(w * 0.07)
    emblem_y = (h - emblem_size) // 2
    banner.paste(emblem_scaled, (emblem_x, emblem_y), mask=emblem_scaled)

    # Typography
    try:
        font_brand = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 150)
        font_badge = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 46)
        font_tagline = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 56)
        font_sub = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 48)
        font_pill = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 38)
    except Exception:
        font_brand = ImageFont.load_default()
        font_badge = ImageFont.load_default()
        font_tagline = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        font_pill = ImageFont.load_default()

    text_x = emblem_x + emblem_size + int(w * 0.05)
    
    # Title
    title_y = int(h * 0.28)
    draw.text((text_x, title_y), "Solar Dream", font=font_brand, fill=(232, 240, 244, 255))
    
    # TV Badge
    badge_x = text_x + 880
    badge_y = title_y + 36
    draw.rounded_rectangle([badge_x, badge_y, badge_x + 140, badge_y + 74], radius=18, fill=(41, 65, 82, 255), outline=(105, 221, 181, 255), width=4)
    draw.text((badge_x + 36, badge_y + 12), "TV", font=font_badge, fill=(105, 221, 181, 255))

    # Tagline
    tagline_y = title_y + 180
    draw.text((text_x, tagline_y), "MONITOR SOLAR EN TIEMPO REAL", font=font_tagline, fill=(235, 196, 119, 255))
    
    # Subtitle
    sub_y = tagline_y + 80
    draw.text((text_x, sub_y), "Protector de pantalla y visualizador de energía para tu hogar", font=font_sub, fill=(134, 153, 171, 255))

    # Feature Pills
    pills = [
        ("EN VIVO · 100% LOCAL", (105, 221, 181)),
        ("HOYMILES DTU", (131, 201, 229)),
        ("SIN NUBE", (235, 196, 119))
    ]
    px = text_x
    py = sub_y + 90
    for text, color in pills:
        pill_w = 420 if "LOCAL" in text else (330 if "HOYMILES" in text else 240)
        draw.rounded_rectangle([px, py, px + pill_w, py + 72], radius=36, fill=(14, 28, 44, 255), outline=color, width=4)
        draw.text((px + 36, py + 14), text, font=font_pill, fill=color)
        px += pill_w + 30

    # Downscale to 1280 x 720 with high-quality Lanczos antialiasing
    final_banner = banner.resize((width, height), Image.Resampling.LANCZOS)
    
    # Ensure RGB (24-bit, NO transparency/alpha channel as strictly required by Google Play Console)
    final_rgb = Image.new("RGB", (width, height), (5, 11, 20))
    final_rgb.paste(final_banner, (0, 0))
    
    return final_rgb

def main():
    os.makedirs("play_store_assets", exist_ok=True)
    
    print("Generating Google Play Store Android TV Banner (1280x720)...")
    banner_1280 = generate_play_tv_banner(1280, 720)
    
    # Save as 24-bit PNG (no alpha)
    png_path = "play_store_assets/tv_banner_1280x720.png"
    banner_1280.save(png_path, "PNG", optimize=True)
    print(f"Saved: {png_path} ({os.path.getsize(png_path)} bytes)")

    # Save as high-quality JPEG as well
    jpg_path = "play_store_assets/tv_banner_1280x720.jpg"
    banner_1280.save(jpg_path, "JPEG", quality=95)
    print(f"Saved: {jpg_path} ({os.path.getsize(jpg_path)} bytes)")

    # Also keep standard 320x180 for manifest / launcher reference
    banner_320 = banner_1280.resize((320, 180), Image.Resampling.LANCZOS)
    png_320_path = "play_store_assets/tv_banner_320x180.png"
    banner_320.save(png_320_path, "PNG", optimize=True)
    print(f"Saved: {png_320_path} ({os.path.getsize(png_320_path)} bytes)")

    print("\nAll TV banner assets generated successfully!")

if __name__ == "__main__":
    main()
