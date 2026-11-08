"""Kirschblüte (N2, sea side, Japan): a two-storey ryokan of dark timber and
white plaster under layered tile roofs, lit paper screens along the ground
floor; a guest wing with a veranda, a bath house, and a stone garden with a
koi pond and cherry trees as its leisure wing. Four stages.

Plot: 20 m along the road (x -10..10), 16 m deep (y -8..8); the road runs
along y = -8, so the fronts face -y."""

import materials as m
import parts as p

STOREY = 3.2
PLOT = (20, 16)


def build(owner_hex):
    walls = m.timber_frame("kirsch_walls")
    tiles = m.textured("kirsch_tiles", "roof_slates_02", 2.5, "#5d636c")
    wood = m.textured("kirsch_wood", "weathered_plank_siding", 2.0, "#5a3d2b")
    deck = m.textured("kirsch_deck", "wood_floor_deck", 2.0, "#b08a62")
    stone = m.textured("kirsch_stone", "stone_wall", 2.0, "#b3ada3")
    gravel = m.textured("kirsch_gravel", "gravel_floor", 1.5, "#d8d5ce", "OVERLAY", 0.6)
    water = m.plain("kirsch_water", "#3f7f8f")
    bark = m.plain("kirsch_bark", "#4b3426")
    blossom = m.plain("kirsch_blossom", "#f2b3c6")
    blossom_light = m.plain("kirsch_blossom_light", "#f8d0dc")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("kirsch_tint_cloth", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 5)]

    # 1 the ryokan: a stone plinth, two timber storeys, a pent roof between them and a deep hip roof
    s = stages[0]
    p.box("main_plinth", (-6.2, 0.8, 0), (6.2, 8.2, 0.45), stone, s)
    p.box("main_ground", (-6, 1, 0.45), (6, 8, STOREY), walls, s)
    p.hip_roof("main_pent_roof", -6, 1, 6, 8, STOREY, 1.0, tiles, s, overhang=1.3)
    p.box("main_upper", (-5.2, 1.8, STOREY), (5.2, 7.2, 2 * STOREY - 0.3), walls, s)
    p.hip_roof("main_roof", -5.2, 1.8, 5.2, 7.2, 2 * STOREY - 0.3, 2.8, tiles, s, overhang=1.4)
    p.box("main_ridge", (-2.6, 4.4, 2 * STOREY + 2.4), (2.6, 4.6, 2 * STOREY + 2.75), wood, s)
    p.box("tint_main_noren", (-1.2, 0.85, 1.5), (1.2, 0.95, 2.7), cloth, s)
    p.box("tint_main_sign", (-1.6, 0.8, 2.8), (1.6, 0.92, 3.15), cloth, s)
    for i, x in enumerate((-8.4, 8.4)):
        p.post(f"main_banner_pole_{i}", x, -6.6, 0, 4.6, 0.05, wood, s)
        p.box(f"tint_main_banner_{i}", (x + 0.06, -6.64, 1.6), (x + 0.7, -6.56, 4.4), cloth, s)
    for i, (x, y) in enumerate(((-1.0, -0.4), (0.0, -1.9), (0.9, -3.4), (0.2, -4.9))):
        p.box(f"main_step_{i}", (x - 0.45, y - 0.35, 0), (x + 0.45, y + 0.35, 0.12), stone, s)

    # 2 the guest wing with a veranda, 3 the bath house: single storeys, set back
    for stage, (x0, x1), side in ((stages[1], (-9.6, -6.4), "west"), (stages[2], (6.4, 9.6), "east")):
        p.box(f"{side}_plinth", (x0 - 0.2, 1.8, 0), (x1 + 0.2, 8.2, 0.45), stone, stage)
        p.box(f"{side}_hall", (x0, 2, 0.45), (x1, 8, STOREY), walls, stage)
        p.hip_roof(f"{side}_roof", x0, 2, x1, 8, STOREY, 1.7, tiles, stage, overhang=1.0)
        p.box(f"{side}_veranda", (x0, 1.0, 0.3), (x1, 2.0, 0.45), deck, stage)

    # 4 the stone garden: raked gravel, a koi pond, rocks, a lantern and three cherry trees
    s = stages[3]
    p.box("garden_gravel", (-9, -7.4, 0), (-2.2, -1.2, 0.05), gravel, s)
    p.pool("garden_pond", 2.6, -5.8, 6.4, -2.6, stone, water, s, rim=0.5, top=0.25)
    for i, (x, y, r) in enumerate(((-7.2, -5.6, 0.7), (-5.0, -3.0, 0.5), (-3.4, -5.9, 0.4))):
        p.blob(f"garden_rock_{i}", (x, y, r * 0.35), r, stone, s, squash=(1.3, 1.0, 0.6))
    p.box("garden_lantern_base", (1.4, -6.6, 0), (1.8, -6.2, 0.9), stone, s)
    p.box("garden_lantern_light", (1.25, -6.75, 0.9), (1.95, -6.05, 1.35), stone, s)
    p.hip_roof("garden_lantern_roof", 1.25, -6.75, 1.95, -6.05, 1.35, 0.35, stone, s, overhang=0.2)
    for i, (x, y) in enumerate(((-8.2, -1.6), (8.0, -1.2), (7.6, -6.4))):
        p.post(f"garden_trunk_{i}", x, y, 0, 2.4, 0.16, bark, s)
        for j, (dx, dy, dz, r) in enumerate(((0, 0, 3.0, 1.3), (0.9, 0.3, 2.6, 0.9), (-0.8, -0.2, 2.7, 0.9))):
            p.blob(f"garden_blossom_{i}_{j}", (x + dx, y + dy, dz), r, blossom if j == 0 else blossom_light, s, squash=(1.0, 1.0, 0.75), subdiv=2)

    return stages
