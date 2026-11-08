"""A small town on the board's town side (south of the loop): terraced rows of
low townhouses and cottages (at most 7.4 m, so the TV camera, 55 degrees down from the
south, sees over them), a market square with stalls round a fountain in the
south-east corner, front gardens. Our own designs in the hotels' style: matte
plaster walls with lit shop windows (materials.facade), clay and slate roofs.

A house goes where it keeps clear of the road (and a pavement), the water,
the plots, the Bank and the Rathaus, and where neither what it hides from the
TV (the ground up to 0.7x its height north of it) nor its evening shadow (~3x
its height east-north-east) falls on a plot, the Bank, the Rathaus or a special
field, and what it hides is not road. So every south hotel's front stays free."""

import math
import random

import bmesh
import bpy
import numpy as np

import board_ink as ink
import board_path as bp
import materials as m
import parts as p
from board_kit import BANK, FIELD_KINDS, KERB_W, SUN, SUN_UP, TV_PITCH
from lagune_kit import ring

MARKET = (43.0, -33.0, 13.0, 9.0)  # centre x, y; width, depth
TINTS = ("#f1e6cf", "#f3d9a6", "#e9c3a6", "#dfe0d2")  # plaster: cream, ochre, apricot, pale stone
CANVAS = ("#b8503c", "#4f7d5a", "#3f6e9e")
ROW_STEP = 7.6


def grid(box, step=1.0):
    x0, y0, x1, y1 = box
    xs = np.linspace(x0, x1, max(2, int((x1 - x0) / step) + 1))
    ys = np.linspace(y0, y1, max(2, int((y1 - y0) / step) + 1))
    return np.array([(x, y) for x in xs for y in ys])


def blocked(L, P, margin):
    """Points on the road (or its pavement), in the water, inside the loop, north of
    the town side, off the board, on a plot or by the Bank or Rathaus."""
    bad = (L.road(P) < bp.ROAD / 2 + KERB_W + margin) | (L.water(P) < BANK + margin) | L.inside(P)
    bad |= (P[:, 1] > -3.5) | (np.abs(P[:, 0]) > 63.6) | (P[:, 1] < -47.8)
    return bad | L.in_boxes(P, L.plot_boxes(margin) + [(a - margin, b - margin, c + margin, d + margin) for a, b, c, d in L.buildings])


def hides(L, box, height, special):
    """Whether a box this tall hides road, a plot or a building from the TV, or shades a plot, building or special field."""
    x0, y0, x1, y1 = box
    keep_out = L.plot_boxes(0.2) + L.buildings
    band = grid((x0, y1, x1, y1 + height / math.tan(TV_PITCH) + 0.5))
    if (L.road(band) < bp.ROAD / 2 + KERB_W + 0.2).any() or L.in_boxes(band, keep_out).any():
        return True
    top = grid(box, 1.5)
    for t in (0.25, 0.5, 0.75, 1.0):
        Q = top + np.array(SUN) * height / math.tan(SUN_UP) * t
        near = np.hypot(Q[:, None, 0] - special[None, :, 0], Q[:, None, 1] - special[None, :, 1]).min(1)
        if L.in_boxes(Q, keep_out).any() or (near < 3.4).any():
            return True
    return False


def lots(L, seed=5):
    """(houses, market box): each house (box, eave, rise, tint, slate roof, chimney), in rows west to east."""
    rng = random.Random(seed)
    special = np.array([(f["x"], f["y"]) for f in L.fields if FIELD_KINDS[f["id"]] != "."])
    cx, cy, mw, md = MARKET
    market = (cx - mw / 2, cy - md / 2, cx + mw / 2, cy + md / 2)
    assert not blocked(L, grid(market), 1.0).any() and not hides(L, market, 3.0, special), "market square not clear"
    taken, houses = [market], []
    for y0 in np.arange(-47.4, -8.0, ROW_STEP):
        x = -63.4
        while x < 60:
            w, d, eave, rise = rng.uniform(4.6, 6.4), rng.uniform(5.0, 6.4), rng.uniform(3.6, 5.4), rng.uniform(1.4, 2.0)
            inner = [(a + 0.01, b + 0.01, c - 0.01, e - 0.01) for a, b, c, e in taken]
            # a townhouse, or where its shadow or bulk would reach too far, a single-storey cottage
            fit = next(((box, e, r) for box, e, r in (((x, y0, x + w, y0 + d), eave, rise), ((x, y0, x + w, y0 + 4.6), 3.0, 1.3))
                        if x + w < 63.6 and not blocked(L, grid(box), 1.0).any() and not L.in_boxes(grid(box, 0.5), inner).any()
                        and not hides(L, box, e + r, special)), None)
            if fit:
                box, eave, rise = fit
                houses.append((box, eave, rise, rng.randrange(len(TINTS)), rng.random() < 0.3, rng.random() < 0.35))
                taken.append(box)
                x += w + 0.04  # terraced: a 4 cm gap keeps neighbours' walls apart
            else:
                x += 1.0
    return houses, market


def add_house(walls, roof, chimney, box, eave, rise, over=0.35):
    """Walls with gable ends (the ridge runs along x, the front faces the TV), a
    roof overhanging front and back only (neighbours' roofs never overlap)."""
    x0, y0, x1, y1 = box
    cy, top = (y0 + y1) / 2, eave + rise
    p.add_box(walls, x0, y0, 0, x1, y1, eave)
    for x, order in ((x0, (0, 2, 1)), (x1, (0, 1, 2))):
        v = [walls.verts.new(q) for q in ((x, y0, eave), (x, y1, eave), (x, cy, top))]
        walls.faces.new([v[i] for i in order])
    v = [roof.verts.new(q) for q in ((x0, y0 - over, eave - 0.01), (x1, y0 - over, eave - 0.01), (x1, y1 + over, eave - 0.01),
                                    (x0, y1 + over, eave - 0.01), (x0, cy, top + 0.01), (x1, cy, top + 0.01))]
    for f in ((0, 1, 5, 4), (2, 3, 4, 5), (0, 3, 2, 1)):  # slopes and soffit; the walls' gables close the ends
        roof.faces.new([v[i] for i in f])
    if chimney:
        cx = x0 + (x1 - x0) * 0.3
        p.add_box(chimney, cx - 0.3, cy + 0.5, eave, cx + 0.3, cy + 1.1, top + 0.5)


def canvas(name, color):
    """Stall awnings: the colour in stripes with cream."""
    g = m.Graph(name)
    band = g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", g.xyz.outputs["X"], 0.7)), 0.5)
    return g.color(g.mix(band, color, "#f4efe4"))


def build(houses, market):
    """The houses (one object per wall tint and roof kind), the market square with
    its fountain and six stalls, and lawn front gardens (bake-only decals)."""
    stone = m.textured("town_stone", "sandstone_blocks_05", 2.0, "#e2d6c0")
    wood = m.textured("town_wood", "weathered_plank_siding", 2.0, "#8a6a4c")
    walls = [m.facade(f"town_walls_{i}", wall_tint=t, ground_tint="#e8dcc6", frame="#e9e1d2", bay=2.2, storey=2.8, base=3.0)
             for i, t in enumerate(TINTS)]
    roofs = [m.textured("town_roof_clay", "clay_roof_tiles_02", 2.5, "#b86a52"), m.textured("town_roof_slate", "roof_slates_02", 2.5, "#6d7380")]
    bms = {k: bmesh.new() for k in [f"w{i}" for i in range(len(TINTS))] + ["r0", "r1", "c"]}
    for box, eave, rise, tint, slate, chim in houses:
        add_house(bms[f"w{tint}"], bms[f"r{int(slate)}"], bms["c"] if chim else None, box, eave, rise)
    mats = {**{f"w{i}": w for i, w in enumerate(walls)}, "r0": roofs[0], "r1": roofs[1], "c": stone}
    out = [_object(f"town_{k}", bm, mats[k]) for k, bm in bms.items()]
    out = [o for o in out if o.data.polygons]
    cx, cy = (market[0] + market[2]) / 2, (market[1] + market[3]) / 2
    ink.flat("ground_market", ink.rect(*market), lambda u, v: (u, v, 0.004), m.textured("town_cobbles", "cobblestone_square", 2.4, "#cbbfa9", "OVERLAY", 0.6))
    disc = lambda r: [(cx + r * math.cos(a), cy + r * math.sin(a)) for a in (2 * math.pi * k / 16 for k in range(16))]  # noqa: E731
    out.append(ring("town_fountain_basin", disc(1.7), disc(1.45), 0, 0.55, stone, None))
    out += [p.cylinder("town_fountain_water", cx, cy, 0, 0.42, 1.44, m.plain("town_water", "#4f9aa6"), segments=16),
            p.cylinder("town_fountain_column", cx, cy, 0.42, 1.6, 0.18, stone, segments=8),
            p.cylinder("town_fountain_bowl", cx, cy, 1.6, 1.85, 0.5, stone, r1=0.75, segments=12)]
    stalls = [(cx + dx, cy + dy) for dy in (-2.9, 2.9) for dx in (-4.2, 0.0, 4.2)]

    def counters(bm):
        for x, y in stalls:
            p.add_box(bm, x - 1.2, y - 0.55, 0, x + 1.2, y + 0.55, 0.9)
            for sx in (-1.25, 1.25):
                for sy in (-0.72, 0.72):
                    p.add_box(bm, x + sx - 0.04, y + sy - 0.04, 0, x + sx + 0.04, y + sy + 0.04, 2.35)

    out.append(p.mesh_object("town_stalls", counters, wood))
    for i, (x, y) in enumerate(stalls):
        out.append(p.gable_roof(f"town_stall_awning_{i}", x - 1.35, y - 0.85, x + 1.35, y + 0.85, 2.25, 0.45,
                                canvas(f"town_canvas_{i % 3}", CANVAS[i % 3]), overhang=0.0))
    gardens = [ink.rect(b[0] + 0.3, b[1] - 2.0, b[2] - 0.3, b[1] - 0.4)[0] for b, *_ in houses if b[1] - 2.0 > -47.8]
    # a lawn promenade before the south hotels' fronts, west of the market: flat, so it hides and shades nothing
    gardens += ink.rect(-50.0, -40.6, market[0] - 1.0, -36.2)
    ink.flat("ground_town_walk", ink.ribbon([(-50.0, -38.4), (market[0] - 1.0, -38.4)], 1.4), lambda u, v: (u, v, 0.006),
             m.textured("town_walk", "gravel_floor", 2.0, "#d6d0c4", "OVERLAY", 0.3))
    ink.flat("ground_town_gardens", gardens, lambda u, v: (u, v, 0.005), m.textured("town_garden", "sparse_grass", 4.0, "#6a9a44", "OVERLAY", 0.45))
    return out


def _object(name, bm, material):
    """A filled bmesh as an object, its winding kept (no normal recalculation)."""
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(material)
    return p.link(bpy.data.objects.new(name, me))
