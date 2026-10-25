import os
import math
from PIL import Image, ImageDraw, ImageFont

def render_tv_scene(
    filename,
    solar_kw="3.85 kW",
    home_kw="1.20 kW",
    grid_kw="2.65 kW",
    solar_state="GENERANDO ENERGÍA",
    grid_state="EXPORTANDO EXCEDENTE",
    grid_mode="EXPORT", # "EXPORT", "IMPORT", "IDLE", "UNKNOWN"
    today_kwh="18.4 kWh",
    hero_title="Tu hogar comparte energía",
    hero_sub="Red (exporta) · 2.65 kW",
    time_str="13:42",
    status_text="EN VIVO · DTU LOCAL",
    status_online=True,
    is_english=False,
    show_dialog=False
):
    W, H = 1920, 1080
    img = Image.new("RGB", (W, H), (5, 11, 20))
    draw = ImageDraw.Draw(img)

    # Fonts
    try:
        font_light_huge = ImageFont.truetype(r"C:\Windows\Fonts\segoeuil.ttf", 92)
        font_light_big = ImageFont.truetype(r"C:\Windows\Fonts\segoeuil.ttf", 70)
        font_light_mid = ImageFont.truetype(r"C:\Windows\Fonts\segoeuil.ttf", 42)
        font_light_small = ImageFont.truetype(r"C:\Windows\Fonts\segoeuil.ttf", 32)
        font_bold_title = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 28)
        font_bold_mid = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 22)
        font_bold_small = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 17)
        font_reg_mid = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 24)
        font_reg_small = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 18)
    except Exception:
        font_light_huge = ImageFont.load_default()
        font_light_big = ImageFont.load_default()
        font_light_mid = ImageFont.load_default()
        font_light_small = ImageFont.load_default()
        font_bold_title = ImageFont.load_default()
        font_bold_mid = ImageFont.load_default()
        font_bold_small = ImageFont.load_default()
        font_reg_mid = ImageFont.load_default()
        font_reg_small = ImageFont.load_default()

    # Colors
    SOLAR = (235, 196, 119)
    BLUE = (131, 201, 229)
    MINT = (105, 221, 181)
    AMBER = (235, 165, 117)
    SURFACE = (11, 24, 37)
    BORDER = (41, 65, 82)
    TEXT = (232, 240, 244)
    MUTED = (134, 153, 171)

    grid_color = MINT if grid_mode == "EXPORT" else (AMBER if grid_mode == "IMPORT" else MUTED)
    live_alpha = 255 if status_online else 120

    # Ambient halos
    halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hdraw = ImageDraw.Draw(halo)
    
    # House halo
    for r in range(380, 0, -8):
        alpha = int(35 * (1 - r / 380))
        hdraw.ellipse([960 - r, 497 - r, 960 + r, 497 + r], fill=(44, 145, 192, alpha))
        
    # Solar halo
    if "0.00" not in solar_kw and status_online:
        for r in range(240, 0, -6):
            alpha = int(32 * (1 - r / 240))
            hdraw.ellipse([384 - r, 504 - r, 384 + r, 504 + r], fill=(235, 196, 119, alpha))
            
    # Grid halo
    if grid_mode != "IDLE" and status_online:
        g_fill = (79, 224, 177) if grid_mode == "EXPORT" else (245, 170, 112)
        for r in range(240, 0, -6):
            alpha = int(32 * (1 - r / 240))
            hdraw.ellipse([1536 - r, 504 - r, 1536 + r, 504 + r], fill=(g_fill[0], g_fill[1], g_fill[2], alpha))

    img.paste(Image.alpha_composite(Image.new("RGBA", (W, H), (5, 11, 20, 255)), halo).convert("RGB"), (0, 0))
    draw = ImageDraw.Draw(img)

    # 1. Top bar
    # Sun icon left
    sun_x, sun_y = 130, 92
    draw.ellipse([sun_x - 10, sun_y - 10, sun_x + 10, sun_y + 10], fill=SOLAR)
    for i in range(8):
        a = i * math.pi / 4
        draw.line([sun_x + 14 * math.cos(a), sun_y + 14 * math.sin(a),
                   sun_x + 20 * math.cos(a), sun_y + 20 * math.sin(a)], fill=SOLAR, width=2)
    draw.text((172, 78), "SOLAR DREAM", font=font_bold_title, fill=TEXT)
    tagline = "Your home's energy" if is_english else "La energía de tu hogar"
    draw.text((115, 132), tagline, font=font_reg_mid, fill=MUTED)

    # Status Pill
    status_bg = (14, 38, 30) if status_online else (38, 26, 18)
    status_fg = MINT if status_online else AMBER
    draw.rounded_rectangle([1332, 73, 1588, 120], radius=22, fill=status_bg, outline=status_fg, width=2)
    draw.ellipse([1355, 92, 1365, 102], fill=status_fg)
    draw.text((1378, 86), status_text, font=font_bold_small, fill=status_fg)

    # Clock right
    draw.text((1805, 68), time_str, font=font_light_mid, fill=TEXT, anchor="ra")
    last_read_lbl = "LAST READING" if is_english else "ÚLTIMA LECTURA"
    draw.text((1805, 128), last_read_lbl, font=font_bold_small, fill=MUTED, anchor="ra")

    # 2. Main Nodes
    # Left: Solar (cx=384, cy=504)
    # Center: House (cx=960, cy=497)
    # Right: Grid (cx=1536, cy=504)

    # Draw Nodes Orbits
    for cx, c_col in [(384, SOLAR), (1536, grid_color)]:
        draw.ellipse([cx - 115, 504 - 115, cx + 115, 504 + 115], fill=SURFACE, outline=BORDER, width=2)
        draw.ellipse([cx - 126, 504 - 126, cx + 126, 504 + 126], outline=(c_col[0], c_col[1], c_col[2]), width=2)

    # House Center Node (Hex/Rounded Box)
    draw.rounded_rectangle([960 - 150, 497 - 140, 960 + 150, 497 + 140], radius=32, fill=SURFACE, outline=BLUE, width=3)

    # Node Titles
    lbl_solar = "SOLAR PRODUCTION" if is_english else "PRODUCCIÓN SOLAR"
    lbl_house = "HOME CONSUMPTION" if is_english else "CONSUMO DEL HOGAR"
    lbl_grid = "POWER GRID" if is_english else "RED ELÉCTRICA"

    draw.text((384, 310), lbl_solar, font=font_bold_mid, fill=MUTED, anchor="ma")
    draw.text((960, 270), lbl_house, font=font_bold_mid, fill=BLUE, anchor="ma")
    draw.text((1536, 310), lbl_grid, font=font_bold_mid, fill=MUTED, anchor="ma")

    # Draw Flow Lines & Arrows
    # Solar to House
    if "0.00" not in solar_kw and status_online:
        draw.line([512, 504, 810, 504], fill=SOLAR, width=4)
        for px in [580, 660, 740]:
            draw.ellipse([px - 4, 500, px + 4, 508], fill=SOLAR)
        # Arrow label
        flow_to_home = "TO HOME" if is_english else "HACIA TU HOGAR"
        draw.text((660, 465), flow_to_home, font=font_bold_small, fill=SOLAR, anchor="ma")

    # House to/from Grid
    if status_online and grid_mode != "IDLE":
        draw.line([1110, 504, 1408, 504], fill=grid_color, width=4)
        for px in [1180, 1260, 1340]:
            draw.ellipse([px - 4, 500, px + 4, 508], fill=grid_color)
        flow_lbl = ("TO GRID" if is_english else "HACIA LA RED") if grid_mode == "EXPORT" else ("TO HOME" if is_english else "HACIA TU HOGAR")
        draw.text((1260, 465), flow_lbl, font=font_bold_small, fill=grid_color, anchor="ma")

    # Metrics Values
    draw.text((384, 715), solar_kw, font=font_light_big, fill=TEXT, anchor="ma")
    draw.text((960, 725), home_kw, font=font_light_huge, fill=TEXT, anchor="ma")
    draw.text((1536, 715), grid_kw, font=font_light_big, fill=TEXT, anchor="ma")

    # Subtitles below metrics
    draw.text((384, 785), solar_state, font=font_bold_small, fill=SOLAR if status_online else MUTED, anchor="ma")
    inst_lbl = "INSTANT POWER" if is_english else "POTENCIA INSTANTÁNEA"
    if not status_online:
        inst_lbl = "LAST READING" if is_english else "ÚLTIMA LECTURA"
    draw.text((960, 810), inst_lbl, font=font_reg_small, fill=MUTED, anchor="ma")
    draw.text((1536, 785), grid_state, font=font_bold_small, fill=grid_color if status_online else MUTED, anchor="ma")

    # 3. Bottom Hero Card
    draw.rounded_rectangle([115, 895, 1805, 1010], radius=24, fill=SURFACE, outline=BORDER, width=2)
    
    # Left solar badge
    draw.rounded_rectangle([140, 915, 205, 990], radius=16, fill=(35, 28, 14), outline=SOLAR, width=2)
    draw.text((172, 938), "⚡", font=font_reg_mid, fill=SOLAR, anchor="mm")

    lbl_today = "SOLAR ENERGY · TODAY" if is_english else "ENERGÍA SOLAR · HOY"
    draw.text((225, 922), lbl_today, font=font_bold_small, fill=MUTED)
    draw.text((225, 955), today_kwh, font=font_light_small, fill=TEXT)

    # Divider
    draw.line([560, 920, 560, 985], fill=BORDER, width=2)

    # Center hero text
    draw.text((600, 925), hero_title, font=font_bold_mid, fill=TEXT)
    draw.text((600, 965), hero_sub, font=font_reg_small, fill=MUTED)

    # Right badge
    local_conn = "LOCAL CONNECTION" if is_english else "CONEXIÓN LOCAL"
    no_cloud = "No cloud dependency" if is_english else "Sin depender de la nube"
    draw.text((1770, 925), local_conn, font=font_bold_small, fill=MINT, anchor="ra")
    draw.text((1770, 962), no_cloud, font=font_reg_small, fill=MUTED, anchor="ra")

    # 4. Optional Dialog Overlay (for Scene 5: Remote setup)
    if show_dialog:
        # Semi-transparent dark overlay
        dim_overlay = Image.new("RGBA", (W, H), (0, 0, 0, 160))
        img.paste(Image.alpha_composite(Image.new("RGBA", (W, H), (5, 11, 20, 255)), dim_overlay).convert("RGB"), (0, 0))
        draw = ImageDraw.Draw(img)

        # Dialog Box
        dw, dh = 760, 440
        dx1, dy1 = (W - dw) // 2, (H - dh) // 2
        dx2, dy2 = dx1 + dw, dy1 + dh
        draw.rounded_rectangle([dx1, dy1, dx2, dy2], radius=24, fill=(18, 32, 48), outline=(60, 105, 135), width=3)

        # Dialog title & message
        draw.text((dx1 + 48, dy1 + 45), "Configuración de Solar Dream", font=font_bold_title, fill=TEXT)
        draw.text((dx1 + 48, dy1 + 100), "Introduce la dirección IP local de tu DTU Hoymiles:", font=font_reg_mid, fill=MUTED)

        # Input box (Focused with green/mint border)
        draw.rounded_rectangle([dx1 + 48, dy1 + 160, dx2 - 48, dy1 + 230], radius=12, fill=(10, 18, 28), outline=MINT, width=3)
        draw.text((dx1 + 72, dy1 + 180), "192.168.1.100", font=font_bold_mid, fill=TEXT)
        draw.line([dx1 + 260, dy1 + 175, dx1 + 260, dy1 + 215], fill=MINT, width=2) # cursor

        # Buttons
        # Save Button (Focused with D-Pad)
        draw.rounded_rectangle([dx2 - 200, dy2 - 90, dx2 - 48, dy2 - 35], radius=14, fill=MINT)
        draw.text((dx2 - 124, dy2 - 65), "Guardar", font=font_bold_small, fill=(5, 11, 20), anchor="mm")

        # Screensaver Button
        draw.rounded_rectangle([dx1 + 48, dy2 - 90, dx1 + 280, dy2 - 35], radius=14, fill=(28, 48, 68), outline=BORDER, width=2)
        draw.text((dx1 + 164, dy2 - 65), "Ajustes de Salvapantallas", font=font_bold_small, fill=TEXT, anchor="mm")

        # Cancel Button
        draw.rounded_rectangle([dx2 - 360, dy2 - 90, dx2 - 220, dy2 - 35], radius=14, fill=(28, 48, 68), outline=BORDER, width=2)
        draw.text((dx2 - 290, dy2 - 65), "Cancelar", font=font_bold_small, fill=MUTED, anchor="mm")

    # Save to disk
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    img.save(filename, "PNG", optimize=True)
    # Also save as JPEG
    jpg_name = os.path.splitext(filename)[0] + ".jpg"
    img.save(jpg_name, "JPEG", quality=95)
    print(f"Generated: {filename} & {jpg_name}")

def main():
    dest_dir = "play_store_assets/screenshots"
    os.makedirs(dest_dir, exist_ok=True)

    scenes = [
        # 1. Full Solar Export (Midday peak)
        {
            "filename": f"{dest_dir}/screenshot_tv_1.png",
            "solar_kw": "3.85 kW", "home_kw": "1.20 kW", "grid_kw": "2.65 kW",
            "solar_state": "GENERANDO ENERGÍA", "grid_state": "EXPORTANDO EXCEDENTE", "grid_mode": "EXPORT",
            "today_kwh": "18.4 kWh", "hero_title": "Tu hogar comparte energía", "hero_sub": "Red (exporta) · 2.65 kW",
            "time_str": "13:42", "status_text": "EN VIVO · DTU LOCAL", "status_online": True, "is_english": False
        },
        # 2. Perfect Balance / Self-consumption
        {
            "filename": f"{dest_dir}/screenshot_tv_2.png",
            "solar_kw": "1.75 kW", "home_kw": "1.70 kW", "grid_kw": "0.05 kW",
            "solar_state": "GENERANDO ENERGÍA", "grid_state": "SIN INTERCAMBIO", "grid_mode": "IDLE",
            "today_kwh": "14.2 kWh", "hero_title": "Tu energía está en equilibrio", "hero_sub": "Red · 0.05 kW",
            "time_str": "16:15", "status_text": "EN VIVO · DTU LOCAL", "status_online": True, "is_english": False
        },
        # 3. Night Mode / Grid Powering Home
        {
            "filename": f"{dest_dir}/screenshot_tv_3.png",
            "solar_kw": "0.00 kW", "home_kw": "0.85 kW", "grid_kw": "0.85 kW",
            "solar_state": "EN REPOSO", "grid_state": "IMPORTANDO ENERGÍA", "grid_mode": "IMPORT",
            "today_kwh": "22.1 kWh", "hero_title": "La red mantiene tu hogar en marcha", "hero_sub": "Red (importa) · 0.85 kW",
            "time_str": "21:30", "status_text": "EN VIVO · DTU LOCAL", "status_online": True, "is_english": False
        },
        # 4. Cloudy / Mixed Consumption
        {
            "filename": f"{dest_dir}/screenshot_tv_4.png",
            "solar_kw": "0.65 kW", "home_kw": "1.90 kW", "grid_kw": "1.25 kW",
            "solar_state": "GENERANDO ENERGÍA", "grid_state": "IMPORTANDO ENERGÍA", "grid_mode": "IMPORT",
            "today_kwh": "6.8 kWh", "hero_title": "El sol y la red alimentan tu hogar", "hero_sub": "Red (importa) · 1.25 kW",
            "time_str": "10:20", "status_text": "EN VIVO · DTU LOCAL", "status_online": True, "is_english": False
        },
        # 5. Remote control on-screen settings dialog
        {
            "filename": f"{dest_dir}/screenshot_tv_5.png",
            "solar_kw": "2.40 kW", "home_kw": "1.10 kW", "grid_kw": "1.30 kW",
            "solar_state": "GENERANDO ENERGÍA", "grid_state": "EXPORTANDO EXCEDENTE", "grid_mode": "EXPORT",
            "today_kwh": "12.0 kWh", "hero_title": "Tu hogar comparte energía", "hero_sub": "Red (exporta) · 1.30 kW",
            "time_str": "14:05", "status_text": "EN VIVO · DTU LOCAL", "status_online": True, "is_english": False,
            "show_dialog": True
        },
        # 6. Offline / Waiting for DTU
        {
            "filename": f"{dest_dir}/screenshot_tv_6.png",
            "solar_kw": "2.41 kW", "home_kw": "1.12 kW", "grid_kw": "1.29 kW",
            "solar_state": "EN ESPERA", "grid_state": "EN ESPERA", "grid_mode": "UNKNOWN",
            "today_kwh": "11.2 kWh", "hero_title": "Esperando al DTU", "hero_sub": "Sin conexión · última lectura a las 18:45",
            "time_str": "18:45", "status_text": "SIN CONEXIÓN", "status_online": False, "is_english": False
        },
        # 7. English UI - Peak Solar Export
        {
            "filename": f"{dest_dir}/screenshot_tv_7.png",
            "solar_kw": "4.20 kW", "home_kw": "1.10 kW", "grid_kw": "3.10 kW",
            "solar_state": "GENERATING POWER", "grid_state": "EXPORTING SURPLUS", "grid_mode": "EXPORT",
            "today_kwh": "21.6 kWh", "hero_title": "Your home is sharing energy", "hero_sub": "Grid (export) · 3.10 kW",
            "time_str": "13:10", "status_text": "LIVE · LOCAL DTU", "status_online": True, "is_english": True
        },
        # 8. English UI - Night Mode / Grid Power
        {
            "filename": f"{dest_dir}/screenshot_tv_8.png",
            "solar_kw": "0.00 kW", "home_kw": "0.95 kW", "grid_kw": "0.95 kW",
            "solar_state": "IDLE", "grid_state": "IMPORTING POWER", "grid_mode": "IMPORT",
            "today_kwh": "19.8 kWh", "hero_title": "The grid is powering your home", "hero_sub": "Grid (import) · 0.95 kW",
            "time_str": "22:15", "status_text": "LIVE · LOCAL DTU", "status_online": True, "is_english": True
        }
    ]

    for s in scenes:
        render_tv_scene(**s)

    print("\nAll 8 Android TV screenshots successfully generated!")

if __name__ == "__main__":
    main()
