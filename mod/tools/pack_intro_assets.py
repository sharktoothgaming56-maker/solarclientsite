"""Rebuild intro assets: high-quality frames + logo-free BG still + logo PNG."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys

from PIL import Image

try:
    import imageio_ffmpeg
except ImportError:
    sys.exit("imageio_ffmpeg required")

VIDEO = r"C:\Users\Marrow-001\Downloads\SolarClient-Loading-Intro.mp4"
VIDEO_BG = r"C:\Users\Marrow-001\Downloads\SolarClient-Loading-Intro-BG.mp4"
LOGO_SRC = (
    r"C:\Users\Marrow-001\.cursor\projects\c-Users-Marrow-001-gemini-antigravity-scratch-SolarClient-mod"
    r"\assets\c__Users_Marrow-001_AppData_Roaming_Cursor_User_workspaceStorage_"
    r"a7d2cf61fd16c0ba311feb06cc8459b2_images_0f94ce3f-2336-4917-86d7-90689b1845e0-"
    r"515c373f-436a-43f7-b07c-be663fa15c01.png"
)
MOD_ROOT = r"C:\Users\Marrow-001\.gemini\antigravity\scratch\SolarClient\mod"
EXTRACT = os.path.join(MOD_ROOT, "tools", "intro_extract")
ASSETS = os.path.join(
    MOD_ROOT, "src", "main", "resources", "assets", "solarclient", "textures", "intro"
)

# Quality over tiny size — lag is fixed in Java via async decode, not by blurring.
OUT_W, OUT_H = 960, 540
FPS = 12


def ffmpeg() -> str:
    return imageio_ffmpeg.get_ffmpeg_exe()


def clear_dir(path: str) -> None:
    if os.path.isdir(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


def extract_frames() -> list[str]:
    clear_dir(EXTRACT)
    pattern = os.path.join(EXTRACT, "frame_%04d.png")
    cmd = [
        ffmpeg(), "-y", "-i", VIDEO,
        "-vf", f"fps={FPS},scale={OUT_W}:{OUT_H}:flags=lanczos",
        "-start_number", "0", pattern,
    ]
    print("extract intro", " ".join(cmd))
    subprocess.check_call(cmd)
    frames = sorted(f for f in os.listdir(EXTRACT) if f.startswith("frame_") and f.endswith(".png"))
    print("extracted", len(frames), "frames")
    return frames


def extract_menu_bg() -> None:
    out = os.path.join(EXTRACT, "menu_bg_src.png")
    # Last frame of the logo-free BG video.
    cmd = [
        ffmpeg(), "-y", "-sseof", "-0.05", "-i", VIDEO_BG,
        "-frames:v", "1",
        "-vf", f"scale={OUT_W}:{OUT_H}:flags=lanczos",
        out,
    ]
    print("extract bg", " ".join(cmd))
    subprocess.check_call(cmd)
    Image.open(out).convert("RGBA").save(os.path.join(ASSETS, "menu_bg.png"), optimize=True)
    print("menu_bg written")


def logo_to_transparent(src_path: str) -> Image.Image:
    im = Image.open(src_path).convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            if lum < 18:
                px[x, y] = (255, 255, 255, 0)
            elif lum < 90:
                alpha = int((lum - 18) / (90 - 18) * 255)
                px[x, y] = (255, 255, 255, alpha)
            else:
                px[x, y] = (255, 255, 255, 255)
    bbox = im.getbbox()
    if bbox:
        im = im.crop(bbox)
    return im


def find_bright_bbox(im: Image.Image, thresh: int = 140) -> tuple[int, int, int, int]:
    g = im.convert("L")
    px = g.load()
    w, h = g.size
    min_x, min_y, max_x, max_y = w, h, 0, 0
    found = False
    y0, y1 = int(h * 0.15), int(h * 0.85)
    x0, x1 = int(w * 0.05), int(w * 0.95)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if px[x, y] >= thresh:
                found = True
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if not found:
        cw, ch = int(w * 0.55), int(h * 0.32)
        cx0, cy0 = (w - cw) // 2, (h - ch) // 2
        return cx0, cy0, cx0 + cw, cy0 + ch
    pad = 6
    return max(0, min_x - pad), max(0, min_y - pad), min(w, max_x + 1 + pad), min(h, max_y + 1 + pad)


def write_assets(frames: list[str], logo: Image.Image) -> None:
    if os.path.isdir(ASSETS):
        for name in os.listdir(ASSETS):
            if name.startswith("frame_") and name.endswith(".png"):
                os.remove(os.path.join(ASSETS, name))
    else:
        os.makedirs(ASSETS, exist_ok=True)

    for i, name in enumerate(frames):
        Image.open(os.path.join(EXTRACT, name)).convert("RGBA").save(
            os.path.join(ASSETS, f"frame_{i:03d}.png"), optimize=True
        )

    last = Image.open(os.path.join(EXTRACT, frames[-1])).convert("RGBA")
    bbox = find_bright_bbox(last)
    print("end-card logo bbox", bbox)

    logo.save(os.path.join(ASSETS, "logo.png"), optimize=True)
    print("logo", logo.size)

    extract_menu_bg()

    bx0, by0, bx1, by1 = bbox
    with open(os.path.join(ASSETS, "meta.txt"), "w", encoding="utf-8") as f:
        f.write(f"frames={len(frames)}\n")
        f.write(f"fps={FPS}\n")
        f.write(f"frame_size={OUT_W}x{OUT_H}\n")
        f.write(f"logo_src={logo.size[0]}x{logo.size[1]}\n")
        f.write(f"endcard_logo_box={bx0},{by0},{bx1},{by1}\n")

    total = sum(
        os.path.getsize(os.path.join(root, fn))
        for root, _, files in os.walk(ASSETS)
        for fn in files
    )
    print("assets MB", round(total / 1024 / 1024, 2))


def main() -> None:
    for path in (VIDEO, VIDEO_BG, LOGO_SRC):
        if not os.path.isfile(path):
            sys.exit(f"missing: {path}")
    frames = extract_frames()
    logo = logo_to_transparent(LOGO_SRC)
    write_assets(frames, logo)
    print("done")


if __name__ == "__main__":
    main()
