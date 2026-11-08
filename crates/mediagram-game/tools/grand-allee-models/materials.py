"""Matte materials for the Grand Allee hotels: no specular anywhere, as the
"Abendsonne" look test decided. Real CC0 textures (`textures/`, Poly Haven)
are projected from object coordinates in metres with box projection; every
part's origin is the plot origin, so patterns line up across parts and no UV
unwrap is needed before the bake turns it all into the hotel's atlas."""

import os

import bpy

TEXTURES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "textures")


def hex_rgb(h):
    """sRGB hex to linear RGB, as Blender's colour inputs expect."""
    h = h.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in c)


class Graph:
    """A matte material and helpers to wire its node tree."""

    def __init__(self, name, color="#ffffff"):
        self.mat = bpy.data.materials.new(name)
        self.nt = self.mat.node_tree
        self.bsdf = next(n for n in self.nt.nodes if n.type == "BSDF_PRINCIPLED")
        self.bsdf.inputs["Base Color"].default_value = (*hex_rgb(color), 1)
        self.bsdf.inputs["Roughness"].default_value = 1.0
        self.bsdf.inputs["Specular IOR Level"].default_value = 0.0
        coords = self.nt.nodes.new("ShaderNodeTexCoord")
        self.obj = coords.outputs["Object"]
        self.xyz = self.nt.nodes.new("ShaderNodeSeparateXYZ")
        self.nt.links.new(self.obj, self.xyz.inputs[0])

    def _in(self, sock, src):
        if isinstance(src, str):
            sock.default_value = (*hex_rgb(src), 1)
        elif isinstance(src, (int, float)):
            sock.default_value = src
        else:
            self.nt.links.new(src, sock)

    def image(self, name, metres, tint="#ffffff", blend="MULTIPLY", factor=1.0):
        """A texture covering `metres` per repeat, combined with `tint`:
        MULTIPLY keeps the texture's own brightness, OVERLAY only its grain
        (for a white wall from a darker plaster scan)."""
        mapping = self.nt.nodes.new("ShaderNodeMapping")
        mapping.inputs["Scale"].default_value = (1 / metres,) * 3
        self.nt.links.new(self.obj, mapping.inputs["Vector"])
        tex = self.nt.nodes.new("ShaderNodeTexImage")
        tex.image = bpy.data.images.load(os.path.join(TEXTURES, name + ".jpg"), check_existing=True)
        tex.projection, tex.projection_blend = "BOX", 0.25
        self.nt.links.new(mapping.outputs["Vector"], tex.inputs["Vector"])
        if blend == "OVERLAY":
            return self.mix(factor, tint, tex.outputs["Color"], "OVERLAY")
        return self.mix(factor, tex.outputs["Color"], tint, blend)

    def op(self, kind, a, b=None):
        m = self.nt.nodes.new("ShaderNodeMath")
        m.operation = kind
        for i, src in enumerate((a, b)):
            if src is not None:
                self._in(m.inputs[i], src)
        return m.outputs[0]

    def within(self, t, lo, hi):
        return self.op("MULTIPLY", self.op("GREATER_THAN", t, lo), self.op("LESS_THAN", t, hi))

    def mix(self, fac, a, b, blend="MIX"):
        m = self.nt.nodes.new("ShaderNodeMix")
        m.data_type, m.blend_type = "RGBA", blend
        self._in(m.inputs["Factor"], fac)
        self._in(m.inputs["A"], a)
        self._in(m.inputs["B"], b)
        return m.outputs["Result"]

    def color(self, src):
        self._in(self.bsdf.inputs["Base Color"], src)
        return self.mat


def plain(name, color):
    return Graph(name, color).mat


def textured(name, texture, metres, tint="#ffffff", blend="MULTIPLY", factor=1.0):
    g = Graph(name)
    return g.color(g.image(texture, metres, tint, blend, factor))


def striped(name, color, width=0.5, shade="#b8b8b8"):
    """Awning and parasol canvas: bands of the colour and a darker shade of it
    along the facade (white-based in the export, so the TV's owner tint keeps
    the stripes)."""
    g = Graph(name)
    band = g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", g.op("ADD", g.xyz.outputs["X"], g.xyz.outputs["Y"]), 2 * width)), 0.5)
    return g.color(g.mix(band, color, g.mix(1.0, color, shade, "MULTIPLY")))


def facade(name, wall="plastered_wall_04", wall_tint="#ffffff", ground="sandstone_blocks_05", ground_tint="#ffffff",
           glass="#34404b", frame="#c9bfae", shop="#2e2219", glow="#ffa552", bay=2.6, storey=3.0, base=3.2):
    """Textured wall with a framed window per bay and storey above a plinth
    storey whose wide shop windows glow warm in the evening light.

    u runs along the wall (x + y works on axis-aligned blocks: one of them is
    constant on each face), v counts storeys."""
    g = Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    u = g.op("FRACT", g.op("DIVIDE", g.op("ADD", x, y), bay))
    v = g.op("FRACT", g.op("DIVIDE", z, storey))
    upper = g.op("GREATER_THAN", z, base)
    framed = g.op("MULTIPLY", g.op("MULTIPLY", g.within(u, 0.25, 0.75), g.within(v, 0.2, 0.86)), upper)
    glazed = g.op("MULTIPLY", g.op("MULTIPLY", g.within(u, 0.29, 0.71), g.within(v, 0.25, 0.82)), upper)
    body = g.mix(upper, g.image(ground, 2.0, ground_tint), g.image(wall, 3.0, wall_tint, "OVERLAY", 0.55))
    surface = g.mix(glazed, g.mix(framed, body, frame), glass)
    # shop windows: dark warm glass with the rooms' light behind it, a soft glow rather than a lamp
    g.color(g.mix(window := shop_window(g, u, z, upper), surface, shop))
    g.nt.links.new(g.mix(window, "#000000", glow), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.1
    return g.mat


def timber_frame(name, timber="#3e2a1e", plaster_tint="#f7f2e8", paper="#f3e6c8", glow="#ffb35c", bay=1.8, storey=3.2):
    """Japanese post-and-beam walls: dark timber posts every bay and beams at
    each floor, white plaster infill upstairs with a small paper window, and
    lit paper screens (shoji) along the ground floor, glowing in the evening."""
    g = Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    u = g.op("FRACT", g.op("DIVIDE", g.op("ADD", x, y), bay))
    v = g.op("FRACT", g.op("DIVIDE", z, storey))
    timber_mask = g.op("MAXIMUM", g.op("LESS_THAN", u, 0.07), g.op("MAXIMUM", g.op("LESS_THAN", v, 0.05), g.op("GREATER_THAN", v, 0.95)))
    ground = g.op("LESS_THAN", z, storey)
    # shoji lattice: thin bars in a grid over the lit paper
    lattice = g.op("MAXIMUM", g.op("LESS_THAN", g.op("FRACT", g.op("MULTIPLY", u, 4.0)), 0.1),
                   g.op("LESS_THAN", g.op("FRACT", g.op("MULTIPLY", z, 2.4)), 0.1))
    upper_window = g.op("MULTIPLY", g.op("MULTIPLY", g.within(u, 0.3, 0.75), g.within(v, 0.35, 0.75)), g.op("SUBTRACT", 1.0, ground))
    lit = g.op("MULTIPLY", g.op("MAXIMUM", ground, upper_window), g.op("SUBTRACT", 1.0, lattice))
    infill = g.mix(lit, g.image("plastered_wall_04", 3.0, plaster_tint, "OVERLAY", 0.5), paper)
    infill = g.mix(g.op("MULTIPLY", lattice, g.op("MAXIMUM", ground, upper_window)), infill, timber)
    g.color(g.mix(timber_mask, infill, timber))
    glowing = g.op("MULTIPLY", lit, g.op("SUBTRACT", 1.0, timber_mask))
    g.nt.links.new(g.mix(glowing, "#000000", glow), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = 0.35
    return g.mat


def shop_window(g, u, z, upper):
    """Wide plinth-storey windows, split by a mullion in each bay."""
    pane = g.op("MULTIPLY", g.op("ADD", g.within(u, 0.14, 0.47), g.within(u, 0.53, 0.86)), g.within(z, 0.55, 2.5))
    return g.op("MULTIPLY", pane, g.op("SUBTRACT", 1.0, upper))
