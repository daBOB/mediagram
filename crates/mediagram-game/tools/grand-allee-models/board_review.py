"""Renders the exported board, hotels and dice the way the TV draws them
(unlit, Standard view: the light is in the atlases) and checks the GLBs:
every material unlit (KHR_materials_unlit), triangles per mesh.

blender -b --factory-startup --python-exit-code 1 -P board_review.py -- --glbs <dir> --out <dir>

Renders: the TV view at 55 degrees with all ten hotels placed from board_path
(fully built), the top view, close-ups of fields 0-3, fields 20-23, the
bridge at field 21 and the waist where two park plots' lands meet on a bend,
and the two dice turned to show all six faces."""

import argparse
import json
import math
import os
import struct
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import board  # noqa: E402
import board_layout  # noqa: E402
import build  # noqa: E402
import materials as m  # noqa: E402
from board_kit import Layout  # noqa: E402


def unlit(glb):
    """Whether every material in the GLB carries KHR_materials_unlit (read from its JSON chunk)."""
    with open(glb, "rb") as f:
        f.read(12)
        length, _ = struct.unpack("<II", f.read(8))
        doc = json.loads(f.read(length))
    return all("KHR_materials_unlit" in mat.get("extensions", {}) for mat in doc["materials"])


def load(glb, at=(0, 0, 0), turn=(0, 0, 0)):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=glb)
    new = set(bpy.data.objects) - before
    for o in new:
        if o.parent is None:
            o.rotation_mode = "XYZ"
            o.location, o.rotation_euler = at, turn
    return [o for o in new if o.type == "MESH"]


def close(name, x, y, dist, lens=35):
    return board.tv_camera(dist, (x, y, 0), lens)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    ap = argparse.ArgumentParser()
    ap.add_argument("--glbs", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--hotels", default=board_layout.HOTELS, help="folder of the hotel GLBs")
    args = ap.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine, sc.cycles.device, sc.cycles.samples = "CYCLES", "CPU", 16
    sc.render.resolution_x, sc.render.resolution_y = 1600, 900
    sc.view_settings.view_transform = "Standard"
    world = bpy.data.worlds.new("plain")
    next(n for n in world.node_tree.nodes if n.type == "BACKGROUND").inputs["Color"].default_value = (*m.hex_rgb("#9db4d6"), 1)
    sc.world = world
    report = {}
    for name in ("board", "die-number", "die-permit"):
        glb = os.path.join(args.glbs, name + ".glb")
        report[name] = {"unlit": unlit(glb), "bytes": os.path.getsize(glb)}
    meshes = load(os.path.join(args.glbs, "board.glb"))
    report["board"]["tris"] = {o.name: board.triangles([o]) for o in meshes}
    L = Layout()
    board_layout.place_hotels(L.plots, args.hotels)
    f = L.fields
    shots = {"tv": board.tv_camera(), "top": build.camera("top", (0, 0, 160), (0, 0.01, 0), 30),
             "fields-0-3": close("c0", sum(f[i]["x"] for i in range(4)) / 4, sum(f[i]["y"] for i in range(4)) / 4, 34),
             "fields-20-23": close("c1", sum(f[i]["x"] for i in range(20, 24)) / 4, sum(f[i]["y"] for i in range(20, 24)) / 4, 34),
             "bridge-21": close("c2", f[21]["x"], f[21]["y"], 15),
             "plots-meet-11-12": close("c3", (f[11]["x"] + f[12]["x"]) / 2, (f[11]["y"] + f[12]["y"]) / 2 + 2, 30)}
    for i, kind in enumerate(("number", "permit")):  # two of each die, the second turned to show the other three faces
        for j, turn in enumerate(((0, 0, 0), (math.pi, 0, math.pi / 2))):
            die = load(os.path.join(args.glbs, f"die-{kind}.glb"), (300 + 1.8 * j, 1.8 * i, 0.6), turn)
            report[f"die-{kind}"]["tris"] = board.triangles(die)
    shots["dice"] = build.camera("dice", (300.9 + 2.2, 0.9 - 5.5, 5.0), (300.9, 0.9, 0.4), 50)
    for name, cam in shots.items():
        sc.camera = cam
        build.render(os.path.join(args.out, f"board-{name}.png"))
    print("@@JSON@@" + json.dumps(report))


if __name__ == "__main__":
    main()
