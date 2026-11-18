"""Set dressing shared by the Kopfnuss stages: curtains, mouldings, marquee bulbs, spotlights with beams."""
import math

from mathutils import Vector

from kn_common import add_sphere, fx, place, pydata


def curtain(name, start, end, z0, z1, mat, coll, parent=None, folds=10, depth=.28, tex=.6):
    """A hanging drape from `start` to `end` (floor points): deep folds, gathered a little tighter at the top."""
    start, end = Vector((*start, 0)), Vector((*end, 0))
    along = end - start
    out = Vector((along.y, -along.x, 0)).normalized()
    columns = folds * 10
    verts, faces = [], []
    for row, (z, gather) in enumerate(((z0, 1.0), ((z0 + z1) / 2, .85), (z1, .6))):
        for i in range(columns + 1):
            t = i / columns
            wave = math.sin(t * folds * math.tau) + .35 * math.sin(t * folds * math.tau * 2.3 + row)
            p = start + along * t + out * wave * depth * gather
            verts.append((p.x, p.y, z))
    for row in range(2):
        for i in range(columns):
            a = row * (columns + 1) + i
            faces.append((a, a + 1, a + columns + 2, a + columns + 1))
    drape = place(name, pydata(name, verts, faces), mat, coll, parent, smooth=True, tex=tex)
    drape["open"] = True  # a one-sided sheet, seen from whichever side faces the camera
    return drape


def moulding(name, center, width, height, mat, coll, parent, bar=.05, tex=1.0):
    """A raised rectangular frame on a wall or door, in the XZ plane at depth center.y."""
    w, h, b = width / 2, height / 2, bar / 2

    def rect(dx, dz, depth):
        return [(-dx, depth, -dz), (dx, depth, -dz), (dx, depth, dz), (-dx, depth, dz)]
    # one closed frame (outer and inner rectangle, front and back), not four bars overlapping at the corners
    verts = rect(w + b, h + b, -b) + rect(w - b, h - b, -b) + rect(w + b, h + b, b) + rect(w - b, h - b, b)
    faces = []
    for i in range(4):
        n = (i + 1) % 4
        faces += [(i, n, 4 + n, 4 + i), (8 + n, 8 + i, 12 + i, 12 + n), (8 + i, 8 + n, n, i), (4 + i, 4 + n, 12 + n, 12 + i)]
    return place(name, pydata(name, verts, faces), mat, coll, parent, center, tex=tex)


def bulbs(name, points, radius, mat, coll, parent=None, tex=1.0):
    """Marquee bulbs as one mesh, so a hundred of them cost one object."""
    verts, faces = [], []
    for point in points:
        add_sphere(verts, faces, point, (radius,) * 3, 10)
    return place(name, pydata(name, verts, faces), mat, coll, parent, smooth=True, tex=tex)


def beam(name, at, target, radius, color, coll, parent=None, sides=28):
    """A spotlight's light cone, drawn by the TV as an additive haze. UV v runs from the lamp (0) to the floor (1)."""
    at, target = Vector(at), Vector(target)
    axis = (target - at).normalized()
    side = axis.orthogonal().normalized()
    other = axis.cross(side)
    verts, faces, uvs = [], [], []
    for centre, r in ((at, radius * .08), (target, radius)):
        for j in range(sides):
            a = math.tau * j / sides
            verts.append(centre + (side * math.cos(a) + other * math.sin(a)) * r)
    for j in range(sides):
        n = (j + 1) % sides
        faces.append((j, n, sides + n, sides + j))
        uvs += [(j / sides, 0), ((j + 1) / sides, 0), ((j + 1) / sides, 1), (j / sides, 1)]
    mesh = pydata(name, verts, faces)
    mesh.uv_layers.new(name="UVMap").data.foreach_set("uv", [c for uv in uvs for c in uv])
    return fx(place(name, mesh, None, coll, parent, smooth=True), "beam", color)


def fx_quad(name, at, width, height, kind, color, coll, parent=None, upright=False):
    """A quad the TV fills with an animated effect: flat on the floor (pad rings, sweeps), or upright facing
    the front (door sparkle, star halo)."""
    w, h = width / 2, height / 2
    corners = [(-w, 0, -h), (w, 0, -h), (w, 0, h), (-w, 0, h)] if upright else [(-w, -h, 0), (w, -h, 0), (w, h, 0), (-w, h, 0)]
    mesh = pydata(name, corners, [(0, 1, 2, 3)])
    mesh.uv_layers.new(name="UVMap").data.foreach_set("uv", [0, 0, 1, 0, 1, 1, 0, 1])
    return fx(place(name, mesh, None, coll, parent, at), kind, color)


def fx_ring(name, z, radius, half, kind, color, coll, parent=None, segments=96):
    """A flat band over a floor light ring, for its animated glow. UV u runs round the ring, v across the band."""
    verts, faces, uvs = [], [], []
    for i in range(segments):
        a = math.tau * i / segments
        for r in (radius - half, radius + half):
            verts.append((math.cos(a) * r, math.sin(a) * r, z))
        n = (i + 1) % segments
        faces.append((i * 2, i * 2 + 1, n * 2 + 1, n * 2))
        uvs += [(i / segments, 0), (i / segments, 1), ((i + 1) / segments, 1), ((i + 1) / segments, 0)]
    mesh = pydata(name, verts, faces)
    mesh.uv_layers.new(name="UVMap").data.foreach_set("uv", [c for uv in uvs for c in uv])
    return fx(place(name, mesh, None, coll, parent), kind, color)


def cyclorama(name, radius, start, end, height, mat, coll, parent=None, z=0.0, tex=.3, segments=64):
    """The curved studio back wall, seen from the stage centre."""
    verts, faces = [], []
    for i in range(segments + 1):
        a = start + (end - start) * i / segments
        for h in (z, z + height):
            verts.append((math.cos(a) * radius, math.sin(a) * radius, h))
        if i:
            faces.append((i * 2 - 2, i * 2, i * 2 + 1, i * 2 - 1))
    wall = place(name, pydata(name, verts, faces), mat, coll, parent, smooth=True, tex=tex)
    wall["open"] = True
    return wall
