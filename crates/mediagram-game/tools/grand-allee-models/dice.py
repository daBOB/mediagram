"""Grand Allee's two dice for the TV: an ivory number die with dark pips and an
oak permit die with burnt-in signs (tick: approved, cross: rejected, a parcel:
free, x2: double).

blender -b --factory-startup --python-exit-code 1 -P dice.py -- --out <dir>

Each is a rounded cube; its faces are painted by a selected-to-active bake from
flat decals on a copy, at 1024^2, scaled to a 256^2 atlas (that is the
anti-aliasing). Light is an even sky, no sun: a die tumbles, and a baked sun
would end up lighting a face that lands on top from below. Saved with the
Standard view, so the colours are as picked; the GLB is unlit.

Faces (the TV turns them up by FACE_UP in
client/src/tv/grand-allee/grand-allee-dice.ts): value 1 on glTF +x, 6 -x, 2 +y, 5 -y, 3 +z, 4 -z (Blender: 1 +X,
6 -X, 2 +Z, 5 -Z, 3 -Y, 4 +Y). The permit die shows on each face the permit
of that value (grand_allee/building.rs permit_for). Every drawing is upright
for the TV once FACE_UP has turned its face to the top. Empties at the face
centres, children of the die: face_1 .. face_6, and face_approved_1/_2/_3,
face_rejected, face_free, face_double."""

import argparse
import json
import os
import sys

import bmesh
import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake  # noqa: E402
import board_ink as ink  # noqa: E402
import materials as m  # noqa: E402
import parts as p  # noqa: E402

SIZE, ROUND = 1.2, 0.11  # edge and corner radius, metres (the TV scales the die)
FACES = {1: ((1, 0, 0), (0, 1, 0)), 6: ((-1, 0, 0), (0, 1, 0)), 2: ((0, 0, 1), (0, 1, 0)),
         5: ((0, 0, -1), (0, -1, 0)), 3: ((0, -1, 0), (0, 0, 1)), 4: ((0, 1, 0), (0, 0, -1))}  # value: outward normal, drawing's up
PERMIT = {1: "approved", 2: "approved", 3: "approved", 4: "rejected", 5: "free", 6: "double"}
SIGN = {"approved": ("ok", "#1f4a2a"), "rejected": ("no", "#7a1f17"), "free": ("gift", "#4a2a12"), "double": (None, "#3a2414")}
PIPS = {1: [(64, 64)], 2: [(36, 36), (92, 92)], 3: [(34, 34), (64, 64), (94, 94)], 4: [(36, 36), (92, 36), (36, 92), (92, 92)],
        5: [(34, 34), (94, 34), (64, 64), (34, 94), (94, 94)], 6: [(36, 30), (92, 30), (36, 64), (92, 64), (36, 98), (92, 98)]}  # 128 px faces


def rounded(name, material):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=SIZE)
    bmesh.ops.bevel(bm, geom=list(bm.edges), offset=ROUND, segments=3, profile=0.5, affect="EDGES")
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(material)
    return p.link(bpy.data.objects.new(name, me))


def on_face(value, lift):
    """(u, v) on a face, in metres from its centre, to a point `lift` above it."""
    n, up = (Vector(a) for a in FACES[value])
    right = up.cross(n)
    c = n * (SIZE / 2 + lift)
    return lambda u, v: tuple(c + right * u + up * v)


def decals(kind):
    out = []
    for value in FACES:
        if kind == "number":  # a drilled pip: a soft rim, the dark pip, a darker core
            for k, (r, col) in enumerate(((0.112, "#8a7f6e"), (0.1, "#26241f"), (0.066, "#0d0d0f"))):
                pips = [d for x, y in PIPS[value] for d in ink.disc((x - 64) / 128 * SIZE, (64 - y) / 128 * SIZE, r, 24)]
                out.append(ink.flat(f"pip_{value}_{k}", pips, on_face(value, 0.002 * (k + 1)), m.plain(f"pip_{k}", col)))
        else:
            sign, col = SIGN[PERMIT[value]]
            mat = m.plain(f"sign_{PERMIT[value]}", col)
            if sign:
                out.append(ink.flat(f"sign_{value}", ink.icon(sign, SIZE), on_face(value, 0.003), mat))
            else:
                out.append(ink.text(f"sign_{value}", "×2", on_face(value, 0.003), mat, 0.34 * SIZE, 0.62 * SIZE, weight=0.06))
    return out


def make(kind, out_dir, res=256):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine, sc.cycles.device, sc.cycles.samples = "CYCLES", "CPU", 64
    sc.view_settings.view_transform = "Standard"
    world = bpy.data.worlds.new("even")
    next(n for n in world.node_tree.nodes if n.type == "BACKGROUND").inputs["Color"].default_value = (1, 1, 1, 1)
    sc.world = world
    skin = m.textured("die_ivory", "plastered_wall_04", 0.8, "#eee6d3", "OVERLAY", 0.5) if kind == "number" \
        else m.textured("die_oak", "weathered_plank_siding", 3.0, "#d8b98a", "OVERLAY", 0.3)
    name = f"die_{kind}"
    low = rounded(name, m.plain(name + "_bake", "#808080"))
    high = [rounded(name + "_high", skin)] + decals(kind)
    bake.pack_atlas([low])
    img = bpy.data.images.new(name + "_atlas", 4 * res, 4 * res, alpha=False, float_buffer=True)
    node = low.data.materials[0].node_tree.nodes.new("ShaderNodeTexImage")
    node.image = img
    low.data.materials[0].node_tree.nodes.active = node
    bake.select(high + [low], low)
    result = bpy.ops.object.bake(type="COMBINED", use_selected_to_active=True, cage_extrusion=0.03, max_ray_distance=0.08,
                                 margin=16, use_clear=True)
    assert result == {"FINISHED"}, result
    img.scale(res, res)
    atlas = os.path.join(out_dir, f"die-{kind}-atlas.png")
    sc.render.image_settings.file_format, sc.render.image_settings.color_mode = "PNG", "RGB"
    img.save_render(filepath=atlas, scene=sc)
    for o in high:
        bpy.data.objects.remove(o)
    bpy.context.view_layer.update()  # drop the removed decals from the view layer before selecting
    low.data.materials.clear()
    low.data.materials.append(bake.unlit(name, bpy.data.images.load(atlas)))
    marks = []
    for value, (n, _) in FACES.items():
        tag = str(value) if kind == "number" else PERMIT[value] + (f"_{value}" if PERMIT[value] == "approved" else "")
        e = p.empty(f"face_{tag}", low)
        e.location, e.empty_display_size = Vector(n) * SIZE / 2, 0.2
        marks.append(e)
    glb = os.path.join(out_dir, f"die-{kind}.glb")
    bake.select([low] + marks, low)
    bpy.ops.export_scene.gltf(filepath=glb, export_format="GLB", use_selection=True, export_image_format="WEBP",
                              export_image_quality=90, export_yup=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out_dir, f"die-{kind}-baked.blend"))
    return {"glb": glb, "glb_bytes": os.path.getsize(glb), "tris": sum(len(q.vertices) - 2 for q in low.data.polygons),
            "faces": sorted(e.name for e in marks)}


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)
    print("@@JSON@@" + json.dumps([make(k, args.out) for k in ("number", "permit")]))
