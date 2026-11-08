"""Fjordhus (N3, sea side, Scandinavia): a lodge of Falu-red boards with
white corner boards, bargeboards and cross-barred windows under steep dark
roofs, behind a white picket fence with pines and a flagpole; two guest
houses, a pair of turf-roofed cabins, and a tarred sauna house on a jetty by
a pond, with a hot tub and birches, as its leisure wing. Five stages.

Plot: 24 m along the road (x -12..12), 16 m deep (y -8..8); the road runs
along y = -8, so the fronts face -y."""

import math
import materials as m
import parts as p
from fjordhus_kit import (  # noqa: F401  (PLOT is read by build.py)
    PLOT,
    SILL,
    STOREY,
    birch,
    gable_window,
    gabled,
    picket_fence,
    pine,
    pond_outline,
    red_boards,
)


def build(owner_hex):
    slate = m.textured("fjord_slate", "roof_slates_02", 2.5, "#5d6168")
    trim = m.plain("fjord_trim", "#f1eee6")
    stone = m.textured("fjord_stone", "stone_wall", 2.0, "#a19d95")
    turf = m.textured("fjord_turf", "sparse_grass", 2.0, "#62813c", "OVERLAY", 0.5)
    tar = m.textured("fjord_tar", "weathered_plank_siding", 2.0, "#7a6656")
    deck = m.textured("fjord_deck", "wood_floor_deck", 2.0, "#a58d74")
    gravel = m.textured("fjord_gravel", "gravel_floor", 1.5, "#cfc9bd", "OVERLAY", 0.5)
    water = m.plain("fjord_water", "#3d6f82")
    door = m.plain("fjord_door", "#2e4a4c")
    glass = m.Graph("fjord_glass", "#2f2a26")
    glass.bsdf.inputs["Emission Color"].default_value = (*m.hex_rgb("#ffb35c"), 1)
    glass.bsdf.inputs["Emission Strength"].default_value = 0.08
    glass = glass.mat
    needles = m.plain("fjord_needles", "#2c4a30")
    birch_leaves = m.plain("fjord_birch_leaves", "#86a34c")
    bark = m.plain("fjord_bark", "#4a3a2c")
    birch_bark = m.plain("fjord_birch_bark", "#e9e5dc")
    brass = m.plain("fjord_brass", "#c9a24a")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("fjord_tint_cloth", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 6)]

    # 1 the lodge: two storeys of red boards on a stone plinth under a steep slate roof, a
    # cross gable over the door with the name board, a picket fence, a flagpole, two spruces
    s = stages[0]
    eaves = SILL + 2 * STOREY
    p.box("main_plinth", (-5.08, 1.32, 0), (5.08, 7.48, SILL), stone, s)
    gabled("main", -5, 1.4, 5, 7.4, eaves, 4.0, red_boards("fjord_main_walls", eaves), slate, trim, s)
    p.box("main_gable_plinth", (-1.88, 0.72, 0), (1.88, 1.4, SILL), stone, s)
    gabled("main_cross", -1.8, 0.8, 1.8, 4.4, eaves, 2.6, red_boards("fjord_cross_walls", eaves), slate, trim, s, ridge="y")
    p.box("main_chimney", (2.3, 4.1, eaves), (3.0, 4.8, eaves + 5.0), trim, s)
    p.box("main_door", (-0.55, 0.74, SILL), (0.55, 0.82, SILL + 2.1), door, s)
    p.mesh_object("main_steps", lambda bm: (p.add_box(bm, -0.9, -0.3, 0, 0.9, 0.8, 0.25), p.add_box(bm, -0.9, 0.25, 0.25, 0.9, 0.8, SILL)), stone, s)
    p.canopy("tint_main_canopy", -1.2, 1.2, 0.8, 3.1, 1.3, cloth, trim, s)
    p.box("tint_main_sign", (-0.9, 0.72, eaves + 0.35), (0.9, 0.78, eaves + 0.95), cloth, s)
    p.box("main_path", (-0.9, -7.6, 0), (0.9, -0.3, 0.04), gravel, s)
    picket_fence("main_fence_w", -11.6, -1.2, -7.6, trim, s)
    picket_fence("main_fence_e", 1.2, 11.6, -7.6, trim, s)
    p.post("main_flagpole", 2.2, -6.9, 0, 9.0, 0.07, trim, s, segments=6)
    p.blob("main_flagpole_knob", (2.2, -6.9, 9.08), 0.12, brass, s)
    p.box("tint_main_flag", (2.27, -6.93, 7.4), (4.0, -6.87, 8.5), cloth, s)
    pine("main_spruce_w", -10.5, -1.6, 6.2, needles, bark, s)
    pine("main_spruce_e", 5.6, -0.6, 6.8, needles, bark, s)

    # 2 the west guest house and 3 the east guest house: gables to the road, white trim;
    # the east one is two storeys with a white balcony
    for stage, (x0, x1, y0, y1), floors, rise, side in ((stages[1], (-11.4, -6.6, 0.6, 7.0), 1, 3.6, "west"),
                                                         (stages[2], (6.6, 11.4, 2.0, 7.4), 2, 3.0, "east")):
        eaves = SILL + floors * STOREY
        p.box(f"{side}_plinth", (x0 - 0.08, y0 - 0.08, 0), (x1 + 0.08, y1 + 0.08, SILL), stone, stage)
        gabled(side, x0, y0, x1, y1, eaves, rise, red_boards(f"fjord_{side}_walls", eaves), slate, trim, stage, ridge="y")
        gable_window(f"{side}_gable_window", (x0 + x1) / 2, y0, eaves + 0.5, glass, trim, stage)
        p.box(f"{side}_door", ((x0 + x1) / 2 - 0.5, y0 - 0.08, SILL), ((x0 + x1) / 2 + 0.5, y0, SILL + 2.1), door, stage)
        p.box(f"{side}_step", ((x0 + x1) / 2 - 0.8, y0 - 0.7, 0), ((x0 + x1) / 2 + 0.8, y0, SILL), stone, stage)
        if floors == 2:
            p.balcony_row(f"{side}_balcony", x0 + 0.5, x1 - 0.5, y0, SILL + STOREY, 1.0, trim, stage)

    # 4 two cabins (hytter) with turf roofs by the road
    s = stages[3]
    for i, (x0, x1, y0, y1) in enumerate(((-11.2, -8.6, -6.4, -3.2), (-7.6, -5.0, -6.8, -3.6))):
        p.box(f"cabin_{i}_plinth", (x0 - 0.06, y0 - 0.06, 0), (x1 + 0.06, y1 + 0.06, 0.3), stone, s)
        gabled(f"cabin_{i}", x0, y0, x1, y1, 2.6, 1.0, red_boards(f"fjord_cabin_{i}_walls", 2.6, sill=0.3, storey=2.4, bay=1.3),
               turf, trim, s, ridge="y", overhang=0.35, thick=0.3, sill=0.3)
        p.box(f"cabin_{i}_door", ((x0 + x1) / 2 - 0.4, y0 - 0.08, 0.3), ((x0 + x1) / 2 + 0.4, y0, 2.2), door, s)

    # 5 the sauna by the water: a pond with shore rocks, a tarred sauna house with a turf roof
    # and a stovepipe, a jetty with a pennant, a deck with a wooden hot tub, two birches
    s = stages[4]
    pond = pond_outline(5.8, -4.6, 4.0, 2.5)
    p.mesh_object("sauna_pond", lambda bm: p.prism(bm, pond, 0, 0.05, lambda u, v, w: (u, v, w)), water, s)
    for i in (4, 6, 9, 11, 13):
        x, y = pond[i]
        p.blob(f"sauna_rock_{i}", (x, y, 0.15), 0.45, stone, s, squash=(1.3, 1.0, 0.6))
    p.box("sauna_base", (8.7, -4.5, 0), (11.5, -1.7, 0.4), stone, s)
    gabled("sauna", 8.8, -4.4, 11.4, -1.8, 2.6, 1.0, tar, turf, None, s, overhang=0.35, thick=0.3, sill=0.4)
    p.box("sauna_door", (8.72, -3.5, 0.4), (8.8, -2.7, 2.2), door, s)
    p.box("sauna_window", (9.6, -4.48, 1.4), (10.4, -4.4, 1.9), glass, s)
    p.post("sauna_stovepipe", 10.6, -2.4, 2.8, 4.4, 0.1, bark, s, segments=6)

    def jetty(bm):
        p.add_box(bm, 3.6, -4.3, 0.3, 8.8, -3.5, 0.42)
        p.add_box(bm, 8.8, -6.8, 0.3, 11.4, -4.5, 0.42)

    p.mesh_object("sauna_jetty", jetty, deck, s)

    def jetty_posts(bm):
        for x, y in ((3.8, -4.2), (3.8, -3.6), (6.2, -4.2), (6.2, -3.6), (9.0, -6.6), (11.2, -6.6)):
            p.add_box(bm, x - 0.08, y - 0.08, 0, x + 0.08, y + 0.08, 0.3)

    p.mesh_object("sauna_jetty_posts", jetty_posts, tar, s)
    tub = [(10.2 + 0.75 * math.cos(a), -5.6 + 0.75 * math.sin(a)) for a in (2 * math.pi * k / 12 for k in range(12))]
    p.mesh_object("sauna_tub", lambda bm: p.prism(bm, tub, 0.42, 1.25, lambda u, v, w: (u, v, w)), tar, s)
    p.mesh_object("sauna_tub_water", lambda bm: p.prism(bm, [(10.2 + (x - 10.2) * 0.85, -5.6 + (y + 5.6) * 0.85) for x, y in tub],
                                                       1.25, 1.27, lambda u, v, w: (u, v, w)), water, s)
    p.post("sauna_pennant_pole", 3.8, -4.3, 0.42, 5.0, 0.05, trim, s, segments=6)
    p.box("tint_sauna_pennant", (3.85, -4.33, 4.0), (5.6, -4.27, 4.6), cloth, s)
    birch("sauna_birch_0", 10.6, -0.2, 5.5, birch_leaves, birch_bark, s)
    birch("sauna_birch_1", 1.3, -2.6, 5.0, birch_leaves, birch_bark, s)

    return stages
