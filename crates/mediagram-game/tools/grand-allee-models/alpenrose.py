"""Alpenrose (S3, south side, the Alps): a chalet hotel with a rubble-stone
ground floor, larch-timber storeys with green shutters and a wide, low
gable roof facing the road; long wooden balconies carry red geraniums.
Two guest chalets flank it, a log-built Stube with a sun terrace follows,
and a wellness garden (pool, wooden hot tub, loungers, firs) is its leisure
wing. Five stages.

Plot: 24 m along the road (x -12..12), 16 m deep (y -8..8); the road runs
along y = -8, so the fronts face -y. Wall corners sit on bay boundaries
(x + y a multiple of BAY) so no window wraps round a corner."""

import materials as m
import parts as p
from alpenrose_kit import (  # noqa: F401  (PLOT is read by build.py)
    PLOT,
    STOREY,
    balcony,
    blooms,
    chalet_roof,
    chalet_wall,
    cylinder,
    fir,
    rail_boards,
)


def build(owner_hex):
    main_walls = chalet_wall("alp_main_walls", attic_z=3 * STOREY, attic_half=1.1)
    wing_walls = chalet_wall("alp_wing_walls", attic_z=2 * STOREY, attic_half=0.0)
    stube_walls = chalet_wall("alp_stube_walls", attic_z=STOREY, attic_half=0.0, base=0.0, wood="#9c6638")
    slates = m.textured("alp_slates", "roof_slates_02", 3.0, "#a7b0bb")
    soffit = m.textured("alp_soffit", "weathered_plank_siding", 1.5, "#8a5a32", "OVERLAY", 0.7)
    wood = m.textured("alp_wood", "weathered_plank_siding", 2.0, "#8a5a32", "OVERLAY", 0.7)
    rail = rail_boards("alp_rail")
    planter = m.plain("alp_planter", "#4a3020")
    geraniums = blooms("alp_geraniums", "#3c6a2c", "#d81f3a", 6.0, 0.55)
    stone = m.textured("alp_stone", "stone_wall", 2.0, "#cfc7ba")
    paving = m.textured("alp_paving", "sandstone_blocks_05", 2.0, "#cdc5b8")
    path = m.textured("alp_path", "gravel_floor", 1.5, "#d6cfc2", "OVERLAY", 0.6)
    deck = m.textured("alp_deck", "wood_floor_deck", 2.0, "#b98a5a", "OVERLAY", 0.6)
    water = m.plain("alp_water", "#4c93a3")
    needles = m.plain("alp_needles", "#2d4a2e")
    bark = m.plain("alp_bark", "#4b3426")
    canvas = m.plain("alp_canvas", "#efe9de")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("alp_tint_cloth", owner_hex)
    stripes = m.striped("alp_tint_stripes", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 6)]

    # 1 the chalet: stone ground floor, two timber storeys, an attic under a wide low gable to the road
    s = stages[0]
    x0, y0, x1, y1, eaves = -5.5, -1.1, 5.5, 5.5, 3 * STOREY
    p.box("main_block", (x0, y0, 0), (x1, y1, eaves), main_walls, s)
    chalet_roof("main_roof", x0, y0, x1, y1, eaves, 2.6, slates, soffit, main_walls, s)
    p.chimney("main_chimney", 2.6, 3.2, eaves, 3.6, stone, s)
    for i, z in enumerate((STOREY, 2 * STOREY)):
        balcony(f"main_balcony_{i}", x0, x1, y0, z, 1.3, wood, rail, planter, geraniums, s)
    balcony("main_balcony_attic", -2.2, 2.2, y0, eaves, 1.0, wood, rail, planter, geraniums, s)
    p.box("tint_main_sign", (-1.9, y0 - 1.42, STOREY + 0.3), (1.9, y0 - 1.36, STOREY + 0.68), cloth, s)
    p.box("main_door", (-0.6, y0 - 0.08, 0), (0.6, y0, 2.3), soffit, s)
    p.box("main_path", (-0.9, -8, 0), (0.9, y0 - 0.08, 0.03), path, s)  # ends at the door, not inside it
    p.post("main_flagpole", 1.25, -7.55, 0, 7.0, 0.05, canvas, s)
    p.box("tint_main_flag", (1.3, -7.57, 5.2), (2.75, -7.53, 6.8), stripes, s)
    fir("main_fir_0", -11.2, -2.6, 7.0, 0.8, needles, bark, s)
    fir("main_fir_1", 11.35, -1.3, 6.0, 0.65, needles, bark, s)

    # 2 west and 3 east guest chalets, set back, one timber storey on stone, a balcony each
    for stage, (wx0, wx1), side in ((stages[1], (-11.0, -6.6), "west"), (stages[2], (6.6, 11.0), "east")):
        top = 2 * STOREY
        p.box(f"{side}_block", (wx0, 0, 0), (wx1, 6.6, top), wing_walls, stage)
        chalet_roof(f"{side}_roof", wx0, 0, wx1, 6.6, top, 1.2, slates, soffit, wing_walls, stage,
                    side=0.6, front=1.3, back=0.5, thick=0.25)
        balcony(f"{side}_balcony", wx0, wx1, 0, STOREY, 1.1, wood, rail, planter, geraniums, stage)

    # 4 the Stube: a log-built restaurant with a sun terrace and parasols
    s = stages[3]
    p.box("stube_plinth", (6.5, -6.7, 0), (11.1, -2.1, 0.3), stone, s)
    p.box("stube_block", (6.6, -6.6, 0.3), (11.0, -2.2, STOREY), stube_walls, s)
    chalet_roof("stube_roof", 6.6, -6.6, 11.0, -2.2, STOREY, 1.3, slates, soffit, stube_walls, s,
                side=0.6, front=0.9, back=0.5, thick=0.25)
    p.box("stube_terrace", (1.6, -7.4, 0), (6.4, -2.6, 0.25), deck, s)
    for i, (x, y) in enumerate(((2.9, -5.9), (5.0, -3.9))):
        stick, _ = p.parasol(f"tint_stube_parasol_{i}", x, y, stripes, wood, s, radius=1.15)
        stick.name = stick.name.removeprefix("tint_")
        cylinder(f"stube_table_{i}", x, y, 0.25, 0.95, 0.42, wood, s, segments=8)

    # 5 the wellness garden: a pool on stone paving, a wooden hot tub, loungers and firs
    s = stages[4]
    p.pool("spa_pool", -9.4, -6.6, -4.4, -3.6, paving, water, s, rim=0.9, top=0.25)
    cylinder("spa_tub", -2.4, -4.6, 0, 0.9, 1.0, soffit, s)
    cylinder("spa_tub_water", -2.4, -4.6, 0.9, 0.92, 0.9, water, s)
    p.box("spa_tub_step", (-3.0, -6.0, 0), (-1.8, -5.6, 0.4), wood, s)
    for i, x in enumerate((-9.2, -7.6, -6.0)):
        p.box(f"spa_lounger_{i}", (x - 0.35, -2.4, 0), (x + 0.35, -0.6, 0.35), wood, s)
        p.box(f"spa_lounger_cushion_{i}", (x - 0.32, -2.3, 0.35), (x + 0.32, -1.1, 0.45), canvas, s)
        p.box(f"spa_lounger_back_{i}", (x - 0.32, -1.1, 0.35), (x + 0.32, -0.7, 0.85), canvas, s)
    for i, (x, y, h) in enumerate(((-11.05, -7.05, 5.5), (-2.0, -7.1, 4.5))):
        fir(f"spa_fir_{i}", x, y, h, 0.85, needles, bark, s)

    return stages
