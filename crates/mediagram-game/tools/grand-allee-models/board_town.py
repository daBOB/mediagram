"""The Bank and the Rathaus at the road's two ends, and the parapets of the two
bridges. The buildings are our own designs in the hotels' style family (matte
textured walls with lit windows, slate and clay roofs), built at hotel size in
their own space (x along the front, y back from it, z up; the front faces -y,
the TV) and shrunk like the hotels."""

import math

import bmesh
from mathutils import Matrix

import board_ink as ink
import materials as m
import parts as p
from board_kit import BANK_AT, BRIDGES, RATHAUS_AT, SCALE, offset


def site(name, at):
    s = p.empty(name)
    s.location, s.scale = (*at, 0), (SCALE,) * 3
    return s


def pyramid(name, x0, y0, x1, y1, z, rise, material, parent):
    def build(bm):
        base = [bm.verts.new(q) for q in ((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z))]
        top = bm.verts.new(((x0 + x1) / 2, (y0 + y1) / 2, z + rise))
        for i in range(4):
            bm.faces.new((base[i], base[(i + 1) % 4], top))
        bm.faces.new(base[::-1])

    return p.mesh_object(name, build, material, parent)


def sign(name, body, y, z, height, width, parent):
    """Gilded letters 6 cm proud of a south wall at y (flat, facing the TV)."""
    obj = ink.text(name, body, lambda u, v: (u, y - 0.06, z + v), m.plain(name, "#c9a24a"), height, width, parent=parent)
    obj.visible_shadow = True
    return obj


def bank():
    """A small classical bank: a plinth, two lit storeys under a slate hip roof,
    a portico of four columns with a stone pediment, BANK on the frieze."""
    s = site("bank", BANK_AT)
    stone = m.textured("bank_stone", "sandstone_blocks_05", 2.0, "#efe6d6")
    walls = m.facade("bank_walls", wall_tint="#f1e6cf", ground_tint="#e8dcc6", frame="#d9cfbd", bay=2.4)
    slate = m.textured("bank_slate", "roof_slates_02", 2.5, "#6d7380")
    p.box("bank_plinth", (-6, -6.6, 0), (6, 4.5, 0.6), stone, s)
    p.box("bank_step_0", (-3.6, -7.4, 0), (3.6, -6.6, 0.4), stone, s)
    p.box("bank_step_1", (-3.6, -8.2, 0), (3.6, -7.4, 0.2), stone, s)
    p.box("bank_body", (-5.5, -3.5, 0.6), (5.5, 4.0, 6.6), walls, s)
    p.cornice("bank_cornice", -5.5, -3.5, 5.5, 4.0, 6.6, stone, s)
    p.hip_roof("bank_roof", -5.5, -3.5, 5.5, 4.0, 6.6, 2.2, slate, s)
    for i, x in enumerate((-3.3, -1.1, 1.1, 3.3)):
        p.cylinder(f"bank_column_{i}", x, -5.7, 0.6, 5.6, 0.36, stone, s, r1=0.3, segments=8)
    p.box("bank_frieze", (-3.9, -6.3, 5.6), (3.9, -3.5, 6.4), stone, s)
    p.gable_roof("bank_pediment", -3.9, -6.3, 3.9, -3.5, 6.4, 1.5, stone, s, overhang=0.2, ridge_along="y")
    sign("bank_sign", "BANK", -6.3, 6.0, 0.5, 5.5, s)
    return list(s.children)


def rathaus():
    """A town hall: two lit storeys under a clay hip roof, a clock tower before
    it with a slate spire, a balcony over the door, RATHAUS above the balcony."""
    s = site("rathaus", RATHAUS_AT)
    stone = m.textured("rathaus_stone", "sandstone_blocks_05", 2.0, "#e9dcc4")
    walls = m.facade("rathaus_walls", wall_tint="#f3d9a6", ground_tint="#efe0c8", frame="#e9e1d2", bay=2.3)
    tiles = m.textured("rathaus_tiles", "clay_roof_tiles_02", 2.5, "#c0705a")
    slate = m.textured("rathaus_slate", "roof_slates_02", 2.5, "#5f6b6e")
    p.box("rathaus_plinth", (-5, -3.9, 0), (5, 4.25, 0.5), stone, s)
    p.box("rathaus_steps", (-1.4, -4.25, 0), (1.4, -3.9, 0.25), stone, s)
    p.box("rathaus_body", (-4.6, -3.4, 0.5), (4.6, 3.8, 6.6), walls, s)
    p.cornice("rathaus_cornice", -4.6, -3.4, 4.6, 3.8, 6.6, stone, s, out=0.2, height=0.3)
    p.hip_roof("rathaus_roof", -4.6, -3.4, 4.6, 3.8, 6.6, 2.6, tiles, s, overhang=0.35)
    p.box("rathaus_tower", (-1.5, -3.8, 0.5), (1.5, -0.8, 11.0), walls, s)
    p.cornice("rathaus_tower_cornice", -1.5, -3.8, 1.5, -0.8, 11.0, stone, s, out=0.2, height=0.3)
    pyramid("rathaus_spire", -1.7, -4.0, 1.7, -0.6, 11.0, 2.8, slate, s)

    def clock(bm):  # a dial 2 cm off the tower's face, its hands 1 cm off the dial
        bmesh.ops.create_cone(bm, cap_ends=True, segments=16, radius1=0.9, radius2=0.9, depth=0.1,
                              matrix=Matrix.Translation((0, -3.87, 9.3)) @ Matrix.Rotation(math.pi / 2, 4, "X"))

    p.mesh_object("rathaus_clock", clock, m.plain("rathaus_dial", "#f4efe4"), s)
    hands = m.plain("rathaus_hands", "#2e2a26")
    p.box("rathaus_hand_hour", (-0.05, -3.96, 9.25), (0.5, -3.93, 9.35), hands, s)
    p.box("rathaus_hand_minute", (-0.04, -3.97, 9.2), (0.06, -3.94, 10.0), hands, s)  # off the hour hand's faces
    p.balcony_row("rathaus_balcony", -1.3, 1.3, -3.8, 3.3, 0.6, stone, s, posts=4)
    sign("rathaus_sign", "RATHAUS", -3.8, 5.15, 0.4, 2.6, s)
    return list(s.children)


def obox(bm, a, b, l0, l1, z0, z1, t0=0.0, t1=1.0):
    """A box along part t0..t1 of the segment a -> b, l0..l1 to its left, z0..z1 high."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = math.hypot(dx, dy)
    nx, ny = -dy / L, dx / L
    base = [(a[0] + dx * t + nx * l, a[1] + dy * t + ny * l) for t, l in ((t0, l0), (t1, l0), (t1, l1), (t0, l1))]
    v = [bm.verts.new((x, y, z)) for z in (z0, z1) for x, y in base]
    for f in ((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
        bm.faces.new([v[i] for i in f])


def bridges(L):
    """Over fields 1 and 21: a low stone parapet along each curved road edge where
    the kerb stops, an iron railing on it, stone pillars at the ends. The deck is
    the field itself; the river's channel is in the ground's bake."""
    from board_ground import strip
    stone = m.textured("bridge_stone", "sandstone_blocks_05", 1.5, "#d8ccb6")
    iron = m.plain("bridge_iron", "#3a3632")
    out = []
    for fid in BRIDGES:
        o = L.fields[fid]["outline"]
        for tag, line in (("outer", o[:7]), ("inner", o[7:])):  # each runs with the outside on its left
            ends = ((line[0], line[1], 0.0), (line[-2], line[-1], 1.0))

            def masonry(bm, line=line, ends=ends):
                strip(bm, bm.loops.layers.uv.new("atlas"), offset(line, -0.02), 0.32, 0.32, False)
                for a, b, t in ends:
                    k = 1 / math.dist(a, b)
                    obox(bm, a, b, -0.06, 0.38, 0, 1.05, t - 0.25 * k, t + 0.25 * k)
                    obox(bm, a, b, -0.1, 0.42, 1.05, 1.15, t - 0.3 * k, t + 0.3 * k)

            def railing(bm, line=line):
                strip(bm, bm.loops.layers.uv.new("atlas"), offset(line, 0.1), 0.1, 0.08, False, z0=0.86)
                for a, b in zip(line[:-2], line[1:-1]):  # a baluster at every inner point of the edge
                    k = 1 / math.dist(a, b)
                    obox(bm, a, b, 0.12, 0.18, 0.32, 0.86, 1 - 0.035 * k, 1 + 0.035 * k)

            out += [ink.solid(f"bridge_{fid}_{tag}_parapet", masonry, stone), ink.solid(f"bridge_{fid}_{tag}_railing", railing, iron)]
    return out
