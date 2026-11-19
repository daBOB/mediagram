"""How the Kopfnuss stages' materials are made: Poly Haven scans and procedural shaders, all mapped in world space.

World-space mapping means no object needs UVs to look right; the only UV map is the bake atlas.
"""
import os

import bpy

from kn_common import rgba

CACHE = os.path.expanduser("~/.cache/kopfnuss-arena")
ANSWERS = ("#2c6bed", "#f2862e", "#f6c731", "#e5487f")
RAINBOW = ("#7746bb", "#28b8ca", "#50b878", "#9ccc37", "#ffdb42", "#ffb52e", "#f4763f", "#e64572", "#bb389d", "#db334d")


class Shader:
    """A material with its Principled BSDF and short ways to add and wire nodes."""

    def __init__(self, name):
        self.mat = bpy.data.materials.new("KN_" + name)
        self.tree = self.mat.node_tree
        self.bsdf = next(n for n in self.tree.nodes if n.type == "BSDF_PRINCIPLED")
        self.out = next(n for n in self.tree.nodes if n.type == "OUTPUT_MATERIAL")
        self.pos = self.node("ShaderNodeNewGeometry").outputs["Position"]

    def node(self, kind, **props):
        node = self.tree.nodes.new(kind)
        for key, value in props.items():
            setattr(node, key, value)
        return node

    def link(self, out, socket):
        self.tree.links.new(out, self.bsdf.inputs[socket] if isinstance(socket, str) else socket)

    def set(self, **values):
        for key, value in values.items():
            self.bsdf.inputs[key.replace("_", " ")].default_value = value
        return self

    def mapped(self, scale):
        """World position scaled, as texture coordinates."""
        mapping = self.node("ShaderNodeMapping")
        mapping.inputs["Scale"].default_value = (scale, scale, scale)
        self.link(self.pos, mapping.inputs["Vector"])
        return mapping.outputs["Vector"]

    def noise(self, scale, detail=3.0):
        node = self.node("ShaderNodeTexNoise")
        node.inputs["Scale"].default_value = scale
        node.inputs["Detail"].default_value = detail
        self.link(self.pos, node.inputs["Vector"])
        return node.outputs["Factor"]

    def ranged(self, value, low, high):
        node = self.node("ShaderNodeMapRange")
        node.inputs["To Min"].default_value = low
        node.inputs["To Max"].default_value = high
        self.link(value, node.inputs["Value"])
        return node.outputs["Result"]

    def bump(self, height, strength, distance=.01):
        node = self.node("ShaderNodeBump")
        node.inputs["Strength"].default_value = strength
        node.inputs["Distance"].default_value = distance
        self.link(height, node.inputs["Height"])
        self.link(node.outputs["Normal"], "Normal")

    def image(self, asset, name, vector, data=False):
        node = self.node("ShaderNodeTexImage", projection="BOX", projection_blend=.25)
        node.image = bpy.data.images.load(os.path.join(CACHE, f"{asset}_{name}.jpg"), check_existing=True)
        if data:
            node.image.colorspace_settings.name = "Non-Color"
        self.link(vector, node.inputs["Vector"])
        return node.outputs["Color"]


def scan(name, asset, tile_m, tint=None, rough=(0.0, 1.0), **values):
    """A Poly Haven scan: colour and roughness maps, the roughness map doubling as the bump height."""
    s = Shader(name).set(**values)
    vector = s.mapped(1 / tile_m)
    colour = s.image(asset, "Diffuse", vector)
    if tint:
        mix = s.node("ShaderNodeMix", data_type="RGBA", blend_type="MULTIPLY")
        mix.inputs["Factor"].default_value = 1
        mix.inputs["B"].default_value = rgba(tint) if isinstance(tint, str) else tint
        s.link(colour, mix.inputs["A"])
        colour = mix.outputs["Result"]
    s.link(colour, "Base Color")
    height = s.image(asset, "Rough", vector, data=True)
    s.link(s.ranged(height, *rough), "Roughness")
    s.bump(height, .25, .004)
    return s.mat


def flat(name, color, **values):
    return Shader(name).set(Base_Color=rgba(color), **values).mat


def lacquer(name, color, roughness=.3, coat=.6):
    """Painted and clear-coated: smudges in the roughness keep its reflections from looking computed."""
    s = Shader(name).set(Base_Color=rgba(color), Coat_Weight=coat, Coat_Roughness=.06)
    s.link(s.ranged(s.noise(2.2), roughness * .6, roughness * 1.4), "Roughness")
    return s.mat


def metal(name, color, roughness=.2):
    s = Shader(name).set(Base_Color=rgba(color), Metallic=1.0)
    s.link(s.ranged(s.noise(6.0, 5.0), roughness * .55, roughness * 1.5), "Roughness")
    return s.mat


def glitter(name, color, flake=42.0):
    """Metal flakes, each tilted its own way, so single flakes catch the light: real sparkle in Cycles."""
    s = Shader(name).set(Base_Color=rgba(color), Metallic=1.0, Roughness=.16)
    cells = s.node("ShaderNodeTexVoronoi")
    cells.inputs["Scale"].default_value = flake
    s.link(s.pos, cells.inputs["Vector"])
    tilt = s.node("ShaderNodeVectorMath", operation="SUBTRACT")
    tilt.inputs[1].default_value = (.5, .5, .5)
    s.link(cells.outputs["Color"], tilt.inputs[0])
    scaled = s.node("ShaderNodeVectorMath", operation="SCALE")
    scaled.inputs["Scale"].default_value = .55
    s.link(tilt.outputs["Vector"], scaled.inputs[0])
    bent = s.node("ShaderNodeVectorMath", operation="ADD")
    s.link(s.node("ShaderNodeNewGeometry").outputs["Normal"], bent.inputs[0])
    s.link(scaled.outputs["Vector"], bent.inputs[1])
    unit = s.node("ShaderNodeVectorMath", operation="NORMALIZE")
    s.link(bent.outputs["Vector"], unit.inputs[0])
    s.link(unit.outputs["Vector"], "Normal")
    return s.mat


def neon(name, color, strength):
    s = Shader(name).set(Base_Color=rgba(color), Emission_Color=rgba(color), Emission_Strength=strength, Roughness=.4)
    return s.mat


def candy(name, color):
    """Hard candy with a sugar-dusted top: faces that look up are paler."""
    s = Shader(name).set(Roughness=.22, Coat_Weight=1.0, Coat_Roughness=.04)
    up = s.node("ShaderNodeSeparateXYZ")
    s.link(s.node("ShaderNodeNewGeometry").outputs["Normal"], up.inputs[0])
    dusted = s.node("ShaderNodeMapRange")
    dusted.inputs["From Min"].default_value = .7
    dusted.inputs["From Max"].default_value = .95
    dusted.inputs["To Max"].default_value = .14
    s.link(up.outputs["Z"], dusted.inputs["Value"])
    mix = s.node("ShaderNodeMix", data_type="RGBA")
    mix.inputs["A"].default_value = rgba(color)
    mix.inputs["B"].default_value = (1, .96, .9, 1)
    s.link(dusted.outputs["Result"], mix.inputs["Factor"])
    s.link(mix.outputs["Result"], "Base Color")
    return s.mat


def sky(name, stops, low=0.0, high=16.0):
    """A lit cyclorama: colour by height, as if washed with light from the floor up."""
    s = Shader(name)
    height = s.node("ShaderNodeSeparateXYZ")
    s.link(s.pos, height.inputs[0])
    span = s.node("ShaderNodeMapRange")
    span.inputs["From Min"].default_value = low
    span.inputs["From Max"].default_value = high
    s.link(height.outputs["Z"], span.inputs["Value"])
    ramp = s.node("ShaderNodeValToRGB")
    elements = ramp.color_ramp.elements
    while len(elements) < len(stops):
        elements.new(.5)
    for element, (at, color) in zip(elements, stops):
        element.position = at
        element.color = rgba(color)
    s.link(span.outputs["Result"], ramp.inputs["Factor"])
    s.link(ramp.outputs["Color"], "Emission Color")
    s.set(Emission_Strength=1.0, Base_Color=(0, 0, 0, 1), Roughness=1.0)
    return s.mat

