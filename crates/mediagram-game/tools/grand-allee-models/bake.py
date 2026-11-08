"""Bakes one Grand Allee hotel for the TV and exports it as a GLB.

blender -b --factory-startup --python-exit-code 1 -P bake.py -- \
    --hotel kirschbluete --out <dir> [--res 1024] [--samples 64]

The hotel is built in the same lit scene as the previews (`build.make`) but
with a white owner colour, so its `tint_*` parts bake as lit white for the TV
to multiply by the owner's colour. One UV atlas is packed over all its parts,
the golden-hour light (glowing windows included) is baked into it stage by
stage (each with only the stages up to it present) and saved
through the scene's AgX view as sRGB, so the TV shows what the previews show.
Every part then gets an unlit material (`hotel`, or `owner_tint` for the
tinted parts), the parts are joined per stage into `stage_<n>_body` and
`stage_<n>_tint` under the `stage_<n>` empties, and <out>/<hotel>.glb is
written with the atlas embedded (WebP)."""

import argparse
import json
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402

WHITE = "#ffffff"


def select(objs, active):
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = active


def pack_atlas(objs):
    """One shared UV space for every part: smart project, then pack together."""
    for o in objs:
        if not o.data.uv_layers:
            o.data.uv_layers.new(name="atlas")
    select(objs, objs[0])
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.006)
    bpy.ops.uv.pack_islands(margin=0.006)
    bpy.ops.object.mode_set(mode="OBJECT")


def bake_light(objs, res, samples, stages=None):
    """Cycles' combined pass (lit colour + emission) into one float image.

    `stages`: the parts per stage, in build order. Each stage is then baked with
    only itself and the stages before it present: the TV shows a hotel stage by
    stage, so an early stage must not carry the shadow and occlusion of later
    ones (a black path where a later wing will stand). Ground a later stage
    covers is hidden under it by then anyway."""
    img = bpy.data.images.new("atlas", res, res, alpha=False, float_buffer=True)
    added = []
    for mat in {s.material for o in objs for s in o.material_slots if s.material}:
        node = mat.node_tree.nodes.new("ShaderNodeTexImage")
        node.image = img
        mat.node_tree.nodes.active = node  # Cycles bakes into each material's active image node
        added.append((mat, node))
    sc = bpy.context.scene
    sc.cycles.samples = samples
    sc.render.bake.margin = 4
    groups = [g for g in (stages or [objs]) if g]
    for i, group in enumerate(groups):
        later = [o for g in groups[i + 1:] for o in g]
        for o in later:
            o.hide_render = True  # out of the bake's scene: no shadow, no occlusion
        sc.render.bake.use_clear = i == 0  # later stages add their islands to the same atlas
        select(group, group[0])
        result = bpy.ops.object.bake(type="COMBINED")
        for o in later:
            o.hide_render = False
        if result != {"FINISHED"}:
            raise RuntimeError(f"bake failed: {result}")
    for mat, node in added:
        mat.node_tree.nodes.remove(node)
    return img


def unlit(name, image):
    """Background shader on the atlas: Blender's glTF exporter writes KHR_materials_unlit."""
    mat = bpy.data.materials.new(name)
    nt = mat.node_tree
    nt.nodes.clear()
    out, bg, tex = (nt.nodes.new(t) for t in ("ShaderNodeOutputMaterial", "ShaderNodeBackground", "ShaderNodeTexImage"))
    tex.image = image
    nt.links.new(tex.outputs["Color"], bg.inputs["Color"])
    nt.links.new(bg.outputs["Background"], out.inputs["Surface"])
    return mat


def join_stage(stage, index):
    """The stage's parts become one body mesh and one tint mesh (two draws per stage on the TV)."""
    made = []
    for kind, wanted in (("body", False), ("tint", True)):
        parts = [c for c in stage.children if c.type == "MESH" and c.name.startswith("tint_") == wanted]
        if not parts:
            continue
        select(parts, parts[0])
        bpy.ops.object.join()
        joined = bpy.context.view_layer.objects.active
        joined.name = joined.data.name = f"stage_{index}_{kind}"
        made.append(joined)
    return made


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--hotel", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--res", type=int, default=1024)
    ap.add_argument("--samples", type=int, default=64)
    args = ap.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)

    stages = build.make(args.hotel, WHITE, args.samples)
    parts = [c for s in stages for c in s.children if c.type == "MESH"]
    pack_atlas(parts)
    baked = bake_light(parts, args.res, args.samples, [[c for c in s.children if c.type == "MESH"] for s in stages])
    atlas_path = os.path.join(args.out, f"{args.hotel}-atlas.png")
    sc = bpy.context.scene
    sc.render.image_settings.file_format, sc.render.image_settings.color_mode = "PNG", "RGB"
    baked.save_render(filepath=atlas_path, scene=sc)  # through the scene's AgX view, as sRGB

    atlas = bpy.data.images.load(atlas_path)
    body, tint = unlit("hotel", atlas), unlit("owner_tint", atlas)
    for o in parts:
        o.data.materials.clear()
        o.data.materials.append(tint if o.name.startswith("tint_") else body)
    meshes = [m for i, s in enumerate(stages, 1) for m in join_stage(s, i)]

    glb = os.path.join(args.out, f"{args.hotel}.glb")
    select(stages + meshes, stages[0])
    bpy.ops.export_scene.gltf(filepath=glb, export_format="GLB", use_selection=True, export_image_format="WEBP",
                              export_image_quality=90, export_yup=True)
    tris = {m.name: sum(len(p.vertices) - 2 for p in m.data.polygons) for m in meshes}
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(args.out, f"{args.hotel}-baked.blend"))
    print("@@JSON@@" + json.dumps({"hotel": args.hotel, "glb": glb, "glb_bytes": os.path.getsize(glb), "atlas": atlas_path,
                                   "tris": sum(tris.values()), "meshes": tris}))


if __name__ == "__main__":
    main()
