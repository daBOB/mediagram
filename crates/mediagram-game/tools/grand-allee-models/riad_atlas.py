"""Riad Atlas (S2, south side, Morocco): a three-storey riad of clay-red
rendered walls with horseshoe-arched windows and flat roofs behind stepped
parapets, a slender kasbah tower with a green-tiled lantern and a carved gate;
two two-storey wings frame a forecourt whose zellige floor, fountain, orange
trees and palms are its leisure wing. Four stages.

Plot: 20 m along the road (x -10..10), 16 m deep (y -8..8); the road runs
along y = -8, so the fronts face -y."""

import materials as m
import parts as p
from riad_atlas_kit import (  # noqa: F401  (PLOT is read by build.py)
    PLOT,
    STOREY,
    arch_wall,
    cylinder,
    flat_roof,
    fruit_foliage,
    horseshoe,
    palm,
    zellige,
)


def build(owner_hex):
    clay = "#b35a43"
    walls = arch_wall("riad_walls", clay)
    tower_walls = arch_wall("riad_tower_walls", clay, bay=2.4, half=0.3, sill=1.2, ground_sill=1.2, spring=2.2)
    tower_top = arch_wall("riad_tower_top", clay, bay=2.4, half=0.22, sill=0.95, ground_sill=0.95, spring=1.55)
    plaster = m.textured("riad_plaster", "plastered_wall_04", 3.0, clay, "OVERLAY", 0.55)
    terrace = m.textured("riad_terrace", "plastered_wall_04", 3.0, "#d39a72", "OVERLAY", 0.5)
    green_tiles = m.textured("riad_green_tiles", "clay_roof_tiles_02", 1.2, "#2f7a4f", "OVERLAY", 0.6)
    cream = m.textured("riad_cream", "sandstone_blocks_05", 2.0, "#ecd9b8", "OVERLAY", 0.4)
    door = m.textured("riad_door", "weathered_plank_siding", 1.5, "#2d6f80", "OVERLAY", 0.5)
    wood = m.textured("riad_wood", "weathered_plank_siding", 2.0, "#5a3a26")
    brass = m.plain("riad_brass", "#c8a24a")
    floor = zellige("riad_floor", "#ead9bd", "#b4643f", 0.7)
    tiles = zellige("riad_zellige", "#2c6e8f", "#e8e2d0", 0.25)
    water = m.plain("riad_water", "#3f8597")
    bark = m.plain("riad_bark", "#6a4e36")
    palm_leaf = m.plain("riad_palm_leaf", "#4d7330")
    orange = fruit_foliage("riad_orange_tree", "#2f5a26", "#f08a1c")
    path = m.textured("riad_path", "cobblestone_square", 1.5, "#d9b48a", "OVERLAY", 0.5)
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("riad_tint_cloth", owner_hex)
    canvas = m.striped("riad_tint_canvas", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 5)]

    # 1 the riad: three storeys round a roof terrace, a kasbah tower and a carved gate
    s = stages[0]
    top = 3 * STOREY
    p.box("main_block", (-5, 1, 0), (5, 8, top), walls, s)
    flat_roof("main", -5, 1, 5, 8, top, plaster, terrace, s, h=0.5)
    p.box("main_gate_surround", (-1.7, 0.7, 0), (1.7, 1.0, 4.6), cream, s)
    horseshoe("main_gate_arch", 0, 0.68, 0.0, 1.15, 2.6, plaster, s)
    horseshoe("main_gate_door", 0, 0.66, 0.0, 0.95, 2.6, door, s)
    p.hip_roof("main_gate_roof", -1.9, 0.5, 1.9, 1.0, 4.6, 0.45, green_tiles, s, overhang=0.25)
    p.box("tint_main_sign", (-1.2, 0.62, 3.75), (1.2, 0.7, 4.3), cloth, s)
    p.box("main_path", (-1.3, -8, 0), (1.3, 0.7, 0.03), path, s)
    # the roof terrace's shade sail on four posts
    for i, (x, y) in enumerate(((0.2, 3.4), (4.2, 3.4), (0.2, 6.6), (4.2, 6.6))):
        p.post(f"main_shade_post_{i}", x, y, top + 0.1, top + 2.3, 0.06, wood, s)
    p.box("tint_main_shade", (0.0, 3.2, top + 2.3), (4.4, 6.8, top + 2.4), canvas, s)
    # kasbah tower at the front-left corner: arched windows, a zellige band, crenellated top
    # round a green-tiled pyramid, brass finial, flag
    tx0, ty0, tx1, ty1, tz = -4.9, 0.1, -2.5, 2.5, 11.8
    # 1 cm proud of the block's walls and 1 cm up into the tower top, off the block's roof plane
    p.box("tower_shaft", (tx0 - 0.01, ty0 - 0.01, 0), (tx1 + 0.01, ty1 + 0.01, 3 * STOREY + 0.01), tower_walls, s)
    p.box("tower_top", (tx0, ty0, 3 * STOREY), (tx1, ty1, tz), tower_top, s)
    p.box("tower_band", (tx0 - 0.06, ty0 - 0.06, 3 * STOREY + 0.15), (tx1 + 0.06, ty1 + 0.06, 3 * STOREY + 0.65), tiles, s)
    flat_roof("tower", tx0, ty0, tx1, ty1, tz, plaster, terrace, s, merlon=0.8, t=0.25, h=0.45)
    cx, cy = (tx0 + tx1) / 2, (ty0 + ty1) / 2
    p.hip_roof("tower_roof", tx0 + 0.45, ty0 + 0.45, tx1 - 0.45, ty1 - 0.45, tz + 0.1, 1.0, green_tiles, s, overhang=0.1)
    p.post("tower_finial", cx, cy, tz + 1.0, tz + 1.55, 0.035, brass, s)
    p.blob("tower_finial_ball", (cx, cy, tz + 1.3), 0.12, brass, s)
    p.post("tower_flagpole", tx1 - 0.15, ty0 + 0.15, tz + 0.45, tz + 1.7, 0.035, wood, s)
    p.box("tint_tower_flag", (tx1 - 0.11, ty0 + 0.13, tz + 0.95), (tx1 + 1.0, ty0 + 0.17, tz + 1.65), cloth, s)

    # 2 west wing, 3 east wing: two storeys reaching forward, a green-tiled porch on their fronts
    for stage, (x0, x1), side in ((stages[1], (-9, -5), "west"), (stages[2], (5, 9), "east")):
        top = 2 * STOREY
        p.box(f"{side}_block", (x0, -3, 0), (x1, 7, top), walls, stage)
        flat_roof(side, x0, -3, x1, 7, top, plaster, terrace, stage)
        p.box(f"{side}_eave_band", (x0 - 0.05, -3.05, top - 0.35), (x1 + 0.05, 7.05, top - 0.1), tiles, stage)
        p.awning(f"{side}_porch_roof", x0 + 0.3, x1 - 0.3, -3, 3.0, green_tiles, stage, depth=1.2, drop=0.45)
        for i, x in enumerate((x0 + 0.5, x1 - 0.5)):
            p.post(f"{side}_porch_post_{i}", x, -4.05, 0, 2.6, 0.08, wood, stage)

    # 4 the forecourt: zellige floor, a fountain, orange trees in tiled planters, palms, parasols
    s = stages[3]
    p.box("court_floor", (-5, -3.2, 0), (5, 0.98, 0.06), floor, s)  # stops short of the gate's back face
    p.box("court_floor_front", (-9.4, -7.4, 0), (9.4, -3.2, 0.06), floor, s)

    cylinder("court_fountain_basin", 0, -3.6, 0, 0.55, 1.7, tiles, s)
    cylinder("court_fountain_water", 0, -3.6, 0.55, 0.58, 1.45, water, s)
    cylinder("court_fountain_stem", 0, -3.6, 0.58, 1.3, 0.2, tiles, s, segments=6)
    cylinder("court_fountain_bowl", 0, -3.6, 1.3, 1.6, 0.25, cream, s, r1=0.75)
    cylinder("court_fountain_bowl_water", 0, -3.6, 1.6, 1.62, 0.65, water, s)
    p.blob("court_fountain_spout", (0, -3.6, 1.75), 0.16, cream, s)
    for i, (x, y) in enumerate(((-3.4, -1.2), (3.4, -1.2), (-3.4, -6.0), (3.4, -6.0))):
        p.box(f"court_planter_{i}", (x - 0.6, y - 0.6, 0), (x + 0.6, y + 0.6, 0.6), tiles, s)
        p.post(f"court_orange_trunk_{i}", x, y, 0.6, 1.7, 0.09, bark, s)
        p.blob(f"court_orange_crown_{i}", (x, y, 2.3), 0.85, orange, s, subdiv=2)
    for i, x in enumerate((-7.8, 7.8)):
        palm(f"court_palm_{i}", x, -5.4, 6.3, (0.5 if x < 0 else -0.5, 0.2), bark, palm_leaf, s, length=2.3)
        cx = 5.9 if x > 0 else -5.9
        stick, _ = p.parasol(f"tint_court_parasol_{i}", cx, -6.0, canvas, wood, s, radius=1.2)
        stick.name = stick.name.removeprefix("tint_")
        cylinder(f"court_table_{i}", cx, -6.0, 0.06, 0.75, 0.45, cream, s)

    return stages
