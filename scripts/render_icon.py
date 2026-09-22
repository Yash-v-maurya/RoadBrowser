"""Generates the RoadBrowser launcher artwork from one set of geometry.

The mark: a browser window (rounded frame with a top bar and a gold address
pill) with a road running out of its bottom edge toward the viewer, gold
dashed centre line, on an ivory background.

Run from anywhere:  python scripts/render_icon.py

Writes (relative to the repo root):
  app/src/main/res/drawable/ic_launcher_background.xml   ivory backdrop
  app/src/main/res/drawable/ic_launcher_foreground.xml   full-colour mark
  app/src/main/res/drawable/ic_launcher_monochrome.xml   single-colour mark (themed icons)
  app/src/main/res/drawable/ic_stat_roadbrowser.xml      24dp white notification icon
  icon.png          512x512 rounded-square render of the 72dp visible area
  icon_preview.png  96px preview

Then run scripts/generate_icons.ps1 to refresh the legacy mipmap PNGs.
Requires Pillow (pip install pillow).
"""
import os
import sys

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BG_TOP = "#FFFFFF"
BG = "#F7F5F0"          # ivory
CHARCOAL = "#161616"
GOLD = "#C9A227"
WHITE = "#FFFFFF"

# ---- geometry in the 108dp adaptive-icon space -----------------------------
# Safe zone: circle of radius 33 centred on (54, 54). Everything except the
# road's bottom bleed stays inside it.
CX = 54.0
WIN_X, WIN_Y, WIN_W, WIN_H = 33.0, 28.0, 42.0, 32.0   # window frame box
WIN_R = 6.0                                           # corner radius
FRAME_W = 3.2                                         # frame stroke
BAR_H = 8.0                                           # top bar height
PILL_X0, PILL_X1, PILL_H = 42.0, 66.0, 3.0            # address pill
PILL_Y = WIN_Y + BAR_H / 2
VANISH_Y = WIN_Y + BAR_H                              # road apex (under the bar)
ROAD_RATE = 0.52                                      # half-width growth per dp
DASH_F = 0.14                                         # dash half-width / road half-width
LAUNCHER_DASHES = [(43.0, 47.0), (50.0, 55.5), (64.0, 71.0), (77.0, 87.0), (94.0, 108.0)]
STATUS_DASHES = [(44.0, 48.0), (51.5, 56.0), (64.0, 71.0), (77.0, 87.0)]


def road_half(y):
    return max(0.0, (y - VANISH_Y) * ROAD_RATE)


def road_poly(y_bottom=108.0):
    hb = road_half(y_bottom)
    return [(CX - hb, y_bottom), (CX + hb, y_bottom), (CX, VANISH_Y)]


def dashes(ranges, f=DASH_F):
    out = []
    for y0, y1 in ranges:  # y0 far, y1 near
        h0 = road_half(y0) * f
        h1 = road_half(y1) * f
        out.append([(CX - h1, y1), (CX + h1, y1), (CX + h0, y0), (CX - h0, y0)])
    return out


# ---- path-data helpers -----------------------------------------------------
def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def ident(p):
    return p


def poly_path(pts, tf=ident):
    pts = [tf(p) for p in pts]
    d = "M" + ",".join(map(fmt, pts[0]))
    for p in pts[1:]:
        d += "L" + ",".join(map(fmt, p))
    return d + "Z"


def rounded_rect_path(x, y, w, h, r, tf=ident, sc=1.0):
    x, y = tf((x, y))
    w, h, r = w * sc, h * sc, r * sc
    return (f"M{fmt(x + r)},{fmt(y)}H{fmt(x + w - r)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x + w)},{fmt(y + r)}V{fmt(y + h - r)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x + w - r)},{fmt(y + h)}H{fmt(x + r)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x)},{fmt(y + h - r)}V{fmt(y + r)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x + r)},{fmt(y)}Z")


def top_bar_path(tf=ident, sc=1.0):
    x, y = tf((WIN_X, WIN_Y))
    w, h, r = WIN_W * sc, BAR_H * sc, WIN_R * sc
    return (f"M{fmt(x)},{fmt(y + h)}V{fmt(y + r)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x + r)},{fmt(y)}H{fmt(x + w - r)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x + w)},{fmt(y + r)}V{fmt(y + h)}Z")


def pill_path(h=PILL_H, tf=ident, sc=1.0):
    x0, y = tf((PILL_X0, PILL_Y))
    x1, _ = tf((PILL_X1, PILL_Y))
    r = h * sc / 2
    return (f"M{fmt(x0)},{fmt(y - r)}H{fmt(x1)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x1)},{fmt(y + r)}H{fmt(x0)}"
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x0)},{fmt(y - r)}Z")


def vpath(data, fill=None, stroke=None, sw=None, even_odd=False):
    attrs = [f'android:pathData="{data}"']
    if fill:
        attrs.append(f'android:fillColor="{fill}"')
    if even_odd:
        attrs.append('android:fillType="evenOdd"')
    if stroke:
        attrs.append(f'android:strokeColor="{stroke}"')
        attrs.append(f'android:strokeWidth="{fmt(sw)}"')
        attrs.append('android:strokeLineCap="round"')
        attrs.append('android:strokeLineJoin="round"')
    return "    <path\n        " + "\n        ".join(attrs) + " />"


def vector(size, viewport, body, tint=None):
    tint_attr = f'\n    android:tint="{tint}"' if tint else ""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}"'
            f'{tint_attr}>\n' + "\n".join(body) + "\n</vector>\n")


# ---- XML: background -------------------------------------------------------
def background_xml():
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- RoadBrowser launcher background: white fading to warm ivory. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="54"
                android:startY="0"
                android:endX="54"
                android:endY="108"
                android:startColor="{BG_TOP}"
                android:endColor="{BG}" />
        </aapt:attr>
    </path>
</vector>
'''


# ---- XML: foreground (full colour) -----------------------------------------
def foreground_xml():
    body = [
        "    <!-- Road: from the vanishing point under the top bar, out of the window, off the bottom -->",
        vpath(poly_path(road_poly()), fill=CHARCOAL),
        "    <!-- Browser window frame and top bar -->",
        vpath(rounded_rect_path(WIN_X, WIN_Y, WIN_W, WIN_H, WIN_R), stroke=CHARCOAL, sw=FRAME_W),
        vpath(top_bar_path(), fill=CHARCOAL),
        "    <!-- Gold address pill and dashed centre line -->",
        vpath(pill_path(), fill=GOLD),
    ]
    for d in dashes(LAUNCHER_DASHES):
        body.append(vpath(poly_path(d), fill=GOLD))
    return vector(108, 108, body)


# ---- XML: single-colour silhouettes (monochrome layer, status icon) -------
def silhouette_paths(tf, sc, frame_w, pill_h, y_bottom, dash_ranges, colour):
    """Window + road as one colour; the pill and dashes are cut out (evenOdd)."""
    road = poly_path(road_poly(y_bottom), tf)
    for d in dashes(dash_ranges):
        road += poly_path(d, tf)
    return [
        vpath(road, fill=colour, even_odd=True),
        vpath(rounded_rect_path(WIN_X, WIN_Y, WIN_W, WIN_H, WIN_R, tf, sc),
              stroke=colour, sw=frame_w),
        vpath(top_bar_path(tf, sc) + pill_path(pill_h, tf, sc), fill=colour, even_odd=True),
    ]


def monochrome_xml():
    return vector(108, 108, silhouette_paths(ident, 1.0, FRAME_W, PILL_H, 108.0,
                                             LAUNCHER_DASHES, WHITE))


def status_xml():
    # Map the 66dp safe zone (21..87) onto 2..22 of the 24dp box.
    s = 20.0 / 66.0

    def tf(p):
        return ((p[0] - 21.0) * s + 2.0, (p[1] - 21.0) * s + 2.0)

    body = silhouette_paths(tf, s, 1.2, 4.0, 87.0, STATUS_DASHES, WHITE)
    return vector(24, 24, body, tint="#FFFFFFFF")


# ---- raster render ---------------------------------------------------------
def render_icon(px=512, ss=4):
    """Render the 72dp visible area of the adaptive icon as a rounded square."""
    k = px * ss / 72.0
    size = px * ss
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    def tf(p):
        return ((p[0] - 18.0) * k, (p[1] - 18.0) * k)

    def box(x, y, w, h):
        x0, y0 = tf((x, y))
        return [x0, y0, x0 + w * k, y0 + h * k]

    # background gradient (same ramp as ic_launcher_background over 0..108)
    c0 = tuple(int(BG_TOP[i:i + 2], 16) for i in (1, 3, 5))
    c1 = tuple(int(BG[i:i + 2], 16) for i in (1, 3, 5))
    for row in range(size):
        t = (18.0 + row / k) / 108.0
        col = tuple(round(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)
        draw.line([(0, row), (size, row)], fill=col)

    def poly(pts, col):
        draw.polygon([tf(p) for p in pts], fill=col)

    # road
    poly(road_poly(), CHARCOAL)
    # window: outer rounded rect, ivory interior, top bar
    draw.rounded_rectangle(box(WIN_X, WIN_Y, WIN_W, WIN_H), radius=WIN_R * k, fill=CHARCOAL)
    inner = box(WIN_X + FRAME_W, WIN_Y + FRAME_W, WIN_W - 2 * FRAME_W, WIN_H - 2 * FRAME_W)
    draw.rounded_rectangle(inner, radius=max(0.0, (WIN_R - FRAME_W) * k), fill=BG)
    draw.rectangle(box(WIN_X + FRAME_W, WIN_Y + FRAME_W, WIN_W - 2 * FRAME_W, BAR_H - FRAME_W),
                   fill=CHARCOAL)
    # the road continues inside the window (drawn over the ivory interior)
    poly(road_poly(WIN_Y + WIN_H), CHARCOAL)
    # gold details
    draw.rounded_rectangle(box(PILL_X0, PILL_Y - PILL_H / 2, PILL_X1 - PILL_X0, PILL_H),
                           radius=PILL_H * k / 2, fill=GOLD)
    for d in dashes(LAUNCHER_DASHES):
        poly(d, GOLD)

    # rounded-square mask, roughly the shape of a launcher mask
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1],
                                           radius=int(size * 0.18), fill=255)
    img.putalpha(mask)
    return img.resize((px, px), Image.LANCZOS)


def main():
    drawable = os.path.join(REPO, "app", "src", "main", "res", "drawable")
    files = {
        "ic_launcher_background.xml": background_xml(),
        "ic_launcher_foreground.xml": foreground_xml(),
        "ic_launcher_monochrome.xml": monochrome_xml(),
        "ic_stat_roadbrowser.xml": status_xml(),
    }
    for name, content in files.items():
        with open(os.path.join(drawable, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        print("wrote", name)

    icon = render_icon()
    icon.save(os.path.join(REPO, "icon.png"))
    icon.resize((96, 96), Image.LANCZOS).save(os.path.join(REPO, "icon_preview.png"))
    print("wrote icon.png and icon_preview.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
