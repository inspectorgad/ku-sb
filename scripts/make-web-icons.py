#!/usr/bin/env python3
"""Generates the web/PWA icon set from one description of the softball.

The Android launcher icons were drawn once by hand; this makes the set
reproducible, so a colour tweak is a one-line change and a re-run rather than
redrawing eight files. No image library is needed or available here, so the
PNGs are rasterised and encoded directly — supersampled 4x and averaged, which
is what keeps the seams from looking ragged at 180px.

Outputs into docs/:
  icon.svg                 vector, used by the manifest at "any" size
  icon-192.png             the Android/Chrome install icon
  icon-512.png             splash and store listings
  icon-maskable-512.png    same art inside the maskable safe zone, so a
                           launcher that crops to a circle does not clip the
                           seams
  icon-180.png             apple-touch-icon, which iOS does not take from the
                           manifest
"""

import os
import struct
import zlib

YELLOW = (0xF2, 0xE8, 0x4B)
YELLOW_EDGE = (0xD9, 0xCB, 0x35)
RED = (0xD0, 0x00, 0x0C)
BLUE = (0x00, 0x51, 0xBA)

OUT_DIR = "docs"


def _bez(p0, c, p2, t):
    return ((1 - t) ** 2 * p0[0] + 2 * t * (1 - t) * c[0] + t ** 2 * p2[0],
            (1 - t) ** 2 * p0[1] + 2 * t * (1 - t) * c[1] + t ** 2 * p2[1])


def _bez_tan(p0, c, p2, t):
    dx = 2 * (1 - t) * (c[0] - p0[0]) + 2 * t * (p2[0] - c[0])
    dy = 2 * (1 - t) * (c[1] - p0[1]) + 2 * t * (p2[1] - c[1])
    n = (dx * dx + dy * dy) ** 0.5 or 1.0
    return dx / n, dy / n


# The two seams, as quadratic curves in unit space around the ball's centre,
# with the cross-stitches hung off them at these points along each curve.
SEAM_T = (0.18, 0.34, 0.5, 0.66, 0.82)
STITCH_HALF_LEN = 0.17


def render_png(size, ball_fraction=0.335, round_bg=False, ss=4):
    """Rasterise one icon. `ball_fraction` shrinks the ball for maskable art."""
    S = size * ss
    px = [[None] * S for _ in range(S)]
    cx = cy = S / 2.0
    rrad = S * 0.18

    for y in range(S):
        row = px[y]
        Y = y + 0.5
        for x in range(S):
            X = x + 0.5
            if round_bg:
                if (X - cx) ** 2 + (Y - cy) ** 2 <= (S / 2.0) ** 2:
                    row[x] = BLUE
            else:
                ix = min(max(X, rrad), S - rrad)
                iy = min(max(Y, rrad), S - rrad)
                if (rrad <= X <= S - rrad) or (rrad <= Y <= S - rrad) \
                        or (X - ix) ** 2 + (Y - iy) ** 2 <= rrad * rrad:
                    row[x] = BLUE

    br = S * ball_fraction
    edge = S * 0.012
    br2, bre2 = br * br, (br - edge) ** 2
    lo, hi = int(cx - br) - 2, int(cx + br) + 2
    for y in range(max(lo, 0), min(hi + 1, S)):
        for x in range(max(lo, 0), min(hi + 1, S)):
            d2 = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
            if d2 <= br2:
                px[y][x] = YELLOW if d2 <= bre2 else YELLOW_EDGE

    def stamp(X, Y, w, colour):
        r = w / 2.0
        r2 = r * r
        for yy in range(int(Y - r) - 1, int(Y + r) + 2):
            if not (0 <= yy < S):
                continue
            for xx in range(int(X - r) - 1, int(X + r) + 2):
                if 0 <= xx < S and (xx + 0.5 - X) ** 2 + (yy + 0.5 - Y) ** 2 <= r2:
                    px[yy][xx] = colour

    T = lambda p: (cx + p[0] * br, cy + p[1] * br)  # noqa: E731
    seam_w, tick_w = S * 0.035 * (br / (S * 0.335)), S * 0.028 * (br / (S * 0.335))
    for sx in (-1, 1):
        p0, c, p2 = (sx * 0.55, -0.835), (sx * 0.20, 0.0), (sx * 0.55, 0.835)
        steps = max(60, S // 2)
        for i in range(steps + 1):
            X, Y = T(_bez(p0, c, p2, i / steps))
            stamp(X, Y, seam_w, RED)
        for t in SEAM_T:
            bx, by = _bez(p0, c, p2, t)
            tx, ty = _bez_tan(p0, c, p2, t)
            nx, ny = -ty, tx
            a = T((bx - nx * STITCH_HALF_LEN, by - ny * STITCH_HALF_LEN))
            b = T((bx + nx * STITCH_HALF_LEN, by + ny * STITCH_HALF_LEN))
            n = max(8, int(S * 0.05))
            for i in range(n + 1):
                stamp(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n,
                      tick_w, RED)

    # Downsample with alpha so the edges are not stair-stepped.
    raw = bytearray()
    n = ss * ss
    for y in range(size):
        raw.append(0)  # filter: none
        for x in range(size):
            rs = gs = bs = as_ = 0
            for dy in range(ss):
                src = px[y * ss + dy]
                for dx in range(ss):
                    p = src[x * ss + dx]
                    if p is not None:
                        rs += p[0]; gs += p[1]; bs += p[2]; as_ += 255
            if as_:
                raw += bytes((round(rs * 255 / as_), round(gs * 255 / as_),
                              round(bs * 255 / as_), as_ // n))
            else:
                raw += b"\x00\x00\x00\x00"

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def render_svg(size=512):
    cx = cy = size / 2.0
    br = size * 0.335
    T = lambda p: (cx + p[0] * br, cy + p[1] * br)  # noqa: E731
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
           f'viewBox="0 0 {size} {size}">',
           f'<rect width="{size}" height="{size}" rx="{size * 0.18:.1f}" fill="#0051BA"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{br:.2f}" fill="#F2E84B" '
           f'stroke="#D9CB35" stroke-width="{size * 0.012:.2f}"/>']
    for sx in (-1, 1):
        p0, c, p2 = (sx * 0.55, -0.835), (sx * 0.20, 0.0), (sx * 0.55, 0.835)
        a, b, cc = T(p0), T(p2), T(c)
        out.append(f'<path d="M {a[0]:.2f},{a[1]:.2f} Q {cc[0]:.2f},{cc[1]:.2f} '
                   f'{b[0]:.2f},{b[1]:.2f}" fill="none" stroke="#D0000C" '
                   f'stroke-width="{size * 0.035:.2f}" stroke-linecap="round"/>')
        for t in SEAM_T:
            bx, by = _bez(p0, c, p2, t)
            tx, ty = _bez_tan(p0, c, p2, t)
            nx, ny = -ty, tx
            s1 = T((bx - nx * STITCH_HALF_LEN, by - ny * STITCH_HALF_LEN))
            s2 = T((bx + nx * STITCH_HALF_LEN, by + ny * STITCH_HALF_LEN))
            out.append(f'<path d="M {s1[0]:.2f},{s1[1]:.2f} L {s2[0]:.2f},{s2[1]:.2f}" '
                       f'fill="none" stroke="#D0000C" stroke-width="{size * 0.028:.2f}" '
                       f'stroke-linecap="round"/>')
    out.append("</svg>")
    return "\n".join(out)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    written = []
    for name, size, kwargs in [
        ("icon-192.png", 192, {}),
        ("icon-512.png", 512, {}),
        # Maskable art must survive a launcher cropping to a circle: everything
        # that matters has to sit inside the middle ~80%, so the ball shrinks.
        ("icon-maskable-512.png", 512, {"ball_fraction": 0.26}),
        ("icon-180.png", 180, {}),
    ]:
        path = os.path.join(OUT_DIR, name)
        with open(path, "wb") as f:
            f.write(render_png(size, **kwargs))
        written.append((name, os.path.getsize(path)))
    svg_path = os.path.join(OUT_DIR, "icon.svg")
    with open(svg_path, "w") as f:
        f.write(render_svg())
    written.append(("icon.svg", os.path.getsize(svg_path)))
    for name, size in written:
        print(f"  {name:<24} {size:>7,} bytes")


if __name__ == "__main__":
    main()
