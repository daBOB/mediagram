"""Run the asset QA over every Kopfnuss set in the saved build: the source pieces together, then the baked mesh."""
import json
import sys
from collections import Counter

import bpy

sys.path.insert(0, "/home/andre/.claude/plugins/cache/blender-agent-skills/blender-agent-skills/1.3.1/skills/blender-asset-qa/scripts")
import qa_check


def brief(result):
    kinds = Counter(str(e).split(":")[0].split(" ")[0] for e in result["errors"])
    warns = Counter(str(w).split(":")[0].split(" ")[0] for w in result["warnings"])
    return {"pass": result["pass"], "errors": dict(kinds), "warnings": dict(warns),
            "other_errors": [str(e)[:150] for e in result["errors"] if not str(e).startswith("z-fighting")][:30],
            "zfight_pairs": sorted({"<->".join(sorted({part.split(".")[0].split(" ")[0] for part in str(e)[12:].split("<->")})) for e in result["errors"] if str(e).startswith("z-fighting")})}


for name in ("Doors", "Quiz", "Pyramid", "Winner"):
    scene = bpy.data.scenes["KN_Set_" + name]
    bpy.context.window.scene = scene
    pieces = [o.name for o in bpy.data.collections[f"COL_{name}_Geo"].all_objects if o.type == "MESH"]
    tris = 0
    for obj_name in pieces:
        mesh = bpy.data.objects[obj_name].evaluated_get(bpy.context.evaluated_depsgraph_get()).data
        tris += sum(len(p.vertices) - 2 for p in mesh.polygons)
    result = qa_check.run(objects=pieces, profile="game", eps=0.003, uv=False)
    print("@@QA", name, "pieces", len(pieces), "tris", tris, json.dumps(brief(result)))
    baked = bpy.data.objects.get("SM_" + name)
    if baked:
        result = qa_check.run(objects=[baked.name], profile="render", eps=0.003)
        metrics = result["objects"].get(baked.name, {})
        print("@@QA", name, "baked", json.dumps({k: metrics.get(k) for k in ("tris", "uv_maps", "uv_overlap", "uv_zero_area", "ngons") if k in metrics}),
              json.dumps(brief(result))[:600])
