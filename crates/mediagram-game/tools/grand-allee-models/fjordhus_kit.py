"""Fjordhus's own shapes, materials and layout constants
(the stages are built in `fjordhus.py`)."""

import math
import bmesh
from mathutils import Matrix
import materials as m
import parts as p


STOREY = 3.0

PLOT = (24, 16)

SILL = 0.5  # the stone plinth under the boards

def red_boards(name, eaves, sill=SILL, storey=STOREY, bay=2.0, red="#a3301f", trim="#f1eee6"):
    """Falu-red boards with a white-framed, cross-barred window per bay and
    storey between the plinth and the eaves, lit warm from inside."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    u = g.op("FRACT", g.op("DIVIDE", g.op("ADD", x, y), bay))
    v = g.op("FRACT", g.op("DIVIDE", g.op("SUBTRACT", z, sill), storey))
    rows = g.within(z, sill, eaves)
    frame = g.op("MULTIPLY", g.op("MULTIPLY", g.within(u, 0.3, 0.7), g.within(v, 0.25, 0.8)), rows)
    pane = g.op("MULTIPLY", g.op("MULTIPLY", g.within(u, 0.34, 0.66), g.within(v, 0.29, 0.76)), rows)
    bars = g.op("MAXIMUM", g.within(u, 0.49, 0.51), g.within(v, 0.515, 0.54))
    lit = g.op("MULTIPLY", pane, g.op("SUBTRACT", 1.0, bars))
    boards = g.image("weathered_plank_siding", 2.4, red, "OVERLAY", 0.45)
    g.color(g.mix(lit, g.mix(frame, boards, trim), "#2f2a26"))
    g.nt.links.new(g.mix(lit, "#000000", "#ffb35c"), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.08
    return g.mat

def gabled(name, x0, y0, x1, y1, eaves, rise, wall, roof, trim, parent, ridge="x", overhang=0.45, thick=0.22, sill=SILL):
    """A house: walls with gable ends in `wall`, two roof slabs in `roof`, and
    (with `trim`) white bargeboards along the rakes and corner boards."""
    if ridge == "x":
        a0, a1, b0, b1, P = x0, x1, y0, y1, (lambda b, z, a: (a, b, z))
    else:
        a0, a1, b0, b1, P = y0, y1, x0, x1, (lambda b, z, a: (b, a, z))
    bc, top = (b0 + b1) / 2, eaves + rise
    ze = eaves - overhang * rise / (bc - b0)  # the slab's underside at the eaves' edge
    A0, A1 = a0 - overhang, a1 + overhang
    body = p.mesh_object(name, lambda bm: p.prism(bm, [(b0, 0), (b1, 0), (b1, eaves), (bc, top), (b0, eaves)], a0, a1, P), wall, parent)

    def slabs(bm):
        for be in (b0 - overhang, b1 + overhang):
            p.prism(bm, [(be, ze), (bc, top), (bc, top + thick), (be, ze + thick)], A0, A1, P)

    parts = [body, p.mesh_object(name + "_roof", slabs, roof, parent)]
    if trim is not None:
        def boards(bm):
            # 2 cm proud of the slab's edge: flush with it, the faces would z-fight
            for be in (b0 - overhang - 0.02, b1 + overhang + 0.02):
                ridge = bc + (0.01 if be < bc else -0.01)  # 1 cm past the ridge, off the slab's ridge face
                for a in (A0, A1):
                    p.prism(bm, [(be, ze - 0.14), (ridge, top - 0.14), (ridge, top + thick + 0.03), (be, ze + thick + 0.03)], a - 0.04, a + 0.04, P)
            for cx in (x0, x1):
                for cy in (y0, y1):
                    p.add_box(bm, cx - 0.1, cy - 0.1, sill, cx + 0.1, cy + 0.1, eaves)

        parts.append(p.mesh_object(name + "_trim", boards, trim, parent))
    return parts

def gable_window(name, x, y_face, z, glass, trim, parent, w=0.9, h=1.1):
    """A white-framed lit window high in a gable that faces the road."""
    p.box(name + "_frame", (x - w / 2 - 0.1, y_face - 0.06, z - 0.1), (x + w / 2 + 0.1, y_face + 0.02, z + h + 0.1), trim, parent)
    p.box(name, (x - w / 2, y_face - 0.09, z), (x + w / 2, y_face - 0.05, z + h), glass, parent)

def pine(name, x, y, height, needles, bark, parent):
    """A spruce: a short trunk under three stacked cones."""
    p.post(name + "_trunk", x, y, 0, height * 0.3, 0.14, bark, parent, segments=6)
    k = height / 7.0

    def crown(bm):
        for z, r, h in ((1.2, 1.5, 3.0), (2.8, 1.15, 2.6), (4.3, 0.8, 2.7)):
            bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=r * k, radius2=0.02, depth=h * k,
                                  matrix=Matrix.Translation((x, y, (z + h / 2) * k)))

    return p.mesh_object(name, crown, needles, parent)

def birch(name, x, y, height, leaves, bark, parent):
    p.post(name + "_trunk", x, y, 0, height * 0.75, 0.1, bark, parent, segments=6)
    p.blob(name + "_crown", (x, y, height * 0.72), 1.1, leaves, parent, squash=(1.0, 1.0, 1.35), subdiv=2)
    p.blob(name + "_crown_low", (x + 0.4, y - 0.3, height * 0.5), 0.75, leaves, parent, subdiv=1)

def picket_fence(name, x0, x1, y, material, parent, height=1.0, gap=0.45):
    """White pickets on two rails, as geometry."""

    def build(bm):
        n = round((x1 - x0) / gap)
        for i in range(n + 1):
            px = x0 + (x1 - x0) * i / n
            p.add_box(bm, px - 0.045, y - 0.02, 0, px + 0.045, y + 0.02, height)
        for z in (0.25, 0.7):
            p.add_box(bm, x0, y + 0.02, z, x1, y + 0.07, z + 0.08)

    return p.mesh_object(name, build, material, parent)

def pond_outline(cx, cy, rx, ry, n=18):
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        k = 1 + 0.08 * math.sin(2 * a + 0.6) + 0.05 * math.sin(5 * a)
        pts.append((cx + rx * k * math.cos(a), cy + ry * k * math.sin(a)))
    return pts
