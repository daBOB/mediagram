"""Core mesh helpers for the Grand Allee hotels (Blender 5.2, bpy): objects in
plot space (metres; x along the road, y away from it, z up; every object's
origin is the plot origin), built from bmesh."""

import bmesh
import bpy


def link(obj, parent=None):
    bpy.context.scene.collection.objects.link(obj)
    if parent is not None:
        obj.parent = parent
    return obj


def empty(name, parent=None):
    return link(bpy.data.objects.new(name, None), parent)


def mesh_object(name, build, material, parent=None):
    """`build(bm)` adds geometry to a fresh bmesh; the result is one object."""
    bm = bmesh.new()
    build(bm)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(material)
    return link(bpy.data.objects.new(name, me), parent)


def add_box(bm, x0, y0, z0, x1, y1, z1):
    v = [bm.verts.new(p) for p in [
        (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
        (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]]
    for f in [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]:
        bm.faces.new([v[i] for i in f])


def box(name, lo, hi, material, parent=None):
    return mesh_object(name, lambda bm: add_box(bm, *lo, *hi), material, parent)

