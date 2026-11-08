"""Bakes Grand Allee's board for the TV and exports board.glb.

blender -b --factory-startup --python-exit-code 1 -P board_bake.py -- --out <dir> [--samples 64]

Two atlases, both lit by the hotels' evening light and saved through AgX:
- the ground, 2048 x 1536 (6.25 cm a pixel): an orthographic render straight
  down at the board with the 3D parts invisible to the camera but casting
  their shadows. On matte surfaces that is the same as a top-down bake, and
  the render's denoiser keeps the setts and icons clean. It textures one flat
  quad over the whole board plus the kerbs and plot edging (UVs = x, y), so
  the river's channel, banks, setts and lettering are all texture: one draw.
- the 3D parts (Bank, Rathaus, bridge parapets, trees), 1024^2, baked like a hotel.
Both meshes get unlit materials (KHR_materials_unlit); the GLB holds
`board_ground` and `board_parts`, atlases embedded as WebP."""

import argparse
import json
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake  # noqa: E402
import board  # noqa: E402
import board_path as bp  # noqa: E402

GROUND_RES = (2048, 1536)


def top_render(path, samples, hidden):
    sc = bpy.context.scene
    for o in hidden:
        o.visible_camera = False
    cam = bpy.data.cameras.new("top")
    cam.type, cam.ortho_scale, cam.clip_end = "ORTHO", bp.BOARD[0], 100
    obj = bpy.data.objects.new("top", cam)
    sc.collection.objects.link(obj)
    obj.location = (0, 0, 40)
    sc.camera = obj
    sc.render.resolution_x, sc.render.resolution_y = GROUND_RES
    sc.cycles.samples = samples
    sc.render.image_settings.file_format, sc.render.image_settings.color_mode = "PNG", "RGB"
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    for o in hidden:
        o.visible_camera = True
    return path


def ground_mesh(kerbs, image):
    """The board quad joined with kerbs and edging; board metres become atlas UVs."""
    w, h = bp.BOARD
    me = bpy.data.meshes.new("board_ground")
    me.from_pydata([(-w / 2, -h / 2, 0), (w / 2, -h / 2, 0), (w / 2, h / 2, 0), (-w / 2, h / 2, 0)], [], [(0, 1, 2, 3)])
    uv = me.uv_layers.new(name="atlas")
    for loop in me.loops:
        uv.data[loop.index].uv = me.vertices[loop.vertex_index].co.xy
    plane = bpy.data.objects.new("board_ground", me)
    bpy.context.scene.collection.objects.link(plane)
    for o in [plane] + kerbs:
        for d in o.data.uv_layers["atlas"].data:
            d.uv = (d.uv[0] / w + 0.5, d.uv[1] / h + 0.5)
    bake.select([plane] + kerbs, plane)
    bpy.ops.object.join()
    plane.data.materials.clear()
    plane.data.materials.append(bake.unlit("board_ground", image))
    plane.data.name = "board_ground"
    return plane


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--samples", type=int, default=64)
    args = ap.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    kerbs, parts, _ = board.make(args.samples)
    sc = bpy.context.scene

    ground_png = top_render(os.path.join(args.out, "board-ground-atlas.png"), args.samples, parts)
    bake.pack_atlas(parts)
    baked = bake.bake_light(parts, 1024, args.samples)
    parts_png = os.path.join(args.out, "board-parts-atlas.png")
    baked.save_render(filepath=parts_png, scene=sc)  # through the scene's AgX view, as sRGB

    body = bake.unlit("board_parts", bpy.data.images.load(parts_png))
    for o in parts:
        o.data.materials.clear()
        o.data.materials.append(body)
    first = next(o for o in parts if o.parent is None)  # joined into an unmoved, unscaled object
    bake.select(parts, first)
    bpy.ops.object.join()
    first.name = first.data.name = "board_parts"
    ground = ground_mesh(kerbs, bpy.data.images.load(ground_png))

    glb = os.path.join(args.out, "board.glb")
    bake.select([ground, first], ground)
    bpy.ops.export_scene.gltf(filepath=glb, export_format="GLB", use_selection=True, export_image_format="WEBP",
                              export_image_quality=90, export_yup=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(args.out, "board-baked.blend"))
    tris = {o.name: board.triangles([o]) for o in (ground, first)}
    print("@@JSON@@" + json.dumps({"glb": glb, "glb_bytes": os.path.getsize(glb), "tris": sum(tris.values()), "meshes": tris}))


if __name__ == "__main__":
    main()
