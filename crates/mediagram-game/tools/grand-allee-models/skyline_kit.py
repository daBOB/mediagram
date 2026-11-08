"""Skyline's own shapes, materials and layout constants
(the stages are built in `skyline.py`)."""

import math
from mathutils import Matrix
import materials as m
import parts as p


STOREY = 3.2

# storeys in each of the three shaft bands, and the crown's height factor: 40 m tall as built
SHAFT_STOREYS = (3, 2, 2)

CROWN_SCALE = 1.0

PLOT = (32, 14)

BAY = 1.5

def _up(g):
    """1 on faces that look up: roofs and terraces."""
    sep = g.nt.nodes.new("ShaderNodeSeparateXYZ")
    g.nt.links.new(g.obj.node.outputs["Normal"], sep.inputs[0])
    return g.op("GREATER_THAN", sep.outputs["Z"], 0.5)

def deco_facade(name, stone="#ebe0cb", spandrel="#3b342e", glass="#33404d", lit="#6e3c1a", glow="#ff9a3c",
                granite="#2f2d31", gold="#c7a259", roof="#7a756e"):
    """Limestone piers between recessed strips of window and dark spandrel, so
    the facade reads as vertical lines; a random share of the rooms is lit.
    The ground storey is dark granite with tall lit shop windows under a gilt
    band. Mirrors at x = 0; faces that look up are gravel roofs."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    s = g.op("DIVIDE", g.op("ADD", g.op("ABSOLUTE", x), y), BAY)
    u, row = g.op("FRACT", s), g.op("FLOOR", g.op("DIVIDE", z, STOREY))
    v = g.op("FRACT", g.op("DIVIDE", z, STOREY))
    base = g.op("LESS_THAN", z, STOREY)
    strip = g.within(u, 0.3, 0.96)
    pane = g.op("MULTIPLY", strip, g.within(v, 0.15, 0.85))
    cell = g.nt.nodes.new("ShaderNodeCombineXYZ")
    for k, src in zip("XYZ", (g.op("FLOOR", s), row, g.op("SIGN", x))):
        g.nt.links.new(src, cell.inputs[k])
    noise = g.nt.nodes.new("ShaderNodeTexWhiteNoise")
    noise.noise_dimensions = "3D"
    g.nt.links.new(cell.outputs[0], noise.inputs["Vector"])
    on = g.op("MULTIPLY", pane, g.op("GREATER_THAN", noise.outputs["Value"], 0.72))
    upper = g.mix(strip, g.image("plastered_wall_04", 3.0, stone, "OVERLAY", 0.5), g.mix(pane, spandrel, g.mix(on, glass, lit)))
    window = g.op("MULTIPLY", g.within(u, 0.12, 0.88), g.within(z, 0.45, 2.6))
    lower = g.mix(g.within(z, 2.75, 3.05), g.mix(window, granite, lit), gold)
    up = _up(g)
    g.color(g.mix(up, g.mix(base, upper, lower), g.image("gravel_floor", 2.0, roof, "OVERLAY", 0.5)))
    glowing = g.op("MULTIPLY", g.op("MAXIMUM", g.op("MULTIPLY", on, g.op("SUBTRACT", 1.0, base)), g.op("MULTIPLY", window, base)),
                   g.op("SUBTRACT", 1.0, up))
    g.nt.links.new(g.mix(glowing, "#000000", glow), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.6
    return g.mat

def ribbed(name, color="#c9a45c", groove="#6e5631", pitch=0.45):
    """Gilt cladding with vertical ribs: the crown and the sunburst over the door."""
    g = m.Graph(name)
    x, y = g.xyz.outputs["X"], g.xyz.outputs["Y"]
    rib = g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", g.op("ADD", g.op("ABSOLUTE", x), y), pitch)), 0.3)
    return g.color(g.mix(g.op("MULTIPLY", rib, g.op("SUBTRACT", 1.0, _up(g))), color, groove))

def sunburst(name, cx, cz, rays=16, color="#d6b46c", groove="#6e5631"):
    """Gilt rays fanning up from (cx, cz): the art-deco sunburst over the door."""
    g = m.Graph(name)
    x, z = g.xyz.outputs["X"], g.xyz.outputs["Z"]
    angle = g.op("ARCTAN2", g.op("SUBTRACT", z, cz), g.op("SUBTRACT", x, cx))
    ray = g.op("LESS_THAN", g.op("FRACT", g.op("MULTIPLY", angle, rays / math.pi)), 0.5)
    return g.color(g.mix(ray, color, groove))

def glowing(name, color, strength):
    g = m.Graph(name, color)
    g.bsdf.inputs["Emission Color"].default_value = (*m.hex_rgb(color), 1)
    g.bsdf.inputs["Emission Strength"].default_value = strength
    return g.mat

def doors(name, glass="#6e3c1a", glow="#ff9a3c", bronze="#4a3622", pitch=0.6):
    """Lit glass doors between bronze mullions and a transom bar."""
    g = m.Graph(name)
    x, z = g.xyz.outputs["X"], g.xyz.outputs["Z"]
    bar = g.op("MAXIMUM", g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", x, pitch)), 0.12), g.within(z, 2.05, 2.2))
    g.color(g.mix(bar, glass, bronze))
    g.nt.links.new(g.mix(bar, glow, "#000000"), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.6
    return g.mat

def parapet(name, x0, y0, x1, y1, z, material, parent, height=0.8, thick=0.25):
    def build(bm):
        p.add_box(bm, x0, y0, z, x1, y0 + thick, z + height)
        p.add_box(bm, x0, y1 - thick, z, x1, y1, z + height)
        p.add_box(bm, x0, y0 + thick, z, x0 + thick, y1 - thick, z + height)
        p.add_box(bm, x1 - thick, y0 + thick, z, x1, y1 - thick, z + height)

    return p.mesh_object(name, build, material, parent)

def lift(objs, dz):
    """Raise parts built at ground level (parasols) onto a terrace, keeping
    every part's origin at the plot origin."""
    for o in objs:
        o.data.transform(Matrix.Translation((0, 0, dz)))
    return objs

def band(name, x0, y0, x1, y1, z0, z1, wall, stone, parent):
    """A storey band of the tower: the block, a coping round its roof and a
    slim pier at each corner running the band's full height."""
    p.box(name, (x0, y0, z0), (x1, y1, z1), wall, parent)
    parapet(name + "_coping", x0 - 0.1, y0 - 0.1, x1 + 0.1, y1 + 0.1, z1, stone, parent, height=0.6, thick=0.3)

    def piers(bm):
        for px in (x0, x1):
            for py in (y0, y1):
                p.add_box(bm, px - 0.25, py - 0.25, z0, px + 0.25, py + 0.25, z1 + 0.9)

    p.mesh_object(name + "_piers", piers, stone, parent)
