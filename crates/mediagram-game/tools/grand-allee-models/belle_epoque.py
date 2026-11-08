"""Belle Époque (P4, park side, Paris): a cream limestone palace under a slate
mansard with dormers and chimneys, wrought-iron balconies running the width of
the facade, a rusticated ground floor of lit round arches and a glass marquise
over the door; two wings, a round corner pavilion under a ribbed dome and
lantern, a pavement café with striped awnings, and a glass-and-iron palm house
as its leisure wing. Six stages.

Plot: 32 m along the road (x -16..16), 14 m deep (y -7..7); the road runs
along y = -7, so the fronts face -y."""

import math
import materials as m
import parts as p
from belle_epoque_kit import (  # noqa: F401  (PLOT is read by build.py)
    PLOT,
    ROTUNDA,
    STOREY,
    dormers,
    glasshouse,
    mansard,
    railing,
    revolve,
    slate_roof,
    stone_facade,
    vault_hall,
)


def build(owner_hex):
    wall = stone_facade("belle_facade")
    tower_wall = stone_facade("belle_rotunda_facade", ring=(ROTUNDA[0], ROTUNDA[1], 8))
    trim = m.textured("belle_trim", "sandstone_blocks_05", 3.0, "#e6d8bd", "OVERLAY", 0.45)
    slate = slate_roof("belle_slate")
    dome_slate = slate_roof("belle_dome", ring=(ROTUNDA[0], ROTUNDA[1], 12))
    zinc = m.plain("belle_zinc", "#a7b0b5")
    iron = m.plain("belle_iron", "#24292b")
    glass = m.plain("belle_glass", "#3b4650")
    hothouse = glasshouse("belle_glasshouse")
    gravel = m.textured("belle_gravel", "gravel_floor", 1.5, "#e2dccf", "OVERLAY", 0.6)
    tub = m.plain("belle_tub", "#ebe6dc")
    bark = m.plain("belle_bark", "#5e4634")
    leaf = m.plain("belle_leaf", "#4e7040")
    cloth_white = m.plain("belle_tablecloth", "#f4f1ea")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("belle_tint_cloth", owner_hex)
    canvas = m.striped("belle_tint_canvas", owner_hex, width=0.3)

    stages = [p.empty(f"stage_{i}") for i in range(1, 7)]

    # 1 the palace: four storeys, two iron balconies the width of the facade, a mansard with dormers
    s = stages[0]
    top = 4 * STOREY
    p.box("main_block", (-5, -1, 0), (5, 6.5, top), wall, s)
    p.cornice("main_string_course", -5, -1, 5, 6.5, STOREY, trim, s, out=0.12, height=0.18)
    p.cornice("main_cornice", -5, -1, 5, 6.5, top, trim, s, out=0.3, height=0.35)
    for z in (2 * STOREY, 3 * STOREY):
        p.balcony_row(f"main_balcony_{z:.0f}", -4.9, 4.9, -1, z, 0.6, iron, s, posts=20)
    mansard("main_mansard", -5, -1, 5, 6.5, top, slate, s)
    dormers("main_dormer", (-2, 0, 2), -1, top, trim, slate, glass, s)
    for i, x in enumerate((-3.2, 3.2)):
        p.chimney(f"main_chimney_{i}", x, 3.0, top + 2.9, 1.3, trim, s, side=0.6)
    p.box("tint_main_sign", (-2.2, 0.0, top + 2.65), (2.2, 0.1, top + 3.5), cloth, s)
    p.canopy("tint_main_marquise", -1.6, 1.6, -1, 3.1, 1.8, cloth, iron, s)
    p.box("main_steps", (-1.4, -1.6, 0), (1.4, -1.0, 0.15), trim, s)

    # 2 and 3: three-storey wings, set back, a balcony and a mansard each
    for stage, sx, side in ((stages[1], -1, "west"), (stages[2], 1, "east")):
        x0, x1 = sorted((sx * 4.95, sx * (11.0 if sx < 0 else 10.9)))
        top = 3 * STOREY
        p.box(f"{side}_block", (x0, 1.0, 0), (x1, 6.5, top), wall, stage)
        p.cornice(f"{side}_string_course", x0, 1.0, x1, 6.5, STOREY - 0.02, trim, stage, out=0.12, height=0.18)
        p.cornice(f"{side}_cornice", x0, 1.0, x1, 6.5, top, trim, stage, out=0.3, height=0.35)
        p.balcony_row(f"{side}_balcony", x0 + 0.1, x1 - 0.1, 1.0, 2 * STOREY, 0.6, iron, stage, posts=12)
        mansard(f"{side}_mansard", x0, 1.0, x1, 6.5, top, slate, stage, lower=2.4, inset=0.75, upper=0.5, top_inset=1.4)
        dormers(f"{side}_dormer", (sx * 6, sx * 8), 1.0, top, trim, slate, glass, stage)
        p.chimney(f"{side}_chimney", sx * 9.2, 3.8, top + 2.7, 1.2, trim, stage, side=0.6)

    # 4 the corner pavilion: a round tower with a balcony, under a ribbed dome and a lantern
    s = stages[3]
    cx, cy, r = ROTUNDA
    top = 4 * STOREY
    revolve("rotunda", cx, cy, 0, ((r, 0), (r, top)), tower_wall, s, segments=20, caps=False)
    revolve("rotunda_string_course", cx, cy, STOREY - 0.18, ((r + 0.12, 0), (r + 0.12, 0.18)), trim, s, segments=20, smooth=False)
    revolve("rotunda_cornice", cx, cy, top - 0.35, ((r + 0.3, 0), (r + 0.3, 0.35)), trim, s, segments=20, smooth=False)
    revolve("rotunda_balcony", cx, cy, 2 * STOREY, ((r + 0.6, 0), (r + 0.6, 0.15)), iron, s, segments=20, smooth=False)
    revolve("rotunda_balcony_rail", cx, cy, 2 * STOREY + 0.95, ((r + 0.57, 0), (r + 0.57, 0.08)), iron, s, segments=20, caps=False)

    def balusters(bm):
        for i in range(20):
            a = 2 * math.pi * (i + 0.5) / 20
            px, py = cx + (r + 0.57) * math.cos(a), cy + (r + 0.57) * math.sin(a)
            p.add_box(bm, px - 0.03, py - 0.03, 2 * STOREY + 0.15, px + 0.03, py + 0.03, 2 * STOREY + 0.98)

    p.mesh_object("rotunda_balcony_posts", balusters, iron, s)
    revolve("rotunda_dome", cx, cy, top, ((r + 0.15, 0), (r + 0.15, 0.45), (r, 1.15), (r - 0.35, 1.9), (r - 0.9, 2.55),
                                         (r - 1.55, 3.0), (0.5, 3.25), (0.03, 3.3)), dome_slate, s, segments=20, caps=False)
    revolve("rotunda_lantern", cx, cy, top + 3.3, ((0.45, 0), (0.45, 0.9)), zinc, s, segments=10, smooth=False)
    revolve("rotunda_lantern_cap", cx, cy, top + 4.2, ((0.6, 0), (0.45, 0.3), (0.22, 0.55), (0.03, 0.7)), zinc, s, segments=10, caps=False)
    p.post("rotunda_finial", cx, cy, top + 4.8, top + 6.3, 0.04, iron, s, segments=6)
    p.box("tint_rotunda_flag", (cx + 0.02, cy - 0.03, top + 5.4), (cx + 1.45, cy + 0.03, top + 6.2), cloth, s)

    # 5 the café: a glazed ground-floor annex with a roof terrace, striped awnings and tables on the pavement
    s = stages[4]
    p.box("cafe", (-9.4, -1.2, 0), (-4.95, 1.05, 3.4), wall, s)
    p.cornice("cafe_cornice", -9.4, -1.2, -5.0, 1.05, 3.55, trim, s, out=0.1, height=0.2)
    railing("cafe_railing", ((-5.05, -1.25), (-9.35, -1.25), (-9.35, 1.0)), 3.55, iron, s, height=0.9)
    for i, x in enumerate((-8.2, -6.2)):
        p.awning(f"tint_cafe_awning_{i}", x - 0.85, x + 0.85, -1.2, 3.0, canvas, s, depth=1.3, drop=0.55)
    for i, (x, y) in enumerate(((-8.9, -2.9), (-7.3, -2.9), (-5.7, -2.9), (-8.1, -4.2), (-6.5, -4.2))):
        p.post(f"cafe_table_leg_{i}", x, y, 0, 0.72, 0.035, iron, s, segments=6)
        revolve(f"cafe_table_{i}", x, y, 0.72, ((0.34, 0), (0.34, 0.05)), cloth_white, s, segments=8, smooth=False)

        def chairs(bm, x=x, y=y):
            for dx in (-0.55, 0.55):
                p.add_box(bm, x + dx - 0.18, y - 0.18, 0, x + dx + 0.18, y + 0.18, 0.45)
                p.add_box(bm, x + dx + (0.15 if dx > 0 else -0.18), y - 0.18, 0.45, x + dx + (0.18 if dx > 0 else -0.15), y + 0.18, 0.9)

        p.mesh_object(f"cafe_chairs_{i}", chairs, iron, s)

    # 6 the palm house: a glass-and-iron hall with a barrel vault and an entrance
    # vestibule, a gravel walk, and potted palms in white tubs at the door
    s = stages[5]
    vault_hall("garden_hall", 11.1, -5.4, 15.7, 5.6, 3.4, 2.3, hothouse, s)
    vault_hall("garden_vestibule", 12.5, -6.3, 14.3, -5.35, 2.5, 0.9, hothouse, s, n=6)
    p.box("garden_cresting", (13.37, -5.4, 5.7), (13.43, 5.6, 5.95), iron, s)
    p.post("garden_finial", 13.4, -5.3, 5.6, 7.4, 0.04, iron, s, segments=6)
    p.box("tint_garden_flag", (13.42, -5.33, 6.5), (14.75, -5.27, 7.3), cloth, s)
    p.box("garden_walk", (12.6, -7.0, 0), (14.2, -6.3, 0.04), gravel, s)
    for i, x in enumerate((11.6, 15.2)):
        p.box(f"garden_tub_{i}", (x - 0.35, -6.55, 0), (x + 0.35, -5.85, 0.7), tub, s)
        p.post(f"garden_palm_trunk_{i}", x, -6.2, 0.7, 2.8, 0.07, bark, s, segments=6)
        p.blob(f"garden_palm_crown_{i}", (x, -6.2, 3.0), 0.8, leaf, s, squash=(1.0, 1.0, 0.6), subdiv=2)

    return stages
