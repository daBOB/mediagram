"""Bake a lit set into one atlas the TV draws unlit.

Everything Cycles shades goes into the picture: light, soft shadow, bounce, glow spill, and reflections as the
stage camera sees them. The TV then pays one texture lookup per pixel for a path-traced look.
"""
import hashlib
import math
import os

import bmesh
import bpy
import numpy as np
from mathutils import Vector

OUT = os.path.expanduser("~/.cache/kopfnuss-arena/bake")
PASSES = {"EMIT", "DIRECT", "INDIRECT", "DIFFUSE", "GLOSSY", "TRANSMISSION"}


def _piece(src, depsgraph, eyes, materials):
    """A world-space copy of one set piece, modifiers applied, blown up by its `tex` share so the unwrap hands
    it that much more of the atlas; `true_position` remembers where it belongs. Faces no stage camera (`eyes`)
    can face stay, since they still cast shadow and light, but are split off and shrunk to cost almost no atlas."""
    evaluated = src.evaluated_get(depsgraph)
    mesh = bpy.data.meshes.new_from_object(evaluated, preserve_all_data_layers=True, depsgraph=depsgraph)
    mesh.transform(evaluated.matrix_world)
    slots = []
    for mat in mesh.materials:
        if mat not in materials:
            materials.append(mat)
        slots.append(materials.index(mat))
    bm = bmesh.new()
    bm.from_mesh(mesh)
    true = bm.verts.layers.float_vector.new("true_position")
    for face in bm.faces:
        face.material_index = slots[face.material_index] if slots else 0
    for v in bm.verts:
        v[true] = v.co
    unseen = set()
    if not src.get("open"):  # drapes and skies are one-sided sheets: their normals say nothing about view
        away = [f for f in bm.faces if all((eye - f.calc_center_median()).dot(f.normal) <= 0 for eye in eyes)]
        if away and len(away) < len(bm.faces):
            split = bmesh.ops.split(bm, geom=away)["geom"]
            unseen = {v for f in split if isinstance(f, bmesh.types.BMFace) for v in f.verts}
    centre = sum((v.co for v in bm.verts), Vector()) / len(bm.verts)
    share = float(src.get("tex", 1.0))
    for v in bm.verts:
        v.co = centre + (v.co - centre) * share * (.12 if v in unseen else 1)
    bm.to_mesh(mesh)
    bm.free()
    return mesh


def _join(name, sources, depsgraph, eyes):
    """One mesh from every set piece, carrying the set's materials and an empty atlas UV map."""
    bm = bmesh.new()
    materials = []
    for src in sources:
        mesh = _piece(src, depsgraph, eyes, materials)
        bm.from_mesh(mesh)
        bpy.data.meshes.remove(mesh)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    for mat in materials:
        mesh.materials.append(mat)
    while mesh.uv_layers:
        mesh.uv_layers.remove(mesh.uv_layers[0])
    mesh.uv_layers.new(name="Atlas")
    return mesh


def _fingerprint(mesh):
    """Where every face corner sits, in order: equal only for a mesh a cached layout still fits."""
    loops = np.empty(len(mesh.loops), "i")
    mesh.loops.foreach_get("vertex_index", loops)
    co = np.empty(len(mesh.vertices) * 3, "f")
    mesh.attributes["true_position"].data.foreach_get("vector", co)
    return hashlib.md5(np.round(co.reshape(-1, 3)[loops], 4).tobytes()).hexdigest()


def _unwrap(obj, view_layer, layout, key):
    """Give the joined mesh its atlas UVs and put its vertices back where they belong. With a `layout` file
    whose key matches, the UVs are read back from it instead: a set that keeps its cached atlas keeps the
    layout that atlas was baked with, so the atlas is never shown through another unwrap. Returns the UVs it
    settled on, and whether they came from the cache."""
    saved = np.load(layout) if layout and os.path.exists(layout) else None
    cached = saved is not None and str(saved["key"]) == key
    if cached:
        uv = saved["uv"]
        obj.data.uv_layers["Atlas"].data.foreach_set("uv", uv)
    else:
        for other in view_layer.objects:
            other.select_set(False)
        obj.select_set(True)
        view_layer.objects.active = obj
        bpy.ops.object.mode_set(mode="EDIT")
        bpy.ops.mesh.select_all(action="SELECT")
        bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.0, scale_to_bounds=False)
        bpy.ops.uv.pack_islands(rotate=True, margin=.0025, shape_method="AABB")
        bpy.ops.object.mode_set(mode="OBJECT")
        uv = np.empty(len(obj.data.loops) * 2, "f")
        obj.data.uv_layers["Atlas"].data.foreach_get("uv", uv)
    mesh = obj.data
    true = mesh.attributes["true_position"]
    co = [0.0] * (len(mesh.vertices) * 3)
    true.data.foreach_get("vector", co)
    mesh.vertices.foreach_set("co", co)
    mesh.attributes.remove(true)
    mesh.update()
    return uv, cached


def _baked_material(name, path):
    """The baked picture as the surface's own light. glTF carries it as an emissive map on a black material;
    the TV shows that map as it is, untouched by its lights."""
    mat = bpy.data.materials.new("KN_Baked_" + name)
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    out, glow, tex = nodes.new("ShaderNodeOutputMaterial"), nodes.new("ShaderNodeEmission"), nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(path, check_existing=False)
    links.new(tex.outputs["Color"], glow.inputs["Color"])
    links.new(glow.outputs["Emission"], out.inputs["Surface"])
    mat.use_backface_culling = False  # drapes and the sky are seen from either side
    return mat


def bake_set(scene, name, size=2048, samples=128, reuse=False):
    """Join, unwrap and bake one set. Returns the baked object, in its own COL_<name>_Baked collection.
    `reuse` takes the atlas and layout already in the cache instead of rendering. Changed geometry is noticed
    and rendered again; a changed lamp or material is not, so name the set in --rebake after such a change."""
    bpy.context.window.scene = scene
    view_layer = scene.view_layers[0]
    geo = bpy.data.collections[f"COL_{name}_Geo"]
    geo.hide_render = False
    baked = bpy.data.collections.new(f"COL_{name}_Baked")
    scene.collection.children.link(baked)
    view_layer.update()
    # by name: a collection hands out its objects in an order that differs from run to run, and the cached
    # layout only fits a mesh joined in the same order
    sources = sorted((o for o in geo.all_objects if o.type == "MESH"), key=lambda o: o.name)
    eyes = [c.matrix_world.translation.copy() for c in bpy.data.collections[f"COL_{name}_Cameras"].objects]
    mesh = _join("SM_" + name, sources, bpy.context.evaluated_depsgraph_get(), eyes)
    obj = bpy.data.objects.new("SM_" + name, mesh)
    obj.parent = bpy.data.objects["Set_" + name]
    baked.objects.link(obj)
    for src in sources:  # off the rig, or the exporter would follow the rig's children to every source piece
        world = src.matrix_world.copy()
        src.parent = None
        src.matrix_world = world
    path = os.path.join(OUT, f"T_{name}_Light.png")
    layout = os.path.join(OUT, f"T_{name}_Layout.npz")
    # the key names the geometry and the bake's quality: a quick trial bake is never reused as the real thing
    key = f"{_fingerprint(mesh)}:{size}:{samples}"
    uv, cached = _unwrap(obj, view_layer, layout if reuse and os.path.exists(path) else None, key)
    if not cached:
        if os.path.exists(layout):
            os.remove(layout)  # a bake cut short must not leave the old layout beside a half-new atlas
        _render(scene, name, mesh, geo, size, samples, path)
        np.savez(layout, uv=uv, key=key)
    mesh.materials.clear()
    mesh.materials.append(_baked_material(name, path))
    mesh.polygons.foreach_set("material_index", [0] * len(mesh.polygons))
    mesh.update()
    print("KN_BAKED", name, len(mesh.polygons), "faces from", len(sources), "pieces ->", path)
    return obj


def _render(scene, name, mesh, geo, size, samples, path):
    """The Cycles bake itself, saved through the scene's view so the filmic roll-off is in the texture."""
    image = bpy.data.images.new(f"T_{name}_Light", size, size, alpha=False, float_buffer=True)
    for mat in mesh.materials:
        nodes = mat.node_tree.nodes
        slot = nodes.get("KN_BakeImage") or nodes.new("ShaderNodeTexImage")
        slot.name = "KN_BakeImage"
        slot.image = image
        nodes.active = slot
    geo.hide_render = True  # the pieces must not shadow their own joined copy
    try:
        scene.cycles.samples = samples
        scene.render.bake.view_from = "ACTIVE_CAMERA"
        result = bpy.ops.object.bake(type="COMBINED", pass_filter=PASSES, margin=max(4, size // 256), use_clear=True)
        if result != {"FINISHED"}:
            raise RuntimeError(f"bake of {name} ended with {result}")
        os.makedirs(OUT, exist_ok=True)
        scene.render.image_settings.file_format = "PNG"
        scene.render.image_settings.color_mode = "RGB"
        scene.render.image_settings.color_depth = "8"
        image.save_render(path, scene=scene)
    finally:  # a failed bake leaves the scene as it found it
        geo.hide_render = False
        bpy.data.images.remove(image)
