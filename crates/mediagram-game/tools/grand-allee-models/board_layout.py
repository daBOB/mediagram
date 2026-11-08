"""Renders Grand Allee's board layout (`board_path.py`) with the baked hotels in
place: a top view and the TV view. Fields, grass and water are flat stand-ins
until the board itself is built and baked.

blender -b --factory-startup --python-exit-code 1 -P board_layout.py -- --out <dir> [--pitch 55]"""

import argparse
import json
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import board_path as bp  # noqa: E402
import build  # noqa: E402
import materials as m  # noqa: E402
import parts as p  # noqa: E402

# The TV camera looks 55 degrees down: at a flatter angle the near row of hotels hid
# the near road (user's choice, 2026-11-08, `reports/board-layout/`).
CAMERA_PITCH = 55.0
HOTELS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "client", "public", "grand-allee")
KINDS = {"B": "#e9c46a", "R": "#e76f51", "P": "#bfb7a8", "S": "#8d99ae", "E": "#90be6d", "G": "#4ea8de", ".": "#d8d2c4"}
BOARD_FIELDS = "B.GEP.S..P.SP.S.P.S.R.GEP.S..P.SP.S.P.S."  # as in grand_allee/board.rs
WATER = "#3b7fb8"


def flat(name, color):
    """An unlit colour, the way the TV draws baked surfaces."""
    mat = m.plain(name, color)
    nt = mat.node_tree
    nt.nodes.clear()
    out, bg = nt.nodes.new("ShaderNodeOutputMaterial"), nt.nodes.new("ShaderNodeBackground")
    bg.inputs["Color"].default_value = (*m.hex_rgb(color), 1)
    nt.links.new(bg.outputs[0], out.inputs[0])
    return mat


def polygon(name, pts, z, mat):
    def build_face(bm):
        bm.faces.new([bm.verts.new((x, y, z)) for x, y in pts])

    return p.mesh_object(name, build_face, mat)


def river(data, mat):
    """The river as a ribbon of flat quads with round joints, and the pond."""
    half, line = data["river_width"] / 2, data["river"]
    for i, (a, b) in enumerate(zip(line, line[1:])):
        dx, dy = b[0] - a[0], b[1] - a[1]
        n = math.hypot(dx, dy)
        nx, ny = -dy / n * half, dx / n * half
        polygon(f"river_{i}", [(a[0] + nx, a[1] + ny), (a[0] - nx, a[1] - ny), (b[0] - nx, b[1] - ny), (b[0] + nx, b[1] + ny)], 0.01, mat)
    for i, (x, y) in enumerate(line[1:-1]):
        p.cylinder(f"river_joint_{i}", x, y, 0.0, 0.01, half, mat, segments=16)
    x, y, r = data["pond"]
    p.cylinder("pond", x, y, 0.0, 0.012, r, mat, segments=32)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--pitch", type=float, default=CAMERA_PITCH, help="TV camera's angle below the horizon, degrees")
    args = ap.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    data, bad = bp.layout()
    assert not bad, bad
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine, sc.cycles.device, sc.cycles.samples = "CYCLES", "CPU", 16
    sc.render.resolution_x, sc.render.resolution_y = 1600, 900
    sc.view_settings.view_transform = "Standard"  # the hotels' atlases already carry AgX
    world = bpy.data.worlds.new("plain")
    next(n for n in world.node_tree.nodes if n.type == "BACKGROUND").inputs["Color"].default_value = (*m.hex_rgb("#9db4d6"), 1)
    sc.world = world
    w, h = data["board"]
    p.box("grass", (-w / 2, -h / 2, -0.2), (w / 2, h / 2, 0), flat("grass", "#6d8f45"))
    water = flat("water", WATER)
    p.box("sea", (-w / 2, data["sea_y"], -0.2), (w / 2, h / 2, 0.01), water)
    river(data, water)
    for f in data["fields"]:
        cx, cy = f["x"], f["y"]
        inset = [(cx + (x - cx) * 0.94, cy + (y - cy) * 0.94) for x, y in f["corners"]]  # a seam between fields
        polygon(f"field_{f['id']}", inset, 0.08, flat(f"f{f['id']}", KINDS[BOARD_FIELDS[f["id"]]]))
    place_hotels(data["plots"])
    json.dump(data, open(os.path.join(args.out, "layout.json"), "w"), indent=1)
    top = build.camera("top", (0, 0, 160), (0, 0.01, 0), 30)
    dist = 132.0  # frames the 128 m board's width at this lens
    a = math.radians(args.pitch)
    tv = build.camera("tv", (0, 2 - dist * math.cos(a), dist * math.sin(a)), (0, 2, 0), 32)
    for cam, name in ((top, "top"), (tv, "tv")):
        sc.camera = cam
        sc.render.filepath = os.path.join(args.out, f"board-layout-{args.pitch:g}-{name}.png")
        bpy.ops.render.render(write_still=True)
    print("@@JSON@@" + json.dumps({"fields": len(data["fields"]), "plots": len(data["plots"])}))


def place_hotels(plots):
    """Imports each plot's baked hotel GLB at its place, turn and scale."""
    for plot in plots:
        before = set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=os.path.join(HOTELS, plot["name"] + ".glb"))
        for o in set(bpy.data.objects) - before:
            if o.parent is None:
                o.rotation_mode = "XYZ"  # the glTF importer leaves quaternions, which would ignore the angle
                o.location, o.rotation_euler, o.scale = (plot["x"], plot["y"], 0), (0, 0, plot["rotation"]), (plot["scale"],) * 3


if __name__ == "__main__":
    main()
