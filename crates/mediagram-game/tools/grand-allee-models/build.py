"""Builds one Grand Allee hotel headless and renders its stages.

blender -b --factory-startup --python-exit-code 1 -P build.py -- \
    --hotel seeblick --out <dir> [--owner "#ef476f"] [--samples 48]

Writes <out>/<hotel>.blend, <out>/<hotel>-stage-<n>.png (stages built up one
by one, seen from the TV board's angle) and prints a JSON line with
triangles per stage."""

import argparse
import importlib
import json
import math
import os
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import materials as m  # noqa: E402
import parts as p  # noqa: E402


def scene_setup(samples, plot):
    """`plot` = (width along the road, depth): outer plots 20-24 x 16 m, park plots 32 x 14 m."""
    w, d = plot
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = samples
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = 1280, 720
    sc.view_settings.view_transform = "AgX"
    # golden hour: a low warm sun from the left front (19 degrees up), cool sky light
    sun = bpy.data.lights.new("sun", "SUN")
    sun.energy, sun.angle = 5.5, math.radians(2.5)
    sun.color = m.hex_rgb("#ffbe7d")
    sun_obj = p.link(bpy.data.objects.new("sun", sun))
    sun_obj.rotation_euler = (math.radians(71), 0, math.radians(-60))
    world = bpy.data.worlds.new("sky")
    bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
    bg.inputs["Color"].default_value = (*m.hex_rgb("#9db4d6"), 1)
    bg.inputs["Strength"].default_value = 0.55
    sc.world = world
    # context: the plot's lawn and the road with its granite fields
    p.box("ctx_lawn", (-w / 2, -d / 2, -0.1), (w / 2, d / 2, 0), m.textured("lawn", "sparse_grass", 4.0, "#5f8a3c", "OVERLAY", 0.45))
    p.box("ctx_road", (-w / 2 - 4, -d / 2 - 5, -0.1), (w / 2 + 4, -d / 2, -0.02), m.textured("road", "asphalt_02", 4.0))
    k = max(w / 20, d / 16)  # the cameras stand back for bigger plots
    sc.camera = camera("tv", (-15 * k, -36 * k, 21 * k), (0, 1, 4), 45)
    camera("close", (-9 * k, -19 * k, 7 * k), (0, 1, 6.5), 35)


def camera(name, at, look, lens):
    cam = bpy.data.cameras.new(name)
    cam.lens = lens
    obj = p.link(bpy.data.objects.new(name, cam))
    obj.location = Vector(at)
    obj.rotation_euler = (Vector(look) - obj.location).to_track_quat("-Z", "Y").to_euler()
    return obj


def render(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return path


def triangles(obj):
    return sum(len(poly.vertices) - 2 for poly in obj.data.polygons) if obj.type == "MESH" else 0


def make(hotel_name, owner, samples):
    """A fresh file with the lit scene and the hotel built in it; returns the stage empties."""
    bpy.ops.wm.read_factory_settings(use_empty=True)  # headless only: a fresh, empty file
    hotel = importlib.import_module(hotel_name)
    scene_setup(samples, getattr(hotel, "PLOT", (20, 16)))
    return hotel.build(owner)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--hotel", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--owner", default="#ef476f")
    ap.add_argument("--samples", type=int, default=48)
    ap.add_argument("--no-render", action="store_true", help="only build and save the .blend (fast checks)")
    args = ap.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)

    stages = make(args.hotel, args.owner, args.samples)
    tris = [sum(triangles(c) for c in s.children) for s in stages]

    renders = []
    for shown in range(1, 0 if args.no_render else len(stages) + 1):
        for i, s in enumerate(stages):
            for c in s.children:
                c.hide_render = i >= shown
        renders.append(render(os.path.join(args.out, f"{args.hotel}-stage-{shown}.png")))
    if not args.no_render:
        # the finished hotel up close, as the TV camera sees it when it leans in
        bpy.context.scene.camera = bpy.data.objects["close"]
        renders.append(render(os.path.join(args.out, f"{args.hotel}-close.png")))
        bpy.context.scene.camera = bpy.data.objects["tv"]
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(args.out, f"{args.hotel}.blend"))
    print("@@JSON@@" + json.dumps({"hotel": args.hotel, "tris_per_stage": tris, "tris": sum(tris), "renders": renders}))


if __name__ == "__main__":
    main()
