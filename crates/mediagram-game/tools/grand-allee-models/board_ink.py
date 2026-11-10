"""Flat 'ink' for the board and the dice: polygons drawn on a plane (painted
icons, letters, pips, signs). They exist only in the scenes the bakes look at;
the GLBs carry them as texture, never as geometry. Icons, with y up:
coin, pediment, staked plot, crane, door with a star, and the permit die's gift."""

import math

import bmesh
import bpy
from mathutils import Vector

import parts as p
from board_kit import area, offset


def flat(name, polys, P, material, parent=None):
    """One object from 2D polygons; P maps (u, v) to a point in space. Each face
    is wound so it faces the way P's plane does (no normal recalculation), and
    each sits 0.05 mm above the one before: where polygons overlap, the top one
    then sees open sky instead of its coplanar twin (which darkens the overlap)."""
    o = Vector(P(0, 0))
    n = (Vector(P(1, 0)) - o).cross(Vector(P(0, 1)) - o).normalized() * 5e-5
    verts, faces = [], []
    for i, poly in enumerate(polys):
        poly = poly if area(poly) > 0 else poly[::-1]
        faces.append(list(range(len(verts), len(verts) + len(poly))))
        verts += [Vector(P(u, v)) + n * i for u, v in poly]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.materials.append(material)
    return decal(p.link(bpy.data.objects.new(name, me), parent))


def decal(obj):
    """Decals cast no shadows: a stroke's overlapping quads and discs would shade each
    other (Cycles' shadow rays hit the coplanar twin) and paint the stroke black."""
    obj.visible_shadow = False
    return obj


def ellipse(cx, cy, rx, ry, rot=0.0, n=24):
    c, s = math.cos(rot), math.sin(rot)
    return [(cx + rx * math.cos(a) * c - ry * math.sin(a) * s, cy + rx * math.cos(a) * s + ry * math.sin(a) * c)
            for a in (2 * math.pi * i / n for i in range(n))]


def disc(cx, cy, r, n=20):
    return [ellipse(cx, cy, r, r, 0, n)]


def ring(cx, cy, rx, ry, w, rot=0.0, n=28):
    """An outline of an ellipse, `w` wide, as quads."""
    a, b = ellipse(cx, cy, rx + w / 2, ry + w / 2, rot, n), ellipse(cx, cy, rx - w / 2, ry - w / 2, rot, n)
    return [[a[i], a[(i + 1) % n], b[(i + 1) % n], b[i]] for i in range(n)]


def rect(x0, y0, x1, y1):
    return [[(x0, y0), (x1, y0), (x1, y1), (x0, y1)]]


def stroke(pts, w):
    """A polyline drawn with a round pen `w` wide."""
    out = []
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        L = math.hypot(bx - ax, by - ay)
        nx, ny = (ay - by) / L * w / 2, (bx - ax) / L * w / 2
        out.append([(ax + nx, ay + ny), (ax - nx, ay - ny), (bx - nx, by - ny), (bx + nx, by + ny)])
    for x, y in pts:
        out += disc(x, y, w / 2, 12)
    return out


def ribbon(line, w):
    """A polyline as one band of quads `w` wide with flat ends: long paths
    without the overlaps of a round pen's joints."""
    a, b = offset(line, w / 2), offset(line, -w / 2)
    return [[a[i], b[i], b[i + 1], a[i + 1]] for i in range(len(line) - 1)]


def dashes(pts, w, on, off):
    """A closed outline drawn as dashes."""
    out, carry = [], 0.0
    for a, b in zip(pts, pts[1:] + pts[:1]):
        L = math.dist(a, b)
        t = carry
        while t < L:
            t1 = min(t + on, L)
            out += stroke([(a[0] + (b[0] - a[0]) * t / L, a[1] + (b[1] - a[1]) * t / L),
                           (a[0] + (b[0] - a[0]) * t1 / L, a[1] + (b[1] - a[1]) * t1 / L)], w)
            t = t1 + off
        carry = t - L
    return out


def icon(kind, s):
    """The icon's polygons, centred on (0, 0), about `s` across."""
    S = lambda pts: [(x * s, y * s) for x, y in pts]  # noqa: E731
    if kind == "bank":  # a coin with a T (Taler)
        return ring(0, 0, 0.4 * s, 0.4 * s, 0.09 * s) + rect(-0.2 * s, 0.13 * s, 0.2 * s, 0.22 * s) + rect(-0.045 * s, -0.22 * s, 0.045 * s, 0.2 * s)
    if kind == "rathaus":  # a pediment over four columns
        cols = [r for i in range(4) for r in rect((-0.36 + 0.22 * i) * s, -0.3 * s, (-0.26 + 0.22 * i) * s, 0.1 * s)]
        return [S([(-0.46, 0.16), (0, 0.44), (0.46, 0.16)])] + cols + rect(-0.46 * s, -0.42 * s, 0.46 * s, -0.32 * s)
    if kind == "plot":  # a staked plot with a flag
        return (dashes(S([(-0.4, -0.37), (0.4, -0.37), (0.4, 0.05), (-0.4, 0.05)]), 0.06 * s, 0.09 * s, 0.07 * s)
                + rect(-0.05 * s, -0.09 * s, 0.02 * s, 0.46 * s) + [S([(0.02, 0.46), (0.34, 0.34), (0.02, 0.22)])])
    if kind == "crane":  # mast, jib and brace, the hook's line and its load, a footing
        return (stroke(S([(-0.26, -0.42), (-0.26, 0.38), (0.42, 0.38)]), 0.08 * s) + stroke(S([(-0.26, 0.1), (0, 0.38)]), 0.08 * s)
                + stroke(S([(0.3, 0.38), (0.3, 0.02)]), 0.04 * s) + rect(0.2 * s, -0.14 * s, 0.4 * s, 0.02 * s) + rect(-0.42 * s, -0.44 * s, -0.08 * s, -0.36 * s))
    if kind == "door":  # a door with a star above
        star = [(math.cos(a) * r * s, (0.28 - math.sin(a) * r) * s)
                for a, r in ((-math.pi / 2 + i * math.pi / 5, 0.08 if i % 2 else 0.19) for i in range(10))]
        return stroke(S([(-0.2, -0.42), (-0.2, 0.06), (0.2, 0.06), (0.2, -0.42)]), 0.08 * s) + disc(0.1 * s, -0.2 * s, 0.04 * s) + [star]
    if kind == "gift":  # a parcel with a ribbon and a bow: the ribbon is the gap between the halves
        return (rect(-0.26 * s, -0.26 * s, -0.035 * s, 0.04 * s) + rect(0.035 * s, -0.26 * s, 0.26 * s, 0.04 * s)
                + rect(-0.3 * s, 0.06 * s, -0.035 * s, 0.17 * s) + rect(0.035 * s, 0.06 * s, 0.3 * s, 0.17 * s)
                + ring(-0.1 * s, 0.25 * s, 0.1 * s, 0.055 * s, 0.05 * s, -0.4) + ring(0.1 * s, 0.25 * s, 0.1 * s, 0.055 * s, 0.05 * s, 0.4))
    if kind == "ok":  # the permit die's tick
        return stroke(S([(-0.26, -0.02), (-0.07, -0.21), (0.28, 0.2)]), 0.13 * s)
    if kind == "no":  # and its cross
        return stroke(S([(-0.22, -0.22), (0.22, 0.22)]), 0.13 * s) + stroke(S([(0.22, -0.22), (-0.22, 0.22)]), 0.13 * s)
    raise ValueError(kind)


def text(name, body, P_matrix, material, height, max_width, weight=0.02, parent=None):
    """Letters as a flat mesh, centred, scaled to `height` (cap height and
    descenders) or down to `max_width`; P_matrix places the text plane."""
    cu = bpy.data.curves.new(name, "FONT")
    cu.body, cu.align_x, cu.align_y, cu.offset, cu.resolution_u = body, "CENTER", "CENTER", weight, 3
    tmp = p.link(bpy.data.objects.new(name + "_font", cu))
    me = bpy.data.meshes.new_from_object(tmp.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    bpy.data.objects.remove(tmp)
    xs, ys = [v.co.x for v in me.vertices], [v.co.y for v in me.vertices]
    k = min(height / (max(ys) - min(ys)), max_width / (max(xs) - min(xs)))
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    for v in me.vertices:
        v.co = P_matrix((v.co.x - cx) * k, (v.co.y - cy) * k)
    me.materials.clear()
    me.materials.append(material)
    return decal(p.link(bpy.data.objects.new(name, me), parent))


def solid(name, build, material):
    """Like parts.mesh_object, but keeps the winding `build` gives: recalculating
    the normals of open strips (no bottoms) can turn them all inside out."""
    bm = bmesh.new()
    build(bm)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(material)
    return p.link(bpy.data.objects.new(name, me))
