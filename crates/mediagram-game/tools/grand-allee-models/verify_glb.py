"""Renders an exported hotel GLB the way the TV will draw it: unlit (the light
is in the atlas), the `owner_tint` material multiplied by an owner colour, no
extra view transform (AgX is already in the atlas).

blender -b --factory-startup --python-exit-code 1 -P verify_glb.py -- \
    --glb <hotel>.glb --out <png> [--owner "#ef476f"] [--plot 20,16] [--stages n]"""

import argparse
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402
import materials as m  # noqa: E402

argv = sys.argv[sys.argv.index("--") + 1:]
ap = argparse.ArgumentParser()
ap.add_argument("--glb", required=True)
ap.add_argument("--out", required=True)
ap.add_argument("--owner", default="#ef476f")
ap.add_argument("--plot", default="20,16")
ap.add_argument("--stages", type=int, default=0, help="show stages 1..n only (0: all)")
args = ap.parse_args(argv)
w, d = (float(v) for v in args.plot.split(","))

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=args.glb)
for o in bpy.data.objects:  # stage_<n>_body / stage_<n>_tint beyond the stages asked for
    if args.stages and o.type == "MESH" and o.name.startswith("stage_") and int(o.name.split("_")[1]) > args.stages:
        o.hide_render = True
tint = bpy.data.materials.get("owner_tint")
if tint:  # the TV's owner tint: atlas colour × owner colour
    nt = tint.node_tree
    tex = next(n for n in nt.nodes if n.type == "TEX_IMAGE")
    shader = next(n for n in nt.nodes if n.type in ("BACKGROUND", "EMISSION", "BSDF_PRINCIPLED"))
    mul = nt.nodes.new("ShaderNodeMix")
    mul.data_type, mul.blend_type = "RGBA", "MULTIPLY"
    mul.inputs["Factor"].default_value = 1.0
    mul.inputs["B"].default_value = (*m.hex_rgb(args.owner), 1)
    nt.links.new(tex.outputs["Color"], mul.inputs["A"])
    nt.links.new(mul.outputs["Result"], shader.inputs[0])

sc = bpy.context.scene
sc.render.engine = "CYCLES"
sc.cycles.device, sc.cycles.samples = "CPU", 16
sc.render.resolution_x, sc.render.resolution_y = 1280, 720
sc.view_settings.view_transform = "Standard"
world = bpy.data.worlds.new("plain")
next(n for n in world.node_tree.nodes if n.type == "BACKGROUND").inputs["Color"].default_value = (*m.hex_rgb("#9db4d6"), 1)
sc.world = world
k = max(w / 20, d / 16)
sc.camera = build.camera("tv", (-15 * k, -36 * k, 21 * k), (0, 1, 4), 45)
sc.render.filepath = args.out
bpy.ops.render.render(write_still=True)
print("@@JSON@@" + '{"render": "%s", "materials": %d}' % (args.out, len(bpy.data.materials)))
