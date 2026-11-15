"""Writes Mediagram's launcher icon and TV banner as Android vector drawables: the film leader, and the wordmark as outlines."""
import math, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from draw_icon_options import FRAUNCES, PAPER, INK, CORAL, width

RES = sys.argv[1]  # android/app/src/main/res


def glyphs(font, s, size, x, y, track=0.0):
    gs, cmap, upm = font.getGlyphSet(), font.getBestCmap(), font['head'].unitsPerEm
    k, pen, adv = size / upm, SVGPathPen(gs, ntos=lambda v: f'{v:.2f}'.rstrip('0').rstrip('.')), 0.0
    for ch in s:
        g = gs[cmap[ord(ch)]]
        g.draw(TransformPen(pen, (k, 0, 0, -k, x + adv * k, y)))
        adv += g.width + track * upm
    return pen.getCommands()


def f(v):
    return f'{v:.2f}'.rstrip('0').rstrip('.')


def ring(cx, cy, r):
    return f'M{f(cx-r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2*r)},0a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0z'


def leader(cx, cy, s):
    """Path elements of the film leader the player shows while a film loads."""
    R, r = 34 * s, 25 * s
    a = math.radians(180)
    ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
    size = 36 * s
    num = glyphs(FRAUNCES, '3', size, cx - width(FRAUNCES, '3', size) / 2, cy + size * 0.36)
    return [
        f'<path android:fillColor="{CORAL}" android:fillAlpha="0.85" android:pathData="M{f(cx)},{f(cy)}L{f(cx)},{f(cy-r)}A{f(r)},{f(r)} 0 1,1 {f(ex)},{f(ey)}z" />',
        f'<path android:strokeColor="{INK}" android:strokeWidth="{f(2.6*s)}" android:pathData="{ring(cx, cy, R)}" />',
        f'<path android:strokeColor="{INK}" android:strokeWidth="{f(1.6*s)}" android:pathData="{ring(cx, cy, r)}" />',
        f'<path android:strokeColor="{INK}" android:strokeAlpha="0.45" android:strokeWidth="{f(1.2*s)}" '
        f'android:pathData="M{f(cx-R-4*s)},{f(cy)}H{f(cx+R+4*s)}M{f(cx)},{f(cy-R-4*s)}V{f(cy+R+4*s)}" />',
        f'<path android:fillColor="{INK}" android:pathData="{num}" />',
    ]


def vector(w, h, comment, paths):
    body = '\n    '.join([f'<path android:fillColor="{PAPER}" android:pathData="M0,0h{w}v{h}h-{w}z" />'] + paths)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{w}dp"\n    android:height="{h}dp"\n'
            f'    android:viewportWidth="{w}"\n    android:viewportHeight="{h}">\n    {body}\n</vector>\n')


open(f'{RES}/drawable/ic_launcher.xml', 'w').write(vector(
    108, 108, "The player's film leader, the countdown it shows while a film loads, on the player's dark paper.",
    leader(54, 54, 1.0)))

size, track, s = 38, -0.01, 0.75
w = width(FRAUNCES, 'mediagram', size, track)
mw, gap = 108 * s * 0.8, 14
x0 = (320 - (mw + gap + w)) / 2
word = glyphs(FRAUNCES, 'mediagram', size, x0 + mw + gap, 90 + size * 0.34, track)
open(f'{RES}/drawable/tv_banner.xml', 'w').write(vector(
    320, 180, "The launcher's tile: the icon's film leader and the web player's wordmark (Fraunces 600, as outlines).",
    leader(x0 + mw / 2, 90, s) + [f'<path android:fillColor="{INK}" android:pathData="{word}" />']))
print('written')
