"""Builds Taskora launcher assets from logo.png (1264px, opaque, black outside its rounded square).

Usage: python3 tools/make_launcher_icons.py [path/to/logo.png] [preview_dir]   (needs Pillow + numpy)
The artwork is shrunk into the adaptive icon's 66dp safe zone and the background gradient is
extended outward with a fitted color model, so circle masks don't clip the note.
"""
import os, sys, numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = f'{ROOT}/app/src/main/res'
LOGO = sys.argv[1] if len(sys.argv) > 1 else f'{ROOT}/logo.png'
PREVIEW_DIR = sys.argv[2] if len(sys.argv) > 2 else None
src = np.asarray(Image.open(LOGO).convert('RGB')).astype(np.float64)
N = src.shape[0]; H = N / 2; R = 246.0  # rounded-square half size and corner radius

def inside_distance(x, y):
    """Distance from (x, y) to the rounded square's edge, positive inside (image px)."""
    qx = np.abs(x - H) - (H - R); qy = np.abs(y - H) - (H - R)
    outside = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - R
    return -outside

yy, xx = np.mgrid[0:N, 0:N].astype(np.float64)
d = inside_distance(xx + 0.5, yy + 0.5)
center_dist = np.hypot(xx - H, yy - H)

# Fit a smooth color model to the gradient ring (inside the square, away from the note and its shadow).
ring = (d > 15) & (center_dist > 600)
def features(x, y):
    u = x / N; v = y / N
    return np.stack([np.ones_like(u), u, v, u * v, u * u, v * v], axis=-1)
A = features(xx[ring], yy[ring]); coef = np.linalg.lstsq(A, src[ring], rcond=None)[0]
err = np.abs(A @ coef - src[ring]).mean(); print('model mean abs error', round(err, 2))

# Canvas: the note (radius ~540px) must sit inside the 66dp safe circle of a 108dp layer.
px_per_dp = 540 / 33
C = int(round(108 * px_per_dp)); off = (C - N) / 2
cy, cx = np.mgrid[0:C, 0:C].astype(np.float64)
ix, iy = cx - off, cy - off
canvas = np.clip(features(ix, iy) @ coef, 0, 255)
# Blend the artwork in, fading over the outer 70px of its rounded square to hide the seam.
inside = (ix >= 0) & (iy >= 0) & (ix < N) & (iy < N)
alpha = np.zeros((C, C))
sx = np.clip(ix.astype(int), 0, N - 1); sy = np.clip(iy.astype(int), 0, N - 1)
alpha[inside] = np.clip((d[sy[inside], sx[inside]] - 10) / 70, 0, 1)
art = np.zeros((C, C, 3)); art[inside] = src[sy[inside], sx[inside]]
full = (art * alpha[..., None] + canvas * (1 - alpha[..., None])).astype(np.uint8)
full_img = Image.fromarray(full, 'RGB')

densities = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for name, scale in densities.items():
    folder = f'{RES}/mipmap-{name}'
    os.makedirs(folder, exist_ok=True)
    for old in ('ic_launcher.webp', 'ic_launcher_round.webp'):
        if os.path.exists(f'{folder}/{old}'): os.remove(f'{folder}/{old}')
    size = int(108 * scale)
    full_img.resize((size, size), Image.LANCZOS).save(f'{folder}/ic_launcher_foreground.png', optimize=True)

# Background layer color (only visible if a launcher shows past the foreground): the model at the center.
bg = np.clip(features(np.array([H]), np.array([H])) @ coef, 0, 255)[0].astype(int)
print('background (set in values/ic_launcher_background.xml)', '#%02X%02X%02X' % tuple(bg))

# In-app logo: original with transparent corners.
mask = Image.fromarray((np.clip(d + 0.5, 0, 1) * 255).astype(np.uint8), 'L')
logo = Image.open(LOGO).convert('RGBA'); logo.putalpha(mask)
os.makedirs(f'{RES}/drawable-nodpi', exist_ok=True)
logo.resize((288, 288), Image.LANCZOS).save(f'{RES}/drawable-nodpi/taskora_logo.png', optimize=True)
# Play Store icon (512px, full bleed, Play applies its own mask).
full_img.crop((int(off - 0), int(off - 0), int(off + N), int(off + N))).resize((512, 512), Image.LANCZOS).save(f'{ROOT}/app/src/main/ic_launcher-playstore.png')

# Previews: 72dp visible area with circle and squircle masks.
if PREVIEW_DIR is None: sys.exit(0)
vis = full_img.crop((int(18 * px_per_dp),) * 2 + (int(90 * px_per_dp),) * 2).resize((300, 300), Image.LANCZOS)
sheet = Image.new('RGB', (640, 320), 'white')
for i, shape in enumerate(('circle', 'squircle')):
    m = Image.new('L', (300, 300), 0); dr = ImageDraw.Draw(m)
    if shape == 'circle': dr.ellipse((0, 0, 299, 299), fill=255)
    else: dr.rounded_rectangle((0, 0, 299, 299), radius=90, fill=255)
    sheet.paste(vis, (10 + i * 320, 10), m)
sheet.save(f'{PREVIEW_DIR}/icon_preview.png')
