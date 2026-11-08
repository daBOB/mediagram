#!/usr/bin/env bash
# Bakes and exports every Grand Allee hotel, renders each GLB the way the TV
# draws it, and runs the 3D asset check on the baked meshes.
# usage: bake-all.sh <out dir> [hotel ...]
set -u
OUT=${1:?out dir}; shift
D=$(dirname "$(readlink -f "$0")")
QA=${QA_CHECK:-$HOME/.claude/plugins/cache/blender-agent-skills/blender-agent-skills/1.2.0/skills/blender-asset-qa/scripts/qa_check.py}
# outer plots bake at 1024², the wide park plots (and the 40 m Skyline) at 2048² for the same sharpness
declare -A RES=([maharadscha]=2048 [skyline]=2048 [savanne]=2048 [belle_epoque]=2048)
declare -A PLOT=([fjordhus]=24,16 [alpenrose]=24,16 [maharadscha]=32,14 [skyline]=32,14 [savanne]=32,14 [belle_epoque]=32,14)
HOTELS=${*:-lagune kirschbluete fjordhus hacienda_sol riad_atlas alpenrose maharadscha skyline savanne belle_epoque}
mkdir -p "$OUT"
for h in $HOTELS; do
  echo "== $h $(date +%T)"
  blender -b --factory-startup --python-exit-code 1 -P "$D/bake.py" -- --hotel "$h" --out "$OUT" --res "${RES[$h]:-1024}" 2>&1 | grep -E "@@JSON@@|Error|Traceback"
  blender -b --factory-startup --python-exit-code 1 -P "$D/verify_glb.py" -- --glb "$OUT/$h.glb" --out "$OUT/$h-tv.png" --plot "${PLOT[$h]:-20,16}" 2>&1 | grep -E "Error|Traceback"
  objs=$(blender -b "$OUT/$h-baked.blend" --python-expr "import bpy; print('@@OBJS@@', ' '.join(o.name for o in bpy.data.objects if o.type == 'MESH' and o.name.startswith('stage_')))" 2>/dev/null | sed -n 's/^@@OBJS@@ //p')
  blender -b "$OUT/$h-baked.blend" --python-exit-code 1 -P "$QA" -- --objects $objs --profile game 2>&1 | grep -E "^\{|@@JSON@@" | tail -1 | sed "s/^@@JSON@@//" > "$OUT/$h-qa.json"
  vis=$(blender -b "$OUT/$h-baked.blend" -P "$D/zfight_visible.py" 2>/dev/null | tee "$OUT/$h-zfight.txt" | sed -n "s/^@@VISIBLE_ZF@@ //p")
  echo "z-fighting visible from above: ${vis:-?}"
  python3 -c "import json,sys; r=json.load(open(sys.argv[1])); print('qa pass:', r.get('pass'), 'errors:', len(r.get('errors', [])), 'warnings:', len(r.get('warnings', [])))" "$OUT/$h-qa.json" 2>/dev/null || echo "qa: no result"
done
echo "== done $(date +%T)"
