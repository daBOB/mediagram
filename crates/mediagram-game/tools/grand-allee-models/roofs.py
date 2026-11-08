"""Roof shapes for the Grand Allee hotels: hip, gable and cone roofs and onion domes."""

import math

import bmesh
from mathutils import Matrix

from geometry import mesh_object


def hip_roof(name, x0, y0, x1, y1, z, rise, material, parent=None, overhang=0.4):
    """A hip roof over the rectangle, eaves at height z, ridge `rise` above."""
    x0, y0, x1, y1 = x0 - overhang, y0 - overhang, x1 + overhang, y1 + overhang

    def build(bm):
        w, d = x1 - x0, y1 - y0
        cx, cy, inset = (x0 + x1) / 2, (y0 + y1) / 2, min(w, d) / 2
        if w >= d:
            r0, r1 = (x0 + inset, cy, z + rise), (x1 - inset, cy, z + rise)
        else:
            r0, r1 = (cx, y0 + inset, z + rise), (cx, y1 - inset, z + rise)
        a, b, c, e = (bm.verts.new(p) for p in [(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)])
        p, q = bm.verts.new(r0), bm.verts.new(r1)
        if w >= d:
            faces = [(a, b, q, p), (c, e, p, q), (b, c, q), (e, a, p)]
        else:
            faces = [(b, c, q, p), (e, a, p, q), (a, b, p), (c, e, q)]
        for f in faces:
            bm.faces.new(f)
        bm.faces.new((a, e, c, b))  # soffit, so the eaves read from below

    return mesh_object(name, build, material, parent)


def gable_roof(name, x0, y0, x1, y1, z, rise, material, parent=None, overhang=0.4, ridge_along="x"):
    """A pitched roof with two slopes and two gable triangles; the ridge runs
    along x (gables at the sides) or along y (a gable facing the road)."""
    x0, y0, x1, y1 = x0 - overhang, y0 - overhang, x1 + overhang, y1 + overhang

    def build(bm):
        a, b, c, d = (bm.verts.new(q) for q in [(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)])
        if ridge_along == "x":
            cy = (y0 + y1) / 2
            p, q = bm.verts.new((x0, cy, z + rise)), bm.verts.new((x1, cy, z + rise))
            faces = [(a, b, q, p), (c, d, p, q), (b, c, q), (d, a, p)]
        else:
            cx = (x0 + x1) / 2
            p, q = bm.verts.new((cx, y0, z + rise)), bm.verts.new((cx, y1, z + rise))
            faces = [(b, c, q, p), (d, a, p, q), (a, b, p), (c, d, q)]
        for f in faces:
            bm.faces.new(f)
        bm.faces.new((a, d, c, b))

    return mesh_object(name, build, material, parent)


def cone_roof(name, x, y, z, radius, rise, material, parent=None, segments=12):
    """A round pointed roof (thatched huts, towers)."""

    def build(bm):
        bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=radius, radius2=0.02, depth=rise,
                              matrix=Matrix.Translation((x, y, z + rise / 2)))

    return mesh_object(name, build, material, parent)


def onion_dome(name, x, y, z, radius, material, parent=None, segments=16):
    """An onion dome: a swelling bulb drawn in to a point, sitting at height z."""

    def build(bm):
        # profile (radius factor, height factor) from the base to the tip
        profile = [(0.85, 0.0), (1.0, 0.25), (1.08, 0.5), (0.95, 0.8), (0.6, 1.1), (0.25, 1.35), (0.02, 1.7)]
        rings = []
        for rf, hf in profile:
            ring = [bm.verts.new((x + radius * rf * math.cos(2 * math.pi * i / segments),
                                  y + radius * rf * math.sin(2 * math.pi * i / segments), z + radius * hf))
                    for i in range(segments)]
            rings.append(ring)
        for lo, hi in zip(rings, rings[1:]):
            for i in range(segments):
                bm.faces.new((lo[i], lo[(i + 1) % segments], hi[(i + 1) % segments], hi[i]))
        bm.faces.new(list(reversed(rings[0])))
        bm.faces.new(rings[-1])

    return mesh_object(name, build, material, parent)

