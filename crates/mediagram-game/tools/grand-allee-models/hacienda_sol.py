"""Hacienda Sol (S1, south side, Mexico): a two-storey casa of white render
under terracotta tiles with an arcaded portal along its front and a bell
gable over the entrance; a guest wing with its own arcade; a walled
courtyard garden with saltillo tiles, a talavera-tiled pool, a fountain,
saguaros and parasols as its leisure wing. Three stages.

Plot: 20 m along the road (x -10..10), 16 m deep (y -8..8); the road runs
along y = -8, so the fronts face -y."""

import math
import materials as m
import parts as p
from hacienda_sol_kit import (  # noqa: F401  (PLOT is read by build.py)
    MID,
    PLOT,
    STOREY,
    arcade,
    arch_outline,
    casa,
    lean_to,
    nopal,
    saguaro,
    saltillo,
    talavera,
    tube,
    wall_axis,
)


def build(owner_hex):
    walls = m.facade("hacienda_casa", wall_tint="#fbf3e4", ground="plastered_wall_04", ground_tint="#f6ead6",
                     glass="#2a2622", frame="#6b4429", shop="#2e2018", bay=2.6, storey=STOREY, base=STOREY)
    wing_walls = m.facade("hacienda_wing", wall_tint="#fbf3e4", ground="plastered_wall_04", ground_tint="#f6ead6",
                          glass="#2a2622", frame="#6b4429", shop="#2e2018", bay=2.2, storey=4.0, base=3.6)
    render = m.textured("hacienda_render", "plastered_wall_04", 3.0, "#fbf3e4", "OVERLAY", 0.55)
    tiles = m.textured("hacienda_tiles", "clay_roof_tiles_02", 2.5)
    floor = saltillo("hacienda_saltillo")
    blue = talavera("hacienda_talavera")
    water = m.plain("hacienda_water", "#4fa3b8")
    wood = m.textured("hacienda_wood", "weathered_plank_siding", 2.0, "#7a5236")
    bronze = m.plain("hacienda_bronze", "#8a6a34")
    cactus = m.plain("hacienda_cactus", "#55813d")
    canvas = m.plain("hacienda_canvas", "#f4efe4")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("hacienda_tint_cloth", owner_hex)
    stripes = m.striped("hacienda_tint_canvas", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 4)]

    # 1 the casa: two storeys of render, a terracotta gable roof, a five-arch portal in front
    # under a tiled lean-to, the bell gable with its bell and the name board, two banners
    s = stages[0]
    eaves = 2 * STOREY
    casa("main", -9.5, 3.0, 5.0, 7.4, eaves, 1.5, walls, tiles, s)
    arcade("main_arcade", -9.5, 5.0, 5, 1.4, 1.8, 2.1, 3.5, render, s)
    lean_to("main_arcade_roof", -9.7, 5.0, 1.1, 3.0, 3.4, 3.95, tiles, s)
    p.box("main_arcade_floor", (-9.5, 1.4, 0), (5.0, 3.0, 0.08), floor, s)
    gable = [(MID - 1.9, eaves - 0.6), (MID + 1.9, eaves - 0.6), (MID + 1.9, eaves + 0.5), (MID + 1.55, eaves + 0.8),
             (MID + 1.1, eaves + 0.95), (MID + 1.0, eaves + 1.2), (MID - 1.0, eaves + 1.2), (MID - 1.1, eaves + 0.95),
             (MID - 1.55, eaves + 0.8), (MID - 1.9, eaves + 0.5)]
    belfry = arch_outline(MID - 1.0, MID + 1.0, 1, eaves + 1.75, eaves + 2.5, base=eaves + 1.2, pier=0.35)
    crown = [(MID - 1.0, eaves + 2.5), (MID + 1.0, eaves + 2.5), (MID + 0.6, eaves + 2.95), (MID, eaves + 3.15), (MID - 0.6, eaves + 2.95)]
    p.mesh_object("main_bell_gable", lambda bm: [p.prism(bm, o, 2.85, 3.25, wall_axis("x")) for o in (gable, belfry, crown)], render, s)
    p.blob("main_bell", (MID, 3.05, eaves + 1.65), 0.3, bronze, s, squash=(1.0, 1.0, 1.25), subdiv=2)
    p.box("tint_main_sign", (MID - 1.3, 2.79, eaves + 0.2), (MID + 1.3, 2.85, eaves + 0.75), cloth, s)
    for i, x in enumerate((-5.6, 2.2)):
        p.box(f"tint_main_banner_{i}", (x - 0.35, 2.94, STOREY + 0.9), (x + 0.35, 3.0, eaves - 0.4), cloth, s)
    saguaro("main_saguaro", -9.0, 0.6, 2.6, cactus, s, -0.2)

    # 2 the guest wing: one storey along the east side, gable to the road, with its own
    # three-arch portal facing the courtyard
    s = stages[1]
    casa("east", 5.0, -5.6, 9.4, 7.4, 3.6, 1.2, wing_walls, tiles, s, ridge="y", overhang=0.3)
    arcade("east_arcade", -5.6, 1.4, 3, 3.4, 3.8, 2.1, 3.4, render, s, axis="y")
    lean_to("east_arcade_roof", -5.9, 1.1, 3.1, 5.0, 3.3, 3.55, tiles, s, axis="y")
    p.box("east_arcade_floor", (3.42, -5.6, 0), (5.0, 1.4, 0.08), floor, s)  # 2 cm in from the arcade's side
    p.box("east_door", (6.7, -5.68, 0), (7.7, -5.6, 2.4), wood, s)
    p.box("tint_east_sign", (6.2, -5.66, 2.6), (8.2, -5.6, 3.2), cloth, s)

    # 3 the courtyard: saltillo paving inside a low white wall with a gate on the casa's axis,
    # a talavera pool, a fountain, saguaros and prickly pears, parasols with loungers
    s = stages[2]
    p.box("court_paving", (-9.5, -7.4, 0), (3.4, 1.4, 0.04), floor, s)

    def front_wall(bm):
        for x0, x1 in ((-9.8, MID - 1.1), (MID + 1.1, 9.6)):
            p.add_box(bm, x0, -7.75, 0, x1, -7.4, 1.1)
        for x in (MID - 1.35, MID + 1.35):
            p.add_box(bm, x - 0.3, -7.85, 0, x + 0.3, -7.3, 1.9)

    p.mesh_object("court_wall", front_wall, render, s)
    p.mesh_object("court_wall_coping", lambda bm: [p.add_box(bm, x0, -7.82, 1.1, x1, -7.33, 1.2)
                                                   for x0, x1 in ((-9.85, MID - 1.65), (MID + 1.65, 9.65))], tiles, s)
    p.pool("court_pool", -8.4, -5.8, -4.4, -2.6, blue, water, s, rim=0.45)
    circle = [(math.cos(a), math.sin(a)) for a in (2 * math.pi * k / 14 for k in range(14))]
    fx, fy = 0.8, -3.6
    p.mesh_object("court_fountain", lambda bm: (p.prism(bm, [(fx + 1.0 * c, fy + 1.0 * d) for c, d in circle], 0, 0.55, lambda u, v, w: (u, v, w)),
                                                 tube(bm, (fx, fy, 0.5), (fx, fy, 1.35), 0.13)), blue, s)
    p.mesh_object("court_fountain_water", lambda bm: p.prism(bm, [(fx + 0.85 * c, fy + 0.85 * d) for c, d in circle], 0.55, 0.57,
                                                           lambda u, v, w: (u, v, w)), water, s)
    p.blob("court_fountain_bowl", (fx, fy, 1.4), 0.45, blue, s, squash=(1.0, 1.0, 0.35))
    for i, (x, y, h, turn) in enumerate(((-9.1, -6.9, 3.2, 0.3), (7.6, -6.8, 3.4, 0.1))):
        saguaro(f"court_saguaro_{i}", x, y, h, cactus, s, turn)
    for i, (x, y) in enumerate(((-0.4, -6.4), (-4.0, 0.6))):
        nopal(f"court_nopal_{i}", x, y, cactus, s)
    for i, (x, y) in enumerate(((-6.4, -0.6), (1.9, -6.2))):
        pole, _ = p.parasol(f"tint_court_parasol_{i}", x, y, stripes, wood, s)
        pole.name = pole.name.removeprefix("tint_")  # only the canopy takes the owner colour
    for i, x in enumerate((-8.2, -6.0)):
        p.box(f"court_lounger_{i}", (x, -7.2, 0), (x + 1.9, -6.5, 0.4), canvas, s)

    return stages
