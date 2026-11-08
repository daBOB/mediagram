"""Grand Allee's board, built headless in the hotels' evening light: the ground
as the top-down bake sees it (`board_ground`), kerbs and plot edging, and the
3D parts (`board_town`: Bank, Rathaus, bridges; `board_trees`).

blender -b --factory-startup --python-exit-code 1 -P board.py -- --out <dir> [--preview]

Saves <out>/board.blend and prints a JSON line with the parts' triangles;
--preview also renders the lit scene from the TV's angle (before any bake)."""

import argparse
import json
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import board_ground as ground  # noqa: E402
import board_town as town  # noqa: E402
import board_trees as trees  # noqa: E402
import build  # noqa: E402
from board_kit import TV_PITCH, Layout  # noqa: E402


def make(samples):
    """A fresh file with the lit board; returns (ground parts, 3D parts, layout)."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine, sc.cycles.device, sc.cycles.samples = "CYCLES", "CPU", samples
    sc.cycles.use_denoising = True
    sc.view_settings.view_transform = "AgX"
    build.light()
    L = Layout()
    ground.terrain(L)
    ground.water()
    ground.paving(L)
    ground.plot_lawns(L)
    paths = ground.park_paths(L)
    ground.road(L)
    kerbs = ground.ground_parts(L)
    parts = town.bank() + town.rathaus() + town.bridges(L) + trees.grow(trees.spots(L, paths))
    return kerbs, parts, L


def triangles(objs):
    return sum(len(poly.vertices) - 2 for o in objs for poly in o.data.polygons)


def tv_camera(dist=132.0, look=(0, 2, 0), lens=32):
    """The TV's view: 55 degrees down from the south, the whole board's width."""
    a = TV_PITCH
    return build.camera("tv", (look[0], look[1] - dist * math.cos(a), dist * math.sin(a)), look, lens)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--samples", type=int, default=32)
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    kerbs, parts, _ = make(args.samples)
    if args.preview:
        sc = bpy.context.scene
        sc.render.resolution_x, sc.render.resolution_y = 1600, 900
        sc.camera = tv_camera()
        build.render(os.path.join(args.out, "board-preview.png"))
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(args.out, "board.blend"))
    print("@@JSON@@" + json.dumps({"ground_parts_tris": triangles(kerbs), "parts_tris": triangles(parts),
                                   "parts": {o.name: triangles([o]) for o in parts}}))


if __name__ == "__main__":
    main()
