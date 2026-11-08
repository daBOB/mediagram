"""Building parts for the Grand Allee hotels (Blender 5.2, bpy), in plot space:
metres; x along the road, y away from it, z up; the road runs along y = -d/2,
so every object's origin is the plot origin and facade patterns line up.

The core helpers (`geometry`) and roof shapes (`roofs`) are re-exported here,
so builders only import `parts`."""

import bmesh
from mathutils import Matrix

from geometry import add_box, box, empty, link, mesh_object  # noqa: F401  (re-exported)
from roofs import cone_roof, gable_roof, hip_roof, onion_dome  # noqa: F401  (re-exported)


def blob(name, center, radius, material, parent=None, squash=(1.0, 1.0, 1.0), subdiv=1):
    """A low-poly icosphere: blossom clusters, rocks, foliage."""

    def build(bm):
        bmesh.ops.create_icosphere(bm, subdivisions=subdiv, radius=radius,
                                   matrix=Matrix.Translation(center) @ Matrix.Diagonal((*squash, 1.0)))

    return mesh_object(name, build, material, parent)


def post(name, x, y, z0, z1, radius, material, parent=None, segments=8):
    """A round post or trunk."""

    def build(bm):
        bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=radius, radius2=radius * 0.8,
                              depth=z1 - z0, matrix=Matrix.Translation((x, y, (z0 + z1) / 2)))

    return mesh_object(name, build, material, parent)


def cornice(name, x0, y0, x1, y1, z, material, parent=None, out=0.25, height=0.35):
    """A projecting band under the eaves, so the roofline reads from afar. Its
    top stays 1 cm below `z`: flush with the wall's top it would z-fight there."""
    return box(name, (x0 - out, y0 - out, z - height), (x1 + out, y1 + out, z - 0.01), material, parent)


def chimney(name, x, y, z0, height, material, parent=None, side=0.7):
    return box(name, (x - side / 2, y - side / 2, z0), (x + side / 2, y + side / 2, z0 + height), material, parent)


def canopy(name, x0, x1, y_face, z, depth, cloth, post_material, parent=None):
    """An entrance canopy on two posts, out from a facade at y = y_face."""
    y = y_face - depth
    slab = box(name, (x0, y, z), (x1, y_face, z + 0.15), cloth, parent)

    def posts(bm):
        for px in (x0 + 0.1, x1 - 0.1):
            add_box(bm, px - 0.06, y + 0.04, 0, px + 0.06, y + 0.16, z)

    # the posts keep their own colour: only `tint_*` parts take the owner's
    return slab, mesh_object(name.removeprefix("tint_") + "_posts", posts, post_material, parent)


def balcony_row(name, x0, x1, y_face, z, depth, material, parent=None, posts=6):
    """A balcony slab in front of a facade at y = y_face (facing -y), with a
    top rail and posts as geometry (no alpha cut-outs on the TV box)."""

    def build(bm):
        y = y_face - depth
        add_box(bm, x0, y, z, x1, y_face, z + 0.18)
        add_box(bm, x0, y, z + 1.0, x1, y + 0.08, z + 1.08)
        for i in range(posts + 1):
            px = x0 + (x1 - x0) * i / posts
            add_box(bm, px - 0.04, y, z + 0.18, px + 0.04, y + 0.08, z + 1.0)

    return mesh_object(name, build, material, parent)


def awning(name, x0, x1, y_face, z, material, parent=None, depth=1.1, drop=0.6):
    """A sloped canvas awning over a ground-floor window band."""

    def build(bm):
        # a thin sloped slab, closed, so it reads from above and below
        top = [(x0, y_face, z), (x1, y_face, z), (x1, y_face - depth, z - drop), (x0, y_face - depth, z - drop)]
        v = [bm.verts.new(p) for p in top] + [bm.verts.new((x, y, zz - 0.05)) for x, y, zz in top]
        for f in [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]:
            bm.faces.new([v[i] for i in f])

    return mesh_object(name, build, material, parent)


def pool(name, x0, y0, x1, y1, deck, water, parent=None, rim=1.2, top=0.3):
    """A timber deck frame round a matte water basin set a little below it."""

    def frame(bm):
        add_box(bm, x0 - rim, y0 - rim, 0, x1 + rim, y0, top)
        add_box(bm, x0 - rim, y1, 0, x1 + rim, y1 + rim, top)
        add_box(bm, x0 - rim, y0, 0, x0, y1, top)
        add_box(bm, x1, y0, 0, x1 + rim, y1, top)

    deck_obj = mesh_object(name + "_deck", frame, deck, parent)
    water_obj = box(name + "_water", (x0, y0, 0), (x1, y1, top - 0.12), water, parent)
    return deck_obj, water_obj


def parasol(name, x, y, cloth, pole, parent=None, height=2.4, radius=1.3):
    """A pole with a shallow cone canopy (the canopy takes the owner colour)."""
    # the pole keeps its own colour: only `tint_*` parts take the owner's
    stick = box(name.removeprefix("tint_") + "_pole", (x - 0.05, y - 0.05, 0), (x + 0.05, y + 0.05, height), pole, parent)

    def cone(bm):
        bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=radius, radius2=0.05, depth=0.5,
                              matrix=Matrix.Translation((x, y, height - 0.1)))

    return stick, mesh_object(name, cone, cloth, parent)


def prism(bm, outline, w0, w1, P):
    """Extrudes a closed (u, v) outline from w0 to w1; P maps (u, v, w) to plot xyz."""
    ends = [[bm.verts.new(P(u, v, w)) for u, v in outline] for w in (w0, w1)]
    bm.faces.new(ends[0])
    bm.faces.new(ends[1])
    n = len(outline)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((ends[0][i], ends[0][j], ends[1][j], ends[1][i]))



def cylinder(name, x, y, z0, z1, r0, material, parent=None, r1=None, segments=12):
    """A straight (or flared, with `r1` at the top) round solid: towers, basins, drums."""

    def build(bm):
        bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=r0, radius2=r0 if r1 is None else r1,
                              depth=z1 - z0, matrix=Matrix.Translation((x, y, (z0 + z1) / 2)))

    return mesh_object(name, build, material, parent)
