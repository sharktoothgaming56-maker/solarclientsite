"""Generate SDF edge-refraction displacement maps for liquid glass.
Neutral gray (128,128) = no displace. Edge normals in R/G = rim bend.
Local PIL only — no network downloads.
"""
from __future__ import annotations

import math
import os

from PIL import Image


def sd_round_box(px: float, py: float, bx: float, by: float, rad: float) -> float:
    qx = abs(px) - bx + rad
    qy = abs(py) - by + rad
    return math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - rad


def normal_round_box(px: float, py: float, bx: float, by: float, rad: float, eps: float = 0.75):
    d = sd_round_box(px, py, bx, by, rad)
    dx = sd_round_box(px + eps, py, bx, by, rad) - sd_round_box(px - eps, py, bx, by, rad)
    dy = sd_round_box(px, py + eps, bx, by, rad) - sd_round_box(px, py - eps, bx, by, rad)
    length = math.hypot(dx, dy) + 1e-9
    return dx / length, dy / length, d


def sdf_rounded_rect(w: int, h: int, radius: float, thickness: float) -> Image.Image:
    """R=x displace, G=y displace. Center=128,128. Rim carries outward bend."""
    img = Image.new("RGBA", (w, h))
    pix = img.load()
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    hw, hh = w / 2.0, h / 2.0
    r = min(radius, hw - 1.0, hh - 1.0)
    bx, by = hw - 0.5, hh - 0.5

    for y in range(h):
        for x in range(w):
            px = x - cx
            py = y - cy
            nx, ny, d = normal_round_box(px, py, bx, by, r)
            if d > 0:
                strength = 0.0
            else:
                t = max(0.0, min(1.0, (-d) / thickness))
                edge = 1.0 - t
                strength = edge * edge
            # Inward normal for magnifying lens with negative feDisplacementMap scale
            ix, iy = -nx, -ny
            r_ch = int(max(0, min(255, round(128 + strength * 127 * ix))))
            g_ch = int(max(0, min(255, round(128 + strength * 127 * iy))))
            pix[x, y] = (r_ch, g_ch, 128, 255)
    return img


def main() -> None:
    root = os.path.join(os.path.dirname(__file__), "..", "src", "renderer", "assets")
    os.makedirs(root, exist_ok=True)

    pill = sdf_rounded_rect(512, 256, radius=128, thickness=56)
    pill_path = os.path.join(root, "liquid-lens-pill.png")
    pill.save(pill_path, "PNG")

    card = sdf_rounded_rect(512, 512, radius=96, thickness=72)
    card_path = os.path.join(root, "liquid-lens-card.png")
    card.save(card_path, "PNG")

    panel = sdf_rounded_rect(768, 512, radius=80, thickness=80)
    panel_path = os.path.join(root, "liquid-lens-panel.png")
    panel.save(panel_path, "PNG")

    print("wrote", pill_path, pill.size)
    print("wrote", card_path, card.size)
    print("wrote", panel_path, panel.size)
    print("pill corner", pill.getpixel((10, 10)), "center", pill.getpixel((256, 128)))
    print("card corner", card.getpixel((20, 20)), "center", card.getpixel((256, 256)))


if __name__ == "__main__":
    main()
