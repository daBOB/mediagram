"""Seeblick (N2, sea side): a white lake hotel with dark-wood balconies on
every floor and a slate hip roof with dormers; the leisure wing is the
Seeterrasse, a pool on a timber deck in front. Four stages, as in
`grand_allee/board.rs`.

Plot: 20 m along the road (x -10..10) and 16 m deep (y -8..8); the road
runs along y = -8, so the fronts face -y."""

import materials as m
import parts as p

STOREY = 3.0


def build(owner_hex):
    wall = m.facade("seeblick_facade", wall_tint="#fbf6ec", ground_tint="#efe4d2")
    slate = m.textured("seeblick_slate", "roof_slates_02", 3.0, "#7d8796")
    stone = m.textured("seeblick_stone", "sandstone_blocks_05", 2.0, "#d8cdbd")
    wood = m.textured("seeblick_wood", "weathered_plank_siding", 2.0, "#6b4a35")
    deck = m.textured("seeblick_deck", "wood_floor_deck", 2.5)
    water = m.plain("seeblick_water", "#4f93a8")
    cornice = m.plain("seeblick_cornice", "#f2ece0")
    canvas = m.plain("seeblick_canvas", "#f6f2ea")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.striped("seeblick_tint_cloth", owner_hex)
    sign = m.plain("seeblick_tint_sign", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 5)]

    # 1 Hauptgebäude: four storeys, balconies on the three upper floors, dormers in the roof
    s = stages[0]
    top = 4 * STOREY
    p.box("main_block", (-5, 0, 0), (5, 8, top), wall, s)
    p.cornice("main_cornice", -5, 0, 5, 8, top, cornice, s)
    p.hip_roof("main_roof", -5, 0, 5, 8, top, 3.2, slate, s)
    for i, x in enumerate((-2.6, 0, 2.6)):
        p.box(f"main_dormer_{i}", (x - 0.6, 0.9, top), (x + 0.6, 2.2, top + 1.5), wall, s)
        p.hip_roof(f"main_dormer_roof_{i}", x - 0.6, 0.9, x + 0.6, 2.2, top + 1.5, 0.6, slate, s, overhang=0.15)
    for i, x in enumerate((-3.2, 3.2)):
        p.chimney(f"main_chimney_{i}", x, 5.5, top, 4.2, stone, s)
    for floor in (1, 2, 3):
        p.balcony_row(f"main_balcony_{floor}", -4.6, 4.6, 0, floor * STOREY, 1.2, wood, s)
    p.awning("tint_main_awning", -4.2, -1.8, 0, 2.9, cloth, s)
    p.awning("tint_main_awning_e", 1.8, 4.2, 0, 2.9, cloth, s)
    p.canopy("tint_main_canopy", -1.5, 1.5, 0, 3.0, 2.4, cloth, wood, s)
    p.box("tint_main_sign", (-3.0, -0.6, top + 0.2), (3.0, -0.45, top + 1.2), sign, s)
    p.box("main_flagpole", (4.6, -1.6, 0), (4.72, -1.48, 8.5), wood, s)
    p.box("tint_main_flag", (4.72, -1.56, 7.0), (6.2, -1.52, 8.3), sign, s)

    # 2 and 3: a three-storey wing either side, set back a metre
    for stage, (x0, x1), side in ((stages[1], (-9.5, -5), "west"), (stages[2], (5, 9.5), "east")):
        top = 3 * STOREY
        p.box(f"{side}_block", (x0, 1, 0), (x1, 8, top), wall, stage)
        p.cornice(f"{side}_cornice", x0, 1, x1, 8, top, cornice, stage)
        p.hip_roof(f"{side}_roof", x0, 1, x1, 8, top, 2.6, slate, stage)
        p.chimney(f"{side}_chimney", (x0 + x1) / 2, 6.0, top, 3.2, stone, stage)
        for floor in (1, 2):
            p.balcony_row(f"{side}_balcony_{floor}", x0 + 0.4, x1 - 0.4, 1, floor * STOREY, 1.1, wood, stage, posts=3)
        p.awning(f"tint_{side}_awning", x0 + 0.5, x1 - 0.5, 1, 2.9, cloth, stage)

    # 4 Seeterrasse: pool on a timber deck between the hotel and the road
    s = stages[3]
    p.pool("terrace_pool", -3.6, -6.2, 3.6, -3.4, deck, water, s)
    for i, x in enumerate((-7.2, 7.2)):
        p.parasol(f"tint_terrace_parasol_{i}", x, -4.6, cloth, wood, s)
        p.box(f"terrace_lounger_{i}", (x - 0.4, -6.6, 0), (x + 0.4, -4.8, 0.45), canvas, s)

    return stages
