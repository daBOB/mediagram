"""Draws the launcher icon and TV banner options for Mediagram and Spieltisch as SVG (text as outlines)."""
import math, os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.varLib.instancer import instantiateVariableFont

OUT = os.path.dirname(os.path.abspath(__file__))
FRAUNCES = TTFont(os.path.expanduser('~/Workspace/mediagram/android/core/designsystem/src/main/res/font/fraunces_display_600.ttf'))
BRIC = instantiateVariableFont(TTFont(os.path.expanduser(
    '~/Workspace/mediagram/ios/controller/Sources/Spieltisch/Resources/Fonts/BricolageGrotesque.ttf')),
    {'wght': 780, 'opsz': 96, 'wdth': 100})

PAPER, INK, CORAL = '#0d0d0e', '#f3efe7', '#e57a61'
FELT, CREAM, PIP, LIT = '#0F5A43', '#FBF8F1', '#0C2A22', '#F2C14E'


def text(font, s, size, x, y, fill, track=0.0):
    """Returns (svg path, advance width) for s set at size with its baseline at y."""
    gs, cmap, upm = font.getGlyphSet(), font.getBestCmap(), font['head'].unitsPerEm
    k, pen, adv = size / upm, SVGPathPen(gs), 0.0
    for ch in s:
        g = gs[cmap[ord(ch)]]
        g.draw(TransformPen(pen, (k, 0, 0, -k, x + adv * k, y)))
        adv += g.width + track * upm
    return f'<path fill="{fill}" d="{pen.getCommands()}"/>', adv * k


def width(font, s, size, track=0.0):
    return text(font, s, size, 0, 0, '#000', track)[1]


def svg(w, h, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{body}</svg>'


# ---- Mediagram marks, drawn on a 108 square centred at (54, 54) ----

def mark_parts(cx=54, cy=54, s=1.0):
    """A film strip cut into three parts, each a step further along: films live in the channel as parts."""
    seg, gap, h = 24.6 * s, 5 * s, 34 * s
    x0, out = cx - (3 * seg + 2 * gap) / 2, []
    for i, dy in enumerate((-4, 0, 4)):
        x, y = x0 + i * (seg + gap), cy - h / 2 + dy * s
        out.append(f'<rect x="{x:.2f}" y="{y:.2f}" width="{seg:.2f}" height="{h:.2f}" rx="{2*s:.2f}" fill="{CORAL}"/>')
        for j in range(3):
            hx = x + seg * (j + 0.5) / 3 - 2.1 * s
            for hy in (y + 3.4 * s, y + h - 3.4 * s - 4.6 * s):
                out.append(f'<rect x="{hx:.2f}" y="{hy:.2f}" width="{4.2*s:.2f}" height="{4.6*s:.2f}" rx="{1*s:.2f}" fill="{PAPER}"/>')
        out.append(f'<rect x="{x+3*s:.2f}" y="{y+11.6*s:.2f}" width="{seg-6*s:.2f}" height="{h-23.2*s:.2f}" rx="{1*s:.2f}" fill="#b85a46"/>')
    return ''.join(out)


def mark_leader(cx=54, cy=54, s=1.0):
    """The player's own film leader: rings, crosshair, a coral sweep and the 3."""
    R, r = 34 * s, 25 * s
    a = math.radians(-90 + 270)
    ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
    sweep = f'<path d="M{cx},{cy} L{cx},{cy-r} A{r},{r} 0 1 1 {ex:.2f},{ey:.2f} Z" fill="{CORAL}" fill-opacity="0.85"/>'
    rings = (f'<circle cx="{cx}" cy="{cy}" r="{R}" fill="none" stroke="{INK}" stroke-width="{2.6*s}"/>'
             f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{INK}" stroke-width="{1.6*s}"/>'
             f'<path d="M{cx-R-4*s},{cy}H{cx+R+4*s}M{cx},{cy-R-4*s}V{cy+R+4*s}" stroke="{INK}" stroke-opacity="0.45" stroke-width="{1.2*s}"/>')
    size = 36 * s
    w = width(FRAUNCES, '3', size)
    num, _ = text(FRAUNCES, '3', size, cx - w / 2, cy + size * 0.36, INK)
    return sweep + rings + num


def mark_m(cx=54, cy=54, s=1.0):
    """The wordmark's first letter, the safe option."""
    size = 84 * s
    w = width(FRAUNCES, 'm', size)
    p, _ = text(FRAUNCES, 'm', size, cx - w / 2, cy + size * 0.24, INK)
    return p


# ---- Spieltisch marks ----

def mark_round(cx=54, cy=54, s=1.0):
    """Phones laid round the table like place settings, one lit: the night runs on everyone's phone."""
    out = [f'<circle cx="{cx}" cy="{cy}" r="{40*s}" fill="#0B4A37"/>']
    for k in range(5):
        ang = -90 + 72 * k
        x, y = cx + 30 * s * math.cos(math.radians(ang)), cy + 30 * s * math.sin(math.radians(ang))
        w, h = 13 * s, 21 * s
        lit = LIT if k == 0 else PIP
        out.append(f'<g transform="translate({x:.2f},{y:.2f}) rotate({ang+90})">'
                   f'<rect x="{-w/2:.2f}" y="{-h/2:.2f}" width="{w:.2f}" height="{h:.2f}" rx="{2.8*s:.2f}" fill="{CREAM}"/>'
                   f'<rect x="{-w/2+1.7*s:.2f}" y="{-h/2+1.7*s:.2f}" width="{w-3.4*s:.2f}" height="{h-5.4*s:.2f}" rx="{1.4*s:.2f}" fill="{lit}"/></g>')
    return ''.join(out)


def mark_die(cx=54, cy=54, s=1.0):
    """The current die showing five, kept as the safe option."""
    out = [f'<rect x="{cx-22*s}" y="{cy-22*s}" width="{44*s}" height="{44*s}" rx="{8*s}" fill="{CREAM}"/>']
    for dx, dy in ((-11, -11), (11, -11), (0, 0), (-11, 11), (11, 11)):
        out.append(f'<circle cx="{cx+dx*s}" cy="{cy+dy*s}" r="{4*s}" fill="{PIP}"/>')
    return ''.join(out)


def icon(bg, mark):
    return svg(108, 108, f'<rect width="108" height="108" fill="{bg}"/>{mark()}')


def banner(bg, mark, name, font, ink, size, scale=0.82):
    """TV launcher tile, 320x180: the mark on the left, the app's name beside it."""
    w = width(font, name, size, -0.01)
    mw = 108 * scale * 0.8
    gap = 14
    x0 = (320 - (mw + gap + w)) / 2
    m = mark(cx=x0 + mw / 2, cy=90, s=scale)
    t, _ = text(font, name, size, x0 + mw + gap, 90 + size * 0.34, ink, -0.01)
    return svg(320, 180, f'<rect width="320" height="180" fill="{bg}"/>{m}{t}')


OPTIONS = {
    'mediagram-parts': (PAPER, mark_parts, 'mediagram', FRAUNCES, INK, 42),
    'mediagram-leader': (PAPER, mark_leader, 'mediagram', FRAUNCES, INK, 42),
    'mediagram-m': (PAPER, mark_m, 'mediagram', FRAUNCES, INK, 42),
    'spieltisch-round': (FELT, mark_round, 'Spieltisch', BRIC, CREAM, 40),
    'spieltisch-die': (FELT, mark_die, 'Spieltisch', BRIC, CREAM, 40),
}

if __name__ == '__main__':
    for key, (bg, mark, name, font, ink, size) in OPTIONS.items():
        open(f'{OUT}/{key}-icon.svg', 'w').write(icon(bg, mark))
        open(f'{OUT}/{key}-banner.svg', 'w').write(banner(bg, mark, name, font, ink, size))
    print('\n'.join(sorted(OPTIONS)))
