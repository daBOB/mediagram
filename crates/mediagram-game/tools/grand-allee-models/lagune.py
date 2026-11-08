"""Lagune (N1, sea side, South Seas): a thatched lodge on stilts at the edge
of a turquoise lagoon, palms on a white beach and a gate by the road;
overwater bungalows on a boardwalk; a lagoon pool with a round thatched pool
bar, parasols and more palms as its leisure wing. Three stages.

Plot: 20 m along the road (x -10..10), 16 m deep (y -8..8); the road runs
along y = -8, so the fronts face -y."""

import math
import materials as m
import parts as p
from lagune_kit import (  # noqa: F401  (PLOT is read by build.py)
    FLOOR,
    PLOT,
    SHORE,
    STOREY,
    bamboo,
    boxes,
    bungalow,
    flat,
    lagoon_water,
    lit,
    organic,
    palm,
    posts,
    ring,
    thatch,
    thatched_roof,
)


def build(owner_hex):
    sand = m.textured("lagune_sand", "gravel_floor", 3.0, "#f1e2bb", "OVERLAY", 0.35)
    sea = lagoon_water("lagune_lagoon")
    pool_water = m.plain("lagune_pool_water", "#62d4d2")
    rim_stone = m.textured("lagune_rim", "sandstone_blocks_05", 2.0, "#f0e8d8")
    straw = thatch("lagune_thatch")
    wood = m.textured("lagune_wood", "weathered_plank_siding", 2.0, "#8a6a4c")
    deck = m.textured("lagune_deck", "wood_floor_deck", 2.0, "#c49a6c")
    walls = bamboo("lagune_walls")
    glow = lit("lagune_lit", "#3b2616", "#ffa552")
    trunk = m.textured("lagune_trunk", "weathered_plank_siding", 1.0, "#b39b7c")
    frond = m.plain("lagune_frond", "#3f7a2e")
    canvas = m.plain("lagune_canvas", "#f4efe4")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("lagune_tint_cloth", owner_hex)
    stripes = m.striped("lagune_tint_canvas", owner_hex)

    stages = [p.empty(f"stage_{i}") for i in range(1, 4)]

    # 1 the lodge on stilts at the water's edge: deck, plank walls, a tall thatched hip roof,
    # stairs down to the beach, a thatched gate with the sign by the road, palms
    s = stages[0]
    p.box("main_sand", (-10, -8, 0), (10, 8, 0.03), sand, s)
    # 1 cm inside the plot's edge: on it, the lagoon's sides would z-fight with the sand's
    flat("main_lagoon", [(max(-9.99, min(9.99, x)), y) for x, y in SHORE] + [(9.99, 7.99), (-9.99, 7.99)], 0, 0.06, sea, s)
    posts("main_stilts", [(x, y) for x in (-8.0, -5.8, -3.6, -1.6) for y in (-1.0, 2.0, 5.0)], 0, FLOOR - 0.2, 0.13, wood, s)
    p.box("main_deck", (-8.2, -1.2, FLOOR - 0.2), (-1.4, 5.2, FLOOR), deck, s)
    p.box("main_walls", (-7.6, 0, FLOOR), (-2.0, 4.6, FLOOR + STOREY), walls, s)
    boxes("main_windows", [((-5.3, -0.06, FLOOR), (-4.3, 0.02, FLOOR + 2.0)),
                           ((-7.1, -0.06, FLOOR + 0.8), (-6.1, 0.02, FLOOR + 1.8)),
                           ((-3.5, -0.06, FLOOR + 0.8), (-2.5, 0.02, FLOOR + 1.8)),
                           ((-7.66, 0.9, FLOOR + 0.8), (-7.58, 2.0, FLOOR + 1.8)),
                           ((-7.66, 2.8, FLOOR + 0.8), (-7.58, 3.9, FLOOR + 1.8))], glow, s)
    posts("main_veranda_posts", [(-8.0, -1.0), (-1.6, -1.0)], FLOOR, FLOOR + STOREY, 0.1, wood, s)
    thatched_roof("main_roof", -7.6, 0, -2.0, 4.6, FLOOR + STOREY, 4.2, straw, s, 1.3)
    boxes("main_stairs", [((-5.4, -1.2 - 0.3 * (i + 1), 0), (-4.2, -1.2 - 0.3 * i, FLOOR - 0.2 * (i + 1))) for i in range(5)], deck, s)
    boxes("main_path", [((-5.2, y - 0.2, 0), (-4.4, y + 0.2, 0.08)) for y in (-3.3, -4.1, -4.9, -5.7, -6.5)], deck, s)
    posts("main_gate_posts", [(-6.0, -7.4), (-3.6, -7.4)], 0, 3.15, 0.12, wood, s)  # end inside the beam
    p.box("main_gate_beam", (-6.3, -7.55, 2.95), (-3.3, -7.25, 3.2), wood, s)
    p.gable_roof("main_gate_roof", -6.3, -7.55, -3.3, -7.25, 3.2, 0.7, straw, s, overhang=0.2)
    p.box("tint_main_sign", (-5.5, -7.47, 2.15), (-4.1, -7.33, 2.85), cloth, s)
    posts("main_banner_poles", [(-6.8, -7.3), (-2.8, -7.3)], 0, 4.2, 0.05, wood, s)
    p.box("tint_main_banner_0", (-7.5, -7.33, 1.8), (-6.86, -7.27, 4.0), cloth, s)
    p.box("tint_main_banner_1", (-2.74, -7.33, 1.8), (-2.1, -7.27, 4.0), cloth, s)
    for i, (x, y, h, lean) in enumerate(((-9.4, 1.8, 7.0, (1.5, 0.5)), (0.8, -1.4, 6.0, (0.6, -0.9)),
                                         (-8.8, -6.6, 5.4, (1.0, 0.9)))):
        palm(f"main_palm_{i}", x, y, h, lean, trunk, frond, s)

    # 2 overwater bungalows along a boardwalk out from the lodge's deck, a kayak at the landing
    s = stages[1]
    for i, (cx, cy) in enumerate(((1.4, 5.5), (5.6, 5.5), (7.6, -0.3))):
        bungalow(f"bungalow_{i}", cx, cy, (wood, deck, walls, straw, glow), s)
    boxes("bungalow_walk", [((-1.4, 2.5, FLOOR - 0.15), (8.2, 3.5, FLOOR)), ((7.0, 1.7, FLOOR - 0.15), (8.2, 2.5, FLOOR))], deck, s)
    posts("bungalow_walk_posts", [(x, y) for x in (0.6, 3.0, 5.4) for y in (2.6, 3.4)] + [(7.6, 2.0)], 0, FLOOR - 0.15, 0.08, wood, s)
    boxes("bungalow_walk_rail", [((-1.4, 2.5, FLOOR + 0.85), (7.0, 2.58, FLOOR + 0.93))]
          + [((x - 0.04, 2.5, FLOOR), (x + 0.04, 2.58, FLOOR + 0.85)) for x in (-1.35, 0.6, 2.5, 4.4, 6.3)], wood, s)
    p.blob("tint_bungalow_kayak", (3.0, 1.2, 0.14), 1.0, cloth, s, squash=(1.6, 0.3, 0.14))

    # 3 the lagoon pool: a free-form basin in a pale stone rim, a round thatched bar,
    # parasols with loungers, two more palms
    s = stages[2]
    ring("pool_rim", organic(2.6, -5.0, 3.6, 1.9), organic(2.6, -5.0, 3.0, 1.3), 0, 0.25, rim_stone, s)
    flat("pool_water", organic(2.6, -5.0, 3.0, 1.3), 0, 0.16, pool_water, s)
    bar = [(8.0 + 1.15 * math.cos(a), -6.0 + 1.15 * math.sin(a)) for a in (2 * math.pi * k / 12 for k in range(12))]
    ring("pool_bar_counter", bar, [(8.0 + (x - 8.0) * 0.8, -6.0 + (y + 6.0) * 0.8) for x, y in bar], 0, 1.1, wood, s)
    posts("pool_bar_posts", [(8.0 + 1.3 * math.cos(a), -6.0 + 1.3 * math.sin(a)) for a in (2 * math.pi * k / 6 + 0.3 for k in range(6))],
          0, 2.6, 0.08, wood, s)
    p.cone_roof("pool_bar_roof", 8.0, -6.0, 2.6, 1.9, 1.8, straw, s)
    for i, y in enumerate((-5.4, -2.6)):
        pole, _ = p.parasol(f"tint_pool_parasol_{i}", -2.2, y, stripes, wood, s)
        pole.name = pole.name.removeprefix("tint_")  # only the canopy takes the owner colour
        p.box(f"pool_lounger_{i}", (-3.4, y - 1.2, 0), (-2.7, y + 0.7, 0.4), canvas, s)
    for i, (x, y, h, lean) in enumerate(((5.8, -7.0, 6.2, (-0.4, 1.4)), (-0.6, -7.2, 5.8, (0.3, 1.5)))):
        palm(f"pool_palm_{i}", x, y, h, lean, trunk, frond, s)

    return stages
