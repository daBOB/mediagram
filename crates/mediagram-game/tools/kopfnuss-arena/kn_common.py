"""Mesh helpers for the Kopfnuss stage builder. Data API only, so they run live and headless alike."""
import math

import bmesh
import bpy
from mathutils import Vector


def rgba(value, alpha=1):
    """A #rrggbb colour as linear RGBA, the space Blender's sockets expect."""
    value = value.lstrip("#")
    rgb = [int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb) + (alpha,)


def collection(name, scene):
    coll = bpy.data.collections.new(name)
    scene.collection.children.link(coll)
    return coll


def empty(name, coll, parent=None, at=(0, 0, 0)):
    obj = bpy.data.objects.new(name, None)
    coll.objects.link(obj)
    obj.location = at
    obj.parent = parent
    obj.empty_display_size = .35
    return obj


def place(name, mesh, mat, coll, parent=None, at=(0, 0, 0), smooth=False, tex=1.0):
    """Wrap a mesh in an object. `tex` is its share of the baked atlas: 1 for hero pieces, less for far ones."""
    obj = bpy.data.objects.new(name, mesh)
    coll.objects.link(obj)
    obj.parent = parent
    obj.location = at
    if mat:
        mesh.materials.append(mat)
    if smooth:
        mesh.polygons.foreach_set("use_smooth", [True] * len(mesh.polygons))
        sharp = mesh.attributes.new("sharp_edge", "BOOLEAN", "EDGE")
        bm = bmesh.new()
        bm.from_mesh(mesh)
        flags = [len(e.link_faces) == 2 and e.calc_face_angle() > math.radians(40) for e in bm.edges]
        bm.free()
        sharp.data.foreach_set("value", flags)
    obj["tex"] = tex
    return obj


def from_bmesh(name, bm):
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    return mesh


def pydata(name, verts, faces):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    return mesh


def box(name, at, size, mat, coll, parent=None, bevel=.06, tex=1.0):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1)
    for v in bm.verts:
        v.co = Vector((v.co.x * size[0], v.co.y * size[1], v.co.z * size[2]))
    obj = place(name, from_bmesh(name, bm), mat, coll, parent, at, tex=tex)
    if bevel:
        mod = obj.modifiers.new("Soft edges", "BEVEL")
        mod.width = bevel
        mod.segments = 3
        obj.modifiers.new("Weighted corners", "WEIGHTED_NORMAL")
    return obj


def cylinder(name, at, radius, depth, mat, coll, parent=None, vertices=48, radius2=None, tex=1.0):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=vertices, radius1=radius,
                          radius2=radius if radius2 is None else radius2, depth=depth)
    return place(name, from_bmesh(name, bm), mat, coll, parent, at, True, tex)


def add_sphere(verts, faces, centre, scale, segments):
    """Append a UV sphere to a vertex and a face list. Written out instead of taken from bmesh: its sphere
    operator numbers faces differently on every run, which moves a rebuilt set off the atlas it was baked to."""
    rings = max(3, segments // 2)
    top = len(verts)
    verts.append((centre[0], centre[1], centre[2] + scale[2]))
    for j in range(1, rings):
        theta = math.pi * j / rings
        for i in range(segments):
            phi = math.tau * i / segments
            verts.append((centre[0] + scale[0] * math.sin(theta) * math.cos(phi),
                          centre[1] + scale[1] * math.sin(theta) * math.sin(phi),
                          centre[2] + scale[2] * math.cos(theta)))
    bottom = len(verts)
    verts.append((centre[0], centre[1], centre[2] - scale[2]))
    last = top + 1 + (rings - 2) * segments
    for i in range(segments):
        n = (i + 1) % segments
        faces.append((top, top + 1 + i, top + 1 + n))
        for j in range(rings - 2):
            a = top + 1 + j * segments
            faces.append((a + i, a + segments + i, a + segments + n, a + n))
        faces.append((bottom, last + n, last + i))


def ball(name, at, scale, mat, coll, parent=None, tex=1.0, segments=24):
    verts, faces = [], []
    add_sphere(verts, faces, (0, 0, 0), scale, segments)
    return place(name, pydata(name, verts, faces), mat, coll, parent, at, True, tex)


def tube(name, points, radius, mat, coll, parent=None, closed=False, sides=8, tex=1.0):
    """A round tube along `points`. Its UV runs 0..1 along the length, which the runtime chase effects read."""
    points = [Vector(p) for p in points]
    count = len(points)
    lengths = [0.0]
    for i in range(1, count):
        lengths.append(lengths[-1] + (points[i] - points[i - 1]).length)
    total = lengths[-1] + ((points[0] - points[-1]).length if closed else 0)
    normal = None
    verts = []
    for i, p in enumerate(points):
        ahead = points[(i + 1) % count] if closed or i < count - 1 else p
        behind = points[i - 1] if closed or i > 0 else p
        tangent = (ahead - behind).normalized()
        if normal is None:
            normal = tangent.orthogonal().normalized()
        normal = (normal - tangent * normal.dot(tangent)).normalized()
        side = tangent.cross(normal)
        for j in range(sides):
            a = math.tau * j / sides
            verts.append(p + (normal * math.cos(a) + side * math.sin(a)) * radius)
    faces, uvs = [], []
    rings = count if closed else count - 1
    for i in range(rings):
        k = (i + 1) % count
        u0 = lengths[i] / total
        u1 = lengths[k] / total if k else 1.0
        for j in range(sides):
            n = (j + 1) % sides
            faces.append((i * sides + j, i * sides + n, k * sides + n, k * sides + j))
            uvs += [(u0, j / sides), (u0, (j + 1) / sides), (u1, (j + 1) / sides), (u1, j / sides)]
    mesh = pydata(name, verts, faces)
    layer = mesh.uv_layers.new(name="UVMap")
    layer.data.foreach_set("uv", [c for uv in uvs for c in uv])
    return place(name, mesh, mat, coll, parent, smooth=True, tex=tex)


def circle_points(center, radius, start=0.0, end=math.tau, count=64, vertical=False):
    """Points on an arc: flat on the floor, or standing in the XZ plane when `vertical`."""
    closed = abs(end - start - math.tau) < 1e-6
    steps = count if closed else count - 1
    out = []
    for i in range(count):
        a = start + (end - start) * i / steps
        if vertical:
            out.append((center[0] + math.cos(a) * radius, center[1], center[2] + math.sin(a) * radius))
        else:
            out.append((center[0] + math.cos(a) * radius, center[1] + math.sin(a) * radius, center[2]))
    return out, closed


def ring(name, center, radius, thickness, mat, coll, parent=None, tex=1.0, **arc):
    points, closed = circle_points(center, radius, **arc)
    return tube(name, points, thickness, mat, coll, parent, closed, tex=tex)


def prism(name, outline, y0, y1, mat, coll, parent=None, tex=1.0):
    """An XZ outline pushed from y0 (front) to y1 (back): crowns, stars, gables."""
    count = len(outline)
    if sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(outline, outline[1:] + outline[:1])) < 0:
        outline = outline[::-1]  # anticlockwise seen from the front, so every face looks outward
    verts = [(x, y, z) for y in (y0, y1) for x, z in outline]
    faces = [tuple(range(count)), tuple(range(count * 2 - 1, count - 1, -1))]
    faces += [(i + count, (i + 1) % count + count, (i + 1) % count, i) for i in range(count)]
    return place(name, pydata(name, verts, faces), mat, coll, parent, tex=tex)


def fx(obj, kind, color):
    """Mark a mesh as a runtime effect: the TV swaps its material for a shader, and the bake leaves it out."""
    obj["fx"] = kind
    obj["color"] = color
    return obj
