"""Riad Atlas's own shapes, materials and layout constants
(the stages are built in `riad_atlas.py`)."""

from functools import partial
import math
import bmesh
from mathutils import Matrix, Vector
import materials as m
import parts as p


STOREY = 3.2

PLOT = (20, 16)

def arch_wall(name, tint, bay=2.0, half=0.42, sill=0.9, ground_sill=0.3, spring=2.0,
              frame="#ecd9b8", dark="#3b2a1f", wood="#3a2618", glow="#ffa552"):
    """Rendered wall with a horseshoe-arched opening per bay and storey: a
    cream surround, warm light inside, a wooden lattice (mashrabiya) upstairs.

    du is metres from the bay centre (x + y works on axis-aligned blocks),
    vz metres up the storey; the arch circle is wider than the jambs and its
    centre sits above the springing line, which makes the horseshoe."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    du = g.op("MULTIPLY", g.op("SUBTRACT", g.op("FRACT", g.op("DIVIDE", g.op("ADD", x, y), bay)), 0.5), bay)
    vz = g.op("MULTIPLY", g.op("FRACT", g.op("DIVIDE", z, STOREY)), STOREY)
    ground = g.op("LESS_THAN", z, STOREY)
    sill_z = g.op("ADD", sill, g.op("MULTIPLY", ground, ground_sill - sill))
    radius = half * 1.2
    centre = spring + math.sqrt(radius ** 2 - half ** 2)

    def shape(grow):
        rect = g.op("MULTIPLY", g.op("LESS_THAN", g.op("ABSOLUTE", du), half + grow),
                    g.within(vz, g.op("SUBTRACT", sill_z, grow), spring))
        dz = g.op("SUBTRACT", vz, centre)
        circ = g.op("LESS_THAN", g.op("ADD", g.op("MULTIPLY", du, du), g.op("MULTIPLY", dz, dz)), (radius + grow) ** 2)
        return g.op("MAXIMUM", rect, circ)

    opening, surround = shape(0.0), shape(0.14)
    lattice = g.op("MAXIMUM", g.op("LESS_THAN", g.op("FRACT", g.op("MULTIPLY", g.op("ADD", du, vz), 3.5)), 0.3),
                   g.op("LESS_THAN", g.op("FRACT", g.op("MULTIPLY", g.op("SUBTRACT", du, vz), 3.5)), 0.3))
    lattice = g.op("MULTIPLY", lattice, g.op("SUBTRACT", 1.0, ground))
    body = g.image("plastered_wall_04", 3.0, tint, "OVERLAY", 0.55)
    body = g.mix(g.op("LESS_THAN", z, 0.45), body, "#8e4a32")
    inside = g.mix(lattice, dark, wood)
    g.color(g.mix(opening, g.mix(surround, body, frame), inside))
    lit = g.op("MULTIPLY", opening, g.op("SUBTRACT", 1.0, lattice))
    g.nt.links.new(g.mix(lit, "#000000", glow), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.25
    return g.mat

def zellige(name, a, b, size, border=None):
    """Tiles laid on the diagonal in two colours (bejmat floors, fountain
    basins), with the plaster scan's grain over them."""
    g = m.Graph(name)
    x, y = g.xyz.outputs["X"], g.xyz.outputs["Y"]
    cell = g.op("ADD", g.op("FLOOR", g.op("DIVIDE", g.op("ADD", x, y), size)),
                g.op("FLOOR", g.op("DIVIDE", g.op("SUBTRACT", x, y), size)))
    odd = g.op("FLOORED_MODULO", cell, 2.0)
    return g.color(g.mix(0.35, g.mix(odd, a, b), g.image("plastered_wall_04", 2.0, "#ffffff"), "OVERLAY"))

def fruit_foliage(name, leaf, fruit, scale=2.5, size=0.25):
    """Dark glossy-free leaves dotted with fruit (a Voronoi cell's centre)."""
    g = m.Graph(name)
    vor = g.nt.nodes.new("ShaderNodeTexVoronoi")
    vor.inputs["Scale"].default_value = scale
    g.nt.links.new(g.obj, vor.inputs["Vector"])
    return g.color(g.mix(g.op("LESS_THAN", vor.outputs["Distance"], size), leaf, fruit))

def horseshoe(name, cx, y, z0, half, spring, material, parent=None, segments=14):
    """A flat horseshoe-arch panel facing -y (a door, or its surround)."""
    radius = half * 1.2
    zc = spring + math.sqrt(radius ** 2 - half ** 2)
    beta = math.atan2(zc - spring, half)

    def build(bm):
        pts = [(cx - half, z0), (cx - half, spring)]
        for i in range(1, segments):
            a = math.pi + beta - (math.pi + 2 * beta) * i / segments
            pts.append((cx + radius * math.cos(a), zc + radius * math.sin(a)))
        pts += [(cx + half, spring), (cx + half, z0)]
        # anticlockwise seen from the road, so the face looks out (-y)
        bm.faces.new([bm.verts.new((px, y, pz)) for px, pz in reversed(pts)])

    return p.mesh_object(name, build, material, parent)

def flat_roof(prefix, x0, y0, x1, y1, top, wall, roof, parent, merlon=0.9, t=0.3, h=0.8):
    """Roof terrace behind a parapet crowned with merlons; the terrace slab
    hides the wall block's top face."""
    def ring(bm):
        p.add_box(bm, x0, y0, top, x1, y0 + t, top + h)
        p.add_box(bm, x0, y1 - t, top, x1, y1, top + h)
        p.add_box(bm, x0, y0 + t, top, x0 + t, y1 - t, top + h)
        p.add_box(bm, x1 - t, y0 + t, top, x1, y1 - t, top + h)
        if not merlon:
            return
        for (ax, ay), (bx, by) in (((x0, y0), (x1, y0)), ((x0, y1), (x1, y1)), ((x0, y0), (x0, y1)), ((x1, y0), (x1, y1))):
            n = max(2, round(math.hypot(bx - ax, by - ay) / merlon))
            for i in range(n):
                f = (i + 0.5) / n
                mx = ax + (bx - ax) * f + (t / 2 if ax == bx == x0 else -t / 2 if ax == bx == x1 else 0)
                my = ay + (by - ay) * f + (t / 2 if ay == by == y0 else -t / 2 if ay == by == y1 else 0)
                p.add_box(bm, mx - 0.18, my - 0.15, top + h, mx + 0.18, my + 0.15, top + h + 0.45)

    p.mesh_object(prefix + "_parapet", ring, wall, parent)
    p.box(prefix + "_terrace", (x0 + t, y0 + t, top), (x1 - t, y1 - t, top + 0.1), roof, parent)

def palm(name, x, y, height, lean, trunk, leaves, parent, fronds=8, length=2.6):
    """A date palm: a leaning ringed trunk and a crown of arching fronds,
    each frond a closed folded strip (geometry, no cut-outs)."""
    crown = Vector((x + lean[0], y + lean[1], height))

    def stem(bm):
        axis = crown - Vector((x, y, 0))
        rot = axis.to_track_quat("Z", "Y").to_matrix().to_4x4()
        bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=0.24, radius2=0.15, depth=axis.length,
                              matrix=Matrix.Translation((Vector((x, y, 0)) + crown) / 2) @ rot)

    def crown_fronds(bm):
        for k in range(fronds):
            a = 2 * math.pi * (k + 0.3 * (k % 2)) / fronds
            d, side = Vector((math.cos(a), math.sin(a), 0)), Vector((-math.sin(a), math.cos(a), 0))
            droop = 0.55 + 0.25 * (k % 3) / 2
            top, bottom = [], []
            for t in (0.0, 0.35, 0.7, 1.0):
                c = crown + d * length * t + Vector((0, 0, length * (0.35 * t - droop * t * t)))
                w = 0.08 + 0.42 * math.sin(math.pi * min(t, 0.9))
                row = [c + side * w - Vector((0, 0, 0.12 * w)), c + Vector((0, 0, 0.06)), c - side * w - Vector((0, 0, 0.12 * w))]
                top.append([bm.verts.new(v) for v in row])
                bottom.append([bm.verts.new(v - Vector((0, 0, 0.05))) for v in row])
            for rows, flip in ((top, False), (bottom, True)):
                for r0, r1 in zip(rows, rows[1:]):
                    for i in range(2):
                        f = [r0[i], r0[i + 1], r1[i + 1], r1[i]]
                        bm.faces.new(f[::-1] if flip else f)

    p.mesh_object(name + "_trunk", stem, trunk, parent)
    p.mesh_object(name + "_fronds", crown_fronds, leaves, parent)
    p.blob(name + "_dates", tuple(crown - Vector((0, 0, 0.25))), 0.32, trunk, parent, squash=(1, 1, 0.8))


# the shared cylinder at this hotel's own segment count
cylinder = partial(p.cylinder, segments=8)
