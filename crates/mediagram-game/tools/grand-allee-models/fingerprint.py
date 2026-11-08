"""A fingerprint of a built hotel: every part's name, material and vertex
positions (rounded to 0.01 mm), hashed per stage. Two builds with the same
fingerprint are the same model, so a refactor can be checked without a re-bake.

blender -b <hotel>.blend -P fingerprint.py   (prints @@FINGERPRINT@@ <json>)"""

import hashlib
import json

import bpy

stages = {}
for o in sorted(bpy.data.objects, key=lambda o: o.name):
    if o.type != "MESH" or o.parent is None or not o.parent.name.startswith("stage_"):
        continue
    h = stages.setdefault(o.parent.name, hashlib.sha256())
    h.update(o.name.encode())
    h.update(",".join(s.material.name for s in o.material_slots if s.material).encode())
    for v in o.data.vertices:
        h.update(("%.5f %.5f %.5f;" % tuple(o.matrix_world @ v.co)).encode())
print("@@FINGERPRINT@@ " + json.dumps({k: v.hexdigest()[:16] for k, v in sorted(stages.items())}))
