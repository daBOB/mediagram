"""Savanne (P3, park side, an African safari lodge): a main lodge under a
steep thatched roof on timber posts, raised on a deck, with a log gate and
sign at the road; round thatched guest huts (rondavels) to either side, a
lookout tower over a fire circle (boma), acacias with flat crowns in dry
grass; its leisure wing is a watering-hole pool among rocks with a timber
deck. Five stages.

Plot: a park plot, 32 m along the road (x -16..16) and 14 m deep (y -7..7);
the road runs along y = -7, so the fronts face -y."""

import math
import bmesh
from mathutils import Matrix, Vector
import materials as m
import parts as p
from savanne_kit import (  # noqa: F401  (PLOT is read by build.py)
    HUT,
    PLOT,
    acacia,
    cylinder,
    earth_wall,
    grass_tufts,
    rondavel,
    strut,
    thatch,
    thatch_hip,
    water_hole,
)


def build(owner_hex):
    straw = thatch("sav_thatch")
    walls = earth_wall("sav_hut_walls")
    lodge_walls = m.facade("sav_lodge_walls", ground="stone_wall", ground_tint="#c9a27a", bay=2.1, base=4.0)
    ground = m.textured("sav_dry_grass", "sparse_grass", 4.0, "#a9965c", "OVERLAY", 0.55)
    tufts = m.plain("sav_tufts", "#a88c4c")
    deck = m.textured("sav_deck", "wood_floor_deck", 2.0, "#a8835a", "OVERLAY", 0.6)
    timber = m.textured("sav_timber", "weathered_plank_siding", 2.0, "#6a4a32", "OVERLAY", 0.6)
    stone = m.textured("sav_stone", "stone_wall", 2.0, "#bfa588")
    sand = m.textured("sav_sand", "gravel_floor", 1.5, "#d6b98e", "OVERLAY", 0.5)
    rock = m.textured("sav_rock", "sandstone_blocks_05", 2.0, "#b48f6a")
    water = m.plain("sav_water", "#4a8a86")
    ember = m.plain("sav_ember", "#3a2a20")
    bark = m.plain("sav_bark", "#4a3a2c")
    leaves = m.plain("sav_leaves", "#5d6e2e")
    canvas = m.plain("sav_canvas", "#e9e0cc")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("sav_tint_cloth", owner_hex)
    stripes = m.striped("sav_tint_stripes", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 6)]
    huts = [(-10.6, 3.9), (-13.4, -1.4), (10.6, 3.9), (13.4, -1.4)]
    pool = (7.0, -4.4, 3.4, 1.8)
    tower, boma = (-13.6, -5.0), (-8.4, -4.4)

    # 1 the lodge: a deck on the dry grass, a glazed stone core, a ring of posts under a steep thatch;
    #   a log gate with the sign at the road, two acacias
    s = stages[0]
    p.box("ground_dry_grass", (-16, -7, 0), (16, 7, 0.02), ground, s)
    keep_out = [(-6.4, -2.4, 6.4, 6.3), (-1.4, -7, 1.4, -1.4), (-2.3, -7, 2.3, -5.7), (6.0, -2.6, 11.4, -0.4)]
    keep_out += [(hx, hy, HUT + 0.9) for hx, hy in huts] + [(*tower, 2.0), (*boma, 2.6), (pool[0], pool[1], 4.6)]
    grass_tufts("ground_tufts", tufts, s, keep_out)
    p.box("lodge_deck", (-6.0, -1.5, 0), (6.0, 5.5, 0.6), deck, s)
    p.box("lodge_step", (-1.2, -2.1, 0), (1.2, -1.5, 0.3), deck, s)
    p.box("lodge_core", (-4.2, 1.0, 0.6), (4.2, 5.0, 3.6), lodge_walls, s)
    x0, y0, x1, y1, eaves = -5.6, -1.1, 5.6, 5.1, 3.85

    def posts(bm):
        # the posts stop 5 cm into the ring beam: ending at its top they would z-fight there
        top = eaves - 0.05
        for k in range(6):
            px = x0 + (x1 - x0) * k / 5
            for py in (y0, y1):
                bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=0.15, radius2=0.13, depth=top - 0.6,
                                      matrix=Matrix.Translation((px, py, (top + 0.6) / 2)))
        for py in (y0 + 2.07, y0 + 4.13):
            for px in (x0, x1):
                bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=0.15, radius2=0.13, depth=top - 0.6,
                                      matrix=Matrix.Translation((px, py, (top + 0.6) / 2)))
        p.add_box(bm, x0 - 0.12, y0 - 0.12, eaves - 0.3, x1 + 0.12, y0 + 0.12, eaves)
        p.add_box(bm, x0 - 0.12, y1 - 0.12, eaves - 0.3, x1 + 0.12, y1 + 0.12, eaves)
        p.add_box(bm, x0 - 0.12, y0 + 0.12, eaves - 0.3, x0 + 0.12, y1 - 0.12, eaves)
        p.add_box(bm, x1 - 0.12, y0 + 0.12, eaves - 0.3, x1 + 0.12, y1 - 0.12, eaves)

    p.mesh_object("lodge_posts", posts, timber, s)
    thatch_hip("lodge_roof", x0, y0, x1, y1, eaves, 4.6, straw, s, overhang=1.0, thick=0.45)
    def blinds(bm):  # canvas blinds half let down between the front posts, the entrance bay open
        for k in (0, 1, 3, 4):
            p.add_box(bm, x0 + (x1 - x0) * k / 5 + 0.25, y0 - 0.05, 2.7, x0 + (x1 - x0) * (k + 1) / 5 - 0.25, y0 + 0.02, eaves - 0.3)

    p.mesh_object("tint_lodge_blinds", blinds, cloth, s)
    p.box("gate_path", (-1.0, -6.99, 0), (1.0, -2.1, 0.04), sand, s)  # 1 cm in from the plot's edge

    def gate(bm):
        strut(bm, (-1.7, -6.3, 0), (-1.7, -6.3, 4.0), 0.17)
        strut(bm, (1.7, -6.3, 0), (1.7, -6.3, 4.0), 0.17)
        strut(bm, (-2.2, -6.3, 3.6), (2.2, -6.3, 3.6), 0.15)

    p.mesh_object("gate_logs", gate, timber, s)
    p.box("tint_gate_sign", (-1.25, -6.36, 2.6), (1.25, -6.24, 3.4), cloth, s)
    acacia("lodge_acacia_0", -7.6, 3.6, 7.8, 3.4, bark, leaves, s, seed=1)
    acacia("lodge_acacia_1", 8.4, 0.9, 6.6, 4.6, bark, leaves, s, seed=2)

    # 2 two rondavels to the west, 3 two to the east
    for stage, pair, side in ((stages[1], huts[:2], "west"), (stages[2], huts[2:], "east")):
        for k, (hx, hy) in enumerate(pair):
            rondavel(f"{side}_hut_{k}", hx, hy, walls, straw, stone, timber, stage)

    # 4 the boma and its lookout tower: a fire circle with log seats, a thatched platform on stilts
    s = stages[3]
    bx, by = boma
    cylinder("boma_firepit", bx, by, 0, 0.3, 0.8, stone, s, segments=10)
    cylinder("boma_embers", bx, by, 0.3, 0.32, 0.6, ember, s, segments=10)

    def seats(bm):
        for k in range(5):
            a = 2 * math.pi * (k + 0.5) / 5
            c = Vector((bx + 2.0 * math.cos(a), by + 2.0 * math.sin(a), 0.25))
            t = Vector((-math.sin(a), math.cos(a), 0)) * 0.7
            strut(bm, c - t, c + t, 0.22, segments=6)

    p.mesh_object("boma_seats", seats, timber, s)
    tx, ty = tower
    deck_z, roof_z = 3.4, 5.6

    def stilts(bm):
        for dx in (-1.0, 1.0):
            for dy in (-1.0, 1.0):
                strut(bm, (tx + dx * 1.15, ty + dy * 1.15, 0), (tx + dx, ty + dy, roof_z), 0.12)
        strut(bm, (tx - 1.1, ty - 1.1, 0.4), (tx + 1.05, ty - 1.05, deck_z - 0.2), 0.06)
        p.add_box(bm, tx - 1.25, ty - 1.25, deck_z, tx + 1.25, ty + 1.25, deck_z + 0.2)
        rz0, rz1 = deck_z + 0.9, deck_z + 1.0  # the railing round the platform
        p.add_box(bm, tx - 1.25, ty - 1.25, rz0, tx + 1.25, ty - 1.15, rz1)
        p.add_box(bm, tx - 1.25, ty + 1.15, rz0, tx + 1.25, ty + 1.25, rz1)
        p.add_box(bm, tx - 1.25, ty - 1.15, rz0, tx - 1.15, ty + 1.15, rz1)
        p.add_box(bm, tx + 1.15, ty - 1.15, rz0, tx + 1.25, ty + 1.15, rz1)
        for k in range(7):  # ladder rungs up the east side
            p.add_box(bm, tx + 1.3, ty - 0.35, 0.4 + k * 0.45, tx + 1.4, ty + 0.35, 0.46 + k * 0.45)
        p.add_box(bm, tx + 1.3, ty - 0.4, 0, tx + 1.4, ty - 0.33, deck_z + 1.0)
        p.add_box(bm, tx + 1.3, ty + 0.33, 0, tx + 1.4, ty + 0.4, deck_z + 1.0)

    p.mesh_object("tower_frame", stilts, timber, s)
    thatch_hip("tower_roof", tx - 1.0, ty - 1.0, tx + 1.0, ty + 1.0, roof_z, 1.8, straw, s, overhang=0.6, thick=0.3)
    p.post("tower_flagpole", tx, ty, roof_z + 1.4, roof_z + 3.0, 0.04, timber, s, segments=6)
    p.box("tint_tower_flag", (tx + 0.04, ty - 0.02, roof_z + 2.1), (tx + 1.3, ty + 0.02, roof_z + 2.9), cloth, s)
    acacia("boma_acacia", -4.3, -4.4, 5.8, 2.6, bark, leaves, s, seed=4)

    # 5 the watering hole: an irregular pool among rocks, a timber deck with loungers and parasols
    s = stages[4]
    water_hole("pool", *pool, sand, water, rock, s)
    p.box("pool_deck", (6.0, -2.4, 0), (11.2, -0.6, 0.4), deck, s)
    p.box("pool_jetty", (7.4, -3.6, 0.12), (9.0, -2.4, 0.4), deck, s)
    for k, x in enumerate((6.6, 7.6)):
        p.box(f"pool_lounger_{k}", (x - 0.35, -2.2, 0.4), (x + 0.35, -0.8, 0.7), canvas, s)
    for k, x in enumerate((9.2, 10.5)):
        stick, _ = p.parasol(f"tint_pool_parasol_{k}", x, -1.4, stripes, timber, s, radius=1.0)
        stick.name = stick.name.removeprefix("tint_")

    return stages
