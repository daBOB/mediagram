"""Alpenrose's own shapes, materials and layout constants
(the stages are built in `alpenrose.py`)."""

from functools import partial
import bmesh
from mathutils import Matrix
import materials as m
import parts as p


STOREY = 3.0

BAY = 2.2

PLOT = (24, 16)

def chalet_wall(name, attic_z, attic_half, base=STOREY, wood="#a27248", stone="#d2cabd", shutter="#3d6e44",
                frame="#f1ece2", glass="#26303a", glow="#ffb066"):
    """Stone below `base`, larch planks above with a dark floor beam at each
    storey; framed windows with green louvred shutters upstairs, smaller
    white-framed ones in the stone. Above `attic_z` (the gable) only the
    middle bay (|x| < attic_half) gets a window."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    u = g.op("FRACT", g.op("DIVIDE", g.op("ADD", x, y), BAY))
    v = g.op("FRACT", g.op("DIVIDE", z, STOREY))
    upper = g.op("GREATER_THAN", z, base)
    lower = g.op("SUBTRACT", 1.0, upper)
    allowed = g.op("MAXIMUM", g.op("LESS_THAN", z, attic_z), g.op("LESS_THAN", g.op("ABSOLUTE", x), attic_half))
    up = g.op("MULTIPLY", upper, allowed)
    band = g.within(v, 0.3, 0.76)
    win = g.op("MULTIPLY", up, g.op("MULTIPLY", g.within(u, 0.3, 0.7), band))
    shut = g.op("MULTIPLY", up, g.op("MULTIPLY", g.op("ADD", g.within(u, 0.1, 0.29), g.within(u, 0.71, 0.9)), band))
    gwin = g.op("MULTIPLY", lower, g.op("MULTIPLY", g.within(u, 0.34, 0.66), g.within(v, 0.3, 0.72)))
    pane = g.op("ADD", g.op("MULTIPLY", up, g.op("MULTIPLY", g.within(u, 0.33, 0.67), g.within(v, 0.33, 0.73))),
                g.op("MULTIPLY", lower, g.op("MULTIPLY", g.within(u, 0.38, 0.62), g.within(v, 0.35, 0.68))))
    mullion = g.op("MAXIMUM", g.op("LESS_THAN", g.op("ABSOLUTE", g.op("SUBTRACT", u, 0.5)), 0.012),
                   g.op("LESS_THAN", g.op("ABSOLUTE", g.op("SUBTRACT", v, 0.56)), 0.012))
    pane = g.op("MULTIPLY", pane, g.op("SUBTRACT", 1.0, mullion))
    beam = g.op("MULTIPLY", upper, g.op("LESS_THAN", v, 0.04))
    louvre = g.op("LESS_THAN", g.op("FRACT", g.op("MULTIPLY", z, 7.0)), 0.3)
    col = g.mix(upper, g.image("stone_wall", 2.5, stone), g.image("weathered_plank_siding", 2.0, wood, "OVERLAY", 0.7))
    col = g.mix(beam, col, "#4a3020")
    col = g.mix(shut, col, g.mix(louvre, shutter, "#2b4e30"))
    col = g.mix(g.op("MAXIMUM", win, gwin), col, frame)
    g.color(g.mix(pane, col, glass))
    g.nt.links.new(g.mix(pane, "#000000", glow), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.025
    return g.mat

def rail_boards(name, wood="#9a6236"):
    """Solid board balustrade with a cut slot between every pair of boards."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    slot = g.op("MULTIPLY", g.within(g.op("FRACT", g.op("DIVIDE", g.op("ADD", x, y), 0.3)), 0.42, 0.58),
                g.within(g.op("MULTIPLY", g.op("FRACT", g.op("DIVIDE", z, STOREY)), STOREY), 0.45, 0.85))
    return g.color(g.mix(slot, g.image("weathered_plank_siding", 1.5, wood, "OVERLAY", 0.7), "#24180f"))

def blooms(name, leaf, flower, scale, size):
    """Leaves dotted with flowers (a Voronoi cell's centre), as geometry-free detail."""
    g = m.Graph(name)
    vor = g.nt.nodes.new("ShaderNodeTexVoronoi")
    vor.inputs["Scale"].default_value = scale
    g.nt.links.new(g.obj, vor.inputs["Vector"])
    return g.color(g.mix(g.op("LESS_THAN", vor.outputs["Distance"], size), leaf, flower))

def chalet_roof(name, x0, y0, x1, y1, z, rise, roof, soffit, gable, parent, side=1.0, front=1.5, back=0.8, thick=0.3):
    """A low gable roof facing the road (ridge along y): two thick slabs with
    timber undersides and fascias, over a timber gable wall flush with the
    facade, so the deep overhang reads as on a real chalet."""
    cx, slope = (x0 + x1) / 2, rise / ((x1 - x0) / 2)
    ya, yb = y0 - front, y1 + back

    def slabs(bm):
        for xe in (x0 - side, x1 + side):
            ze = z - side * slope
            lo = [(xe, ya, ze), (cx, ya, z + rise), (cx, yb, z + rise), (xe, yb, ze)]
            v = [bm.verts.new(q) for q in lo] + [bm.verts.new((qx, qy, qz + thick)) for qx, qy, qz in lo]
            for f in [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]:
                bm.faces.new([v[i] for i in f])

    obj = p.mesh_object(name, slabs, roof, parent)
    obj.data.materials.append(soffit)
    for poly in obj.data.polygons:
        poly.material_index = 0 if poly.normal.z > 0.3 else 1

    def prism(bm):
        a = [bm.verts.new(q) for q in ((x0, y0, z), (x1, y0, z), (cx, y0, z + rise))]
        b = [bm.verts.new(q) for q in ((x0, y1, z), (x1, y1, z), (cx, y1, z + rise))]
        bm.faces.new(a)
        bm.faces.new(b[::-1])
        for i in range(3):
            j = (i + 1) % 3
            bm.faces.new((a[i], b[i], b[j], a[j]))

    p.mesh_object(name + "_gable", prism, gable, parent)
    return obj

def balcony(name, x0, x1, y_face, z, depth, wood, rail, planter, flowers, parent):
    """A deep balcony on a timber slab with a solid board balustrade and a
    flower box of geraniums hung on its front."""
    yb = y_face - depth

    def frame(bm):
        p.add_box(bm, x0, yb, z, x1, y_face, z + 0.2)

    def boards(bm):
        p.add_box(bm, x0, yb, z + 0.2, x1, yb + 0.07, z + 1.0)
        p.add_box(bm, x0, yb + 0.07, z + 0.2, x0 + 0.07, y_face, z + 1.0)
        p.add_box(bm, x1 - 0.07, yb + 0.07, z + 0.2, x1, y_face, z + 1.0)

    def clumps(bm):
        n = max(1, round((x1 - x0 - 0.3) / 0.85))
        for i in range(n):
            cx = x0 + 0.15 + (x1 - x0 - 0.3) * (i + 0.5) / n
            bmesh.ops.create_icosphere(bm, subdivisions=1, radius=0.3, matrix=Matrix.Translation(
                (cx, yb - 0.14, z + 1.05)) @ Matrix.Diagonal((1.55, 0.95, 0.8, 1.0)))

    p.mesh_object(name, frame, wood, parent)
    p.mesh_object(name + "_rail", boards, rail, parent)
    p.box(name + "_planter", (x0 + 0.1, yb - 0.27, z + 0.72), (x1 - 0.1, yb, z + 0.98), planter, parent)
    p.mesh_object(name + "_geraniums", clumps, flowers, parent)

def fir(name, x, y, height, radius, needles, bark, parent):
    """A spruce: a short trunk under three stacked cones."""

    def tiers(bm):
        for z0, r, rise in ((0.12, 1.0, 0.42), (0.32, 0.8, 0.38), (0.5, 0.58, 0.34), (0.66, 0.36, 0.34)):
            bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=radius * r, radius2=0.02, depth=height * rise,
                                  matrix=Matrix.Translation((x, y, height * (z0 + rise / 2))))

    p.post(name + "_trunk", x, y, 0, height * 0.3, 0.1 + radius * 0.06, bark, parent)
    p.mesh_object(name, tiers, needles, parent)


# the shared cylinder at this hotel's own segment count
cylinder = partial(p.cylinder, segments=12)
