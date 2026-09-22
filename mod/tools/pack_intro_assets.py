from PIL import Image, ImageFilter, ImageDraw
import os

src_dir = r"C:\Users\Marrow-001\.gemini\antigravity\scratch\SolarClient\mod\tools\intro_extract"
assets = r"C:\Users\Marrow-001\.gemini\antigravity\scratch\SolarClient\mod\src\main\resources\assets\solarclient\textures\intro"
os.makedirs(assets, exist_ok=True)

frames = sorted(
    f for f in os.listdir(src_dir) if f.startswith("frame_") and f.endswith(".png")
)
print("input frames", len(frames))

for i, name in enumerate(frames):
    im = Image.open(os.path.join(src_dir, name)).convert("RGBA")
    im.save(os.path.join(assets, f"frame_{i:03d}.png"), optimize=True)

last = Image.open(os.path.join(src_dir, frames[-1])).convert("RGBA")
w, h = last.size
cw, ch = int(w * 0.62), int(h * 0.38)
cx0, cy0 = (w - cw) // 2, (h - ch) // 2 - int(h * 0.02)
logo_region = last.crop((cx0, cy0, cx0 + cw, cy0 + ch))

pixels = logo_region.load()
for y in range(ch):
    for x in range(cw):
        r, g, b, a = pixels[x, y]
        lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        if lum < 55:
            pixels[x, y] = (r, g, b, 0)
        elif lum < 110:
            alpha = int((lum - 55) / 55 * 255)
            pixels[x, y] = (255, 255, 255, alpha)
        else:
            pixels[x, y] = (
                min(255, int(r * 1.05)),
                min(255, int(g * 1.05)),
                min(255, int(b * 1.05)),
                255,
            )

logo_region.save(os.path.join(assets, "logo.png"), optimize=True)
print("logo", logo_region.size)

bg = last.copy()
mask = Image.new("L", (w, h), 0)
draw = ImageDraw.Draw(mask)
pad = 24
draw.ellipse((cx0 - pad, cy0 - pad, cx0 + cw + pad, cy0 + ch + pad), fill=255)
blurred = last.filter(ImageFilter.GaussianBlur(radius=30))
bg.paste(blurred, mask=mask)
overlay = Image.new("RGBA", (w, h), (8, 5, 20, 0))
od = ImageDraw.Draw(overlay)
od.ellipse((cx0 - pad, cy0 - pad, cx0 + cw + pad, cy0 + ch + pad), fill=(10, 8, 24, 100))
bg = Image.alpha_composite(bg, overlay)
bg.save(os.path.join(assets, "menu_bg.png"), optimize=True)

with open(os.path.join(assets, "meta.txt"), "w", encoding="utf-8") as f:
    f.write(f"frames={len(frames)}\n")
    f.write("fps=12\n")
    f.write(f"logo_src={cw}x{ch}\n")
    f.write(f"frame_size={w}x{h}\n")

total = sum(
    os.path.getsize(os.path.join(root, fn))
    for root, _, files in os.walk(assets)
    for fn in files
)
print("assets MB", round(total / 1024 / 1024, 2))
print("done")
