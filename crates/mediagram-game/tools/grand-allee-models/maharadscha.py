"""Maharadscha (P1, park side, India): a cream palace of lime plaster and red
sandstone, a ground-floor arcade of pointed arches, flat roofs behind merloned
parapets, a tall entrance portal and a great onion dome; two wings, two domed
corner towers, chhatri pavilions on the roofs, and a long water garden with
fountains, palms and shamiana tents as its leisure wing. Six stages.

Plot: 32 m along the road (x -16..16), 14 m deep (y -7..7); the road runs
along y = -7, so the fronts face -y."""

import materials as m
import parts as p
from maharadscha_kit import (  # noqa: F401  (PLOT is read by build.py)
    PLOT,
    STOREY,
    TRIM,
    chhatri,
    domed,
    fountain,
    palace_wall,
    palm,
    parapet,
    portal_wall,
    tent,
)


def build(owner_hex):
    wall = palace_wall("maha_wall")
    portal = portal_wall("maha_portal")
    sandstone = m.textured("maha_sandstone", "sandstone_blocks_05", 3.5, TRIM, "OVERLAY", 0.75)
    dome = m.textured("maha_dome", "plastered_wall_04", 3.0, "#f6efe2", "OVERLAY", 0.4)
    gold = m.plain("maha_gold", "#c99b45")
    water = m.plain("maha_water", "#3f7f8f")
    spray = m.plain("maha_spray", "#cfe6ee")
    bark = m.plain("maha_bark", "#7a6048")
    leaf = m.plain("maha_leaf", "#4f7a3a")
    # white in the export; the owner's colour on the TV (the preview shows `owner_hex`)
    cloth = m.plain("maha_tint_cloth", owner_hex)
    tent_cloth = m.striped("maha_tint_tent", owner_hex, width=0.35)

    stages = [p.empty(f"stage_{i}") for i in range(1, 7)]
    top = 3 * STOREY

    # 1 the palace: three storeys, the entrance iwan rising above the roof, the great dome
    s = stages[0]
    p.box("main_block", (-6.6, 0, 0), (6.6, 6.5, top), wall, s)
    for i in (1, 2):
        p.cornice(f"main_chhajja_{i}", -6.6, 0, 6.6, 6.5, i * STOREY + 0.2, sandstone, s, out=0.5, height=0.2)
    parapet("main_parapet", -6.6, 0, 6.6, 6.5, top, wall, s)
    p.box("main_portal", (-2.2, -0.8, 0), (2.2, 0.05, 12.0), portal, s)
    p.box("main_portal_coping", (-2.35, -0.95, 12.0), (2.35, 0.05, 12.25), sandstone, s)
    for i, x in enumerate((-2.0, 2.0)):
        p.post(f"main_pinnacle_{i}", x, -0.6, 12.25, 13.3, 0.2, sandstone, s, segments=8)
        domed(f"main_pinnacle_dome_{i}", x, -0.6, 13.3, 0.32, dome, gold, s, segments=8)
    pole = domed("main_dome", 0, 3.4, top, 2.45, dome, gold, s, segments=16, drum=1.5, drum_mat=wall)
    p.box("tint_main_flag", (0.07, 3.37, pole - 0.9), (1.6, 3.43, pole - 0.05), cloth, s)
    p.box("tint_main_sign", (-1.5, -0.9, 7.9), (1.5, -0.8, 8.8), cloth, s)
    p.box("main_steps", (-2.0, -1.7, 0), (2.0, -0.8, 0.2), sandstone, s)

    # 2 and 3: a two-storey wing either side, set back, with a merloned parapet
    for stage, sx, side in ((stages[1], -1, "west"), (stages[2], 1, "east")):
        x0, x1 = sorted((sx * 6.55, sx * 12.0))
        p.box(f"{side}_block", (x0, 1.4, 0), (x1, 6.5, 2 * STOREY), wall, stage)
        p.cornice(f"{side}_chhajja", x0, 1.4, x1, 6.5, STOREY + 0.15, sandstone, stage, out=0.45, height=0.2)
        parapet(f"{side}_parapet", x0, 1.4, x1, 6.5, 2 * STOREY, wall, stage)

    # 4 the corner towers: three storeys, projecting, each under an onion dome with a flag
    for i, sx in enumerate((-1, 1)):
        s = stages[3]
        x0, x1 = sorted((sx * 11.9, sx * 15.6))
        side = ("west", "east")[i]
        p.box(f"tower_{side}", (x0, -0.6, 0), (x1, 3.6, top), wall, s)
        for k in (1, 2):
            p.cornice(f"tower_{side}_chhajja_{k}", x0, -0.6, x1, 3.6, k * STOREY + 0.2, sandstone, s, out=0.4, height=0.2)
        parapet(f"tower_{side}_parapet", x0, -0.6, x1, 3.6, top, wall, s, merlon=0.75)
        pole = domed(f"tower_{side}_dome", sx * 13.75, 1.5, top, 1.5, dome, gold, s, drum=0.6, drum_mat=wall)
        fx = sx * 13.75 + 0.06
        p.box(f"tint_tower_{side}_flag", (fx, 1.47, pole - 0.7), (fx + 1.2, 1.53, pole - 0.05), cloth, s)

    # 5 chhatris on the roofs: two at the palace's front corners, one on each wing
    s = stages[4]
    for i, (x, y, z, size) in enumerate(((-5.0, 1.4, top, 1.6), (5.0, 1.4, top, 1.6),
                                         (-9.3, 3.9, 2 * STOREY, 2.0), (9.3, 3.9, 2 * STOREY, 2.0))):
        chhatri(f"roof_chhatri_{i}", x, y, z, size, sandstone, dome, gold, s)

    # 6 the water garden: twin reflecting pools either side of a causeway with a
    # fountain basin, jets down the pools, palms on the road side, shamianas at the ends
    s = stages[5]
    p.box("garden_causeway", (-1.3, -7.0, 0), (1.3, -1.7, 0.08), sandstone, s)
    for i, (x0, x1) in enumerate(((-11.5, -1.8), (1.8, 11.5))):
        p.pool(f"garden_pool_{i}", x0, -4.8, x1, -2.8, sandstone, water, s, rim=0.5, top=0.35)
    p.pool("garden_basin", -0.8, -4.6, 0.8, -3.0, sandstone, water, s, rim=0.35, top=0.5)
    fountain("garden_fountain", 0, -3.8, 0.4, 1.8, spray, s, subdiv=2)
    for i, x in enumerate((-8.6, -4.7, 4.7, 8.6)):
        fountain(f"garden_jet_{i}", x, -3.8, 0.2, 1.1, spray, s)
    for i, x in enumerate((-10.0, -6.6, 6.6, 10.0)):
        palm(f"garden_palm_{i}", x, -5.6, 5.2 + 0.4 * (i % 2), bark, leaf, s, reach=1.35)
    for i, sx in enumerate((-1, 1)):
        tent(f"tint_garden_tent_{i}", sx * 13.9, -3.8, 2.6, 2.2, 2.6, tent_cloth, gold, s)

    return stages
