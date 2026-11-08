"""Same-facing coplanar overlaps (z-fighting) in a baked hotel that the TV can
see. The board camera always looks down, so faces pointing down (undersides,
soffits, bottoms on the ground) can never show or flicker and are skipped.
blender -b <hotel>.blend -P zfight_visible.py   (prints ZF lines and a count)
Works on a built file (names the parts) or a baked one (names the stage meshes);
also on the baked board (`board_*` meshes) and dice (`die_*`)."""
import bpy
from mathutils.bvhtree import BVHTree

tris, owner = [], []
verts = []
for o in bpy.data.objects:
    in_stage = o.name.startswith(("stage_", "board_", "die_")) or (o.parent is not None and o.parent.name.startswith("stage_"))
    if o.type != "MESH" or not in_stage:
        continue
    mw = o.matrix_world
    me = o.data
    me.calc_loop_triangles()
    base = len(verts)
    verts += [mw @ v.co for v in me.vertices]
    for lt in me.loop_triangles:
        tris.append(tuple(base + i for i in lt.vertices))
        owner.append(o.name)
bvh = BVHTree.FromPolygons(verts, tris)
seen = set()
for ti, t in enumerate(tris):
    a, b, c = (verts[i] for i in t)
    n = (b - a).cross(c - a)
    if n.length < 1e-6:
        continue
    n.normalize()
    cen = (a + b + c) / 3
    for _, _, tj, _ in bvh.find_nearest_range(cen, 0.001):
        if tj is None or tj == ti or (min(ti, tj), max(ti, tj)) in seen:
            continue
        a2, b2, c2 = (verts[i] for i in tris[tj])
        n2 = (b2 - a2).cross(c2 - a2)
        if n2.length < 1e-6:
            continue
        n2.normalize()
        if n.dot(n2) > 0.999 and n.z > -0.5 and not set(t) & set(tris[tj]):
            seen.add((min(ti, tj), max(ti, tj)))
            print(f"ZF {owner[ti]} / {owner[tj]} at ({cen.x:.2f},{cen.y:.2f},{cen.z:.2f}) facing ({n.x:.0f},{n.y:.0f},{n.z:.0f})")
print(f"@@VISIBLE_ZF@@ {len(seen)}")
