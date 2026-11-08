"""Skyline (P2, park side, New York): an art-deco hotel tower of pale
limestone, its piers running unbroken up the facade between dark spandrels and
lit windows, on a granite-and-gilt plinth with a marquee and a blade sign. It
grows a storey band per stage, each set back from the one below, up to a
gilt, ribbed crown with a steel spire; the leisure wing is the rooftop bar on
the setback terraces of the podium. Six stages.

Plot: 32 m along the road (x -16..16), 14 m deep (y -7..7); the road runs
along y = -7, so the fronts face -y."""

import materials as m
import parts as p
from skyline_kit import (  # noqa: F401  (PLOT is read by build.py)
    CROWN_SCALE,
    PLOT,
    SHAFT_STOREYS,
    STOREY,
    band,
    deco_facade,
    doors,
    glowing,
    lift,
    parapet,
    ribbed,
    sunburst,
)


def build(owner_hex):
    wall = deco_facade("sky_facade")
    stone = m.textured("sky_stone", "plastered_wall_04", 3.0, "#efe5d2", "OVERLAY", 0.5)
    gilt = ribbed("sky_gilt")
    steel = m.plain("sky_steel", "#c9cdd2")
    bronze = m.plain("sky_bronze", "#6b5434")
    entrance = doors("sky_doors")
    bulbs = glowing("sky_bulbs", "#ffd59a", 6.0)
    deck = m.textured("sky_deck", "wood_floor_deck", 2.0, "#c69c72")
    wood = m.plain("sky_bar_wood", "#4a3022")
    cushion = m.plain("sky_cushion", "#ece6da")
    hedge = m.plain("sky_hedge", "#4c6e3a")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("sky_tint_cloth", owner_hex)
    canvas = m.striped("sky_tint_canvas", owner_hex, width=0.3)

    stages = [p.empty(f"stage_{i}") for i in range(1, 7)]
    podium = 3 * STOREY

    # 1 the podium: three storeys on a granite plinth, a gilt portal under a marquee, a blade sign
    s = stages[0]
    p.box("podium", (-10, -1, 0), (10, 6.5, podium), wall, s)
    parapet("podium_parapet", -10.1, -1.1, 10.1, 6.6, podium, stone, s)
    p.box("podium_doors", (-1.8, -1.12, 0), (1.8, -1.0, 2.7), entrance, s)
    p.box("podium_portal", (-2.2, -1.2, 2.7), (2.2, -1.0, 3.1), bronze, s)
    p.box("podium_sunburst", (-1.7, -1.12, 3.9), (1.7, -1.0, 6.2), sunburst("sky_sunburst", 0, 3.75), s)
    p.box("tint_podium_marquee", (-2.8, -3.6, 3.2), (2.8, -1.0, 3.7), cloth, s)
    p.box("podium_marquee_rods", (-2.6, -3.3, 3.7), (2.6, -3.2, 3.8), bronze, s)
    p.box("tint_podium_blade", (8.6, -2.7, 3.6), (8.85, -1.0, 9.0), cloth, s)

    # 2-4 the shaft: a band per stage, each stepped back from the one below
    z0, shaft = podium, []
    for (x0, y0, x1, y1), storeys in zip(((-6.0, 0.0, 6.0, 5.5), (-4.5, 0.75, 4.5, 4.75), (-3.0, 1.5, 3.0, 4.0)), SHAFT_STOREYS):
        shaft.append((x0, y0, x1, y1, z0, z0 + storeys * STOREY))
        z0 += storeys * STOREY
    for i, (x0, y0, x1, y1, z0, z1) in enumerate(shaft):
        band(f"band_{i + 1}", x0, y0, x1, y1, z0, z1, wall, stone, stages[1 + i])

    # 5 the crown: three gilt, ribbed tiers stepping in, and a steel spire
    s = stages[4]
    z = shaft[-1][5]
    for i, (hw, hd, h) in enumerate(((2.2, 1.05, 2.0), (1.5, 0.75, 1.6), (0.9, 0.45, 1.2))):
        p.box(f"crown_tier_{i}", (-hw, 2.75 - hd, z), (hw, 2.75 + hd, z + h * CROWN_SCALE), gilt, s)
        z += h * CROWN_SCALE
    p.cone_roof("crown_spire", 0, 2.75, z, 0.55, 3.4 * CROWN_SCALE, steel, s, segments=8)

    # 6 the rooftop bar on the podium's terraces: decks, a bar under an awning,
    # parasols, lounges, planters and strings of lights
    s = stages[5]
    top = podium + 0.15
    for i, sx in enumerate((-1, 1)):
        side = ("west", "east")[i]
        x0, x1 = sorted((sx * 6.0, sx * 9.85))
        p.box(f"bar_{side}_deck", (x0, -0.85, podium), (x1, 6.35, top), deck, s)
        bx = sx * 6.45
        px0, px1 = sorted((sx * 6.0, sx * 8.0))
        p.box(f"bar_{side}_pavilion", (px0, 4.3, top), (px1, 6.3, top + 2.7), entrance, s)
        p.box(f"bar_{side}_pavilion_roof", (px0 - 0.1, 4.1, top + 2.7), (px1 + 0.1, 6.35, top + 2.85), stone, s)
        p.box(f"bar_{side}_counter", (bx - 0.4, 1.0, top), (bx + 0.4, 3.8, top + 1.1), wood, s)
        p.box(f"bar_{side}_counter_top", (bx - 0.5, 0.9, top + 1.1), (bx + 0.5, 3.9, top + 1.18), bronze, s)
        # parts.parasol names the pole after the canopy, so the canopy takes its tint_ name afterwards
        lift(p.parasol(f"bar_{side}_parasol", sx * 8.4, 0.6, canvas, steel, s, height=2.5, radius=1.3), top)[1].name = f"tint_bar_{side}_parasol"
        p.box(f"bar_{side}_lounge", (sx * 8.9 - 0.4, 2.0, top), (sx * 8.9 + 0.4, 3.9, top + 0.45), cushion, s)
        p.box(f"bar_{side}_planter", (sx * 9.2 - 0.35, 5.5, top), (sx * 9.2 + 0.35, 6.2, top + 0.5), stone, s)
        p.blob(f"bar_{side}_shrub", (sx * 9.2, 5.85, top + 0.95), 0.6, hedge, s, squash=(1.0, 1.0, 0.9))

        def lights(bm, x=sx * 9.6):
            for py in (-0.6, 6.1):
                p.add_box(bm, x - 0.04, py - 0.04, top, x + 0.04, py + 0.04, top + 2.6)
            p.add_box(bm, x - 0.015, -0.6, top + 2.55, x + 0.015, 6.1, top + 2.58)

        p.mesh_object(f"bar_{side}_light_poles", lights, steel, s)

        def bulb_row(bm, x=sx * 9.6):
            for k in range(9):
                py = -0.4 + k * 0.78
                p.add_box(bm, x - 0.07, py - 0.07, top + 2.41, x + 0.07, py + 0.07, top + 2.55)

        p.mesh_object(f"bar_{side}_bulbs", bulb_row, bulbs, s)
    p.box("tint_bar_awning", (6.0, 0.8, top + 2.5), (7.4, 4.1, top + 2.65), cloth, s)
    p.box("bar_awning_post", (7.25, 0.9, top), (7.35, 1.0, top + 2.5), steel, s)

    return stages
