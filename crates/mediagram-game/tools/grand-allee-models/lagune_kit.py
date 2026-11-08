"""Lagune's own shapes, materials and layout constants
(the stages are built in `lagune.py`)."""

import math
import bmesh
from mathutils import Matrix, Vector
import materials as m
import parts as p


STOREY = 2.6

PLOT = (20, 16)

FLOOR = 1.2  # the lodge's deck, on stilts above the high-water line

# the beach line: sand towards the road, the lagoon behind it
SHORE = [(-10, 3.6), (-6.5, 2.9), (-3.0, 1.6), (0.0, 0.2), (3.0, -1.4), (6.0, -2.7), (8.5, -3.3), (10, -3.6)]

def flat(name, outline, z0, z1, material, parent):
    """A flat shape of any plan outline (water, sand), z0..z1 thick."""
    return p.mesh_object(name, lambda bm: p.prism(bm, outline, z0, z1, lambda u, v, w: (u, v, w)), material, parent)

def ring(name, outer, inner, z0, z1, material, parent):
    """A closed band between two plan outlines of the same length (pool rims, bar counters)."""

    def build(bm):
        rows = [[bm.verts.new((x, y, z)) for x, y in pts] for pts, z in ((outer, z0), (outer, z1), (inner, z1), (inner, z0))]
        n = len(outer)
        for r in range(4):
            a, b = rows[r], rows[(r + 1) % 4]
            for i in range(n):
                j = (i + 1) % n
                bm.faces.new((a[i], a[j], b[j], b[i]))

    return p.mesh_object(name, build, material, parent)

def organic(cx, cy, rx, ry, n=18):
    """A kidney-ish outline, counter-clockwise; scaled copies stay parallel."""
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        k = 1 + 0.10 * math.sin(2 * a + 1.0) + 0.05 * math.sin(5 * a)
        pts.append((cx + rx * k * math.cos(a), cy + ry * k * math.sin(a)))
    return pts

def posts(name, points, z0, z1, radius, material, parent):
    """Stilts or poles, all in one object."""

    def build(bm):
        for x, y in points:
            bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=radius, radius2=radius * 0.85,
                                  depth=z1 - z0, matrix=Matrix.Translation((x, y, (z0 + z1) / 2)))

    return p.mesh_object(name, build, material, parent)

def boxes(name, spans, material, parent):
    """Several boxes, (lo, hi) each, in one object."""
    return p.mesh_object(name, lambda bm: [p.add_box(bm, *lo, *hi) for lo, hi in spans], material, parent)

def thatch(name):
    """Straw thatch without a scan: soft courses laid up the roof (wave bands
    in z) and darker fibre streaks running down it."""
    g = m.Graph(name)
    wave = g.nt.nodes.new("ShaderNodeTexWave")
    wave.bands_direction = "Z"
    wave.inputs["Scale"].default_value = 1.1
    wave.inputs["Distortion"].default_value = 4.0
    g.nt.links.new(g.obj, wave.inputs["Vector"])
    stretch = g.nt.nodes.new("ShaderNodeMapping")
    stretch.inputs["Scale"].default_value = (8.0, 8.0, 0.8)
    g.nt.links.new(g.obj, stretch.inputs["Vector"])
    streaks = g.nt.nodes.new("ShaderNodeTexNoise")
    streaks.inputs["Scale"].default_value = 3.0
    streaks.inputs["Detail"].default_value = 6.0
    g.nt.links.new(stretch.outputs["Vector"], streaks.inputs["Vector"])
    courses = g.mix(wave.outputs["Fac"], "#a8844c", "#cba660")
    return g.color(g.mix(1.0, courses, g.mix(streaks.outputs["Fac"], "#5e5e5e", "#ffffff"), "MULTIPLY"))

def thatched_roof(name, x0, y0, x1, y1, z, rise, straw, parent, overhang):
    """A hip roof with a thick straw edge: a second layer just under it shows at the eaves."""
    p.hip_roof(name, x0, y0, x1, y1, z, rise, straw, parent, overhang=overhang)
    p.hip_roof(name + "_eaves", x0, y0, x1, y1, z - 0.3, rise, straw, parent, overhang=overhang + 0.1)

def lagoon_water(name):
    """Turquoise over the sand shallows, deeper blue out in the lagoon."""
    g = m.Graph(name)
    x, y = g.xyz.outputs["X"], g.xyz.outputs["Y"]
    depth = g.op("DIVIDE", g.op("ADD", y, g.op("MULTIPLY", x, 0.45)), 7.0)
    return g.color(g.mix(depth, "#5ccdc2", "#1f6f8c"))

def bamboo(name):
    """Walls of upright bamboo canes: pale canes with dark seams, a little wood grain."""
    g = m.Graph(name)
    seam = g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", g.op("ADD", g.xyz.outputs["X"], g.xyz.outputs["Y"]), 0.16)), 0.18)
    return g.color(g.mix(seam, g.image("wood_floor_deck", 1.6, "#e6c995", "OVERLAY", 0.5), "#7a5a36"))

def lit(name, color, glow, strength=0.08):
    """Doors and windows with the lamps on inside: a warm glow, not a light."""
    g = m.Graph(name, color)
    g.bsdf.inputs["Emission Color"].default_value = (*m.hex_rgb(glow), 1)
    g.bsdf.inputs["Emission Strength"].default_value = strength
    return g.mat

def palm(name, x, y, height, lean, trunk_mat, leaf_mat, parent, fronds=9, length=2.1):
    """A palm: a curving, tapering trunk and a crown of arched fronds, all
    closed geometry (no cut-outs). `lean` is the crown's offset from the foot."""
    cx, cy = x + lean[0], y + lean[1]

    def trunk(bm):
        rings = []
        for i in range(6):
            f = i / 5
            ox, oy, r = x + lean[0] * f * f, y + lean[1] * f * f, 0.2 - 0.08 * f
            rings.append([bm.verts.new((ox + r * math.cos(a), oy + r * math.sin(a), height * f))
                          for a in (2 * math.pi * k / 6 for k in range(6))])
        for lo, hi in zip(rings, rings[1:]):
            for k in range(6):
                bm.faces.new((lo[k], lo[(k + 1) % 6], hi[(k + 1) % 6], hi[k]))
        bm.faces.new(rings[0])
        bm.faces.new(rings[-1])

    def crown(bm):
        c, up = Vector((cx, cy, height)), Vector((0, 0, 1))
        for k in range(fronds):
            a = 2 * math.pi * k / fronds + 0.4
            d = Vector((math.cos(a), math.sin(a), 0))
            side = Vector((-d.y, d.x, 0))
            droop = 1.1 if k % 2 else 0.7
            sections = [(c + d * 0.15, 0.1), (c + d * length * 0.45 + up * 0.35, 0.42),
                        (c + d * length * 0.8 - up * droop * 0.5, 0.26)]
            rings = [[bm.verts.new(q) for q in (s - side * w, s + up * 0.05, s + side * w, s - up * 0.05)] for s, w in sections]
            tip = bm.verts.new(c + d * length - up * droop)
            bm.faces.new(rings[0])
            for lo, hi in zip(rings, rings[1:]):
                for i in range(4):
                    bm.faces.new((lo[i], lo[(i + 1) % 4], hi[(i + 1) % 4], hi[i]))
            for i in range(4):
                bm.faces.new((rings[-1][i], rings[-1][(i + 1) % 4], tip))

    return p.mesh_object(name + "_trunk", trunk, trunk_mat, parent), p.mesh_object(name + "_fronds", crown, leaf_mat, parent)

def bungalow(tag, cx, cy, mats, parent):
    """An overwater bungalow: a deck on stilts, plank walls, a lit door and a
    steep thatched roof; its front faces the road."""
    wood, deck, walls, straw, glow = mats
    floor, half = 1.4, 1.75
    posts(f"{tag}_stilts", [(cx + dx, cy + dy) for dx in (-half, half) for dy in (-half, half)], 0, floor - 0.15, 0.1, wood, parent)
    p.box(f"{tag}_deck", (cx - 2.0, cy - 2.0, floor - 0.15), (cx + 2.0, cy + 2.0, floor), deck, parent)
    p.box(f"{tag}_walls", (cx - 1.3, cy - 1.0, floor), (cx + 1.3, cy + 1.6, floor + 2.4), walls, parent)
    boxes(f"{tag}_openings", [((cx - 0.45, cy - 1.06, floor), (cx + 0.45, cy - 0.98, floor + 1.9)),
                              ((cx - 1.36, cy - 0.2, floor + 0.8), (cx - 1.28, cy + 0.9, floor + 1.7))], glow, parent)
    thatched_roof(f"{tag}_roof", cx - 1.3, cy - 1.0, cx + 1.3, cy + 1.6, floor + 2.4, 2.8, straw, parent, 0.8)
