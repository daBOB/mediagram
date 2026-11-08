#!/usr/bin/env bash
# Builds hotels without rendering and lists the coplanar overlaps the TV camera
# could see (z-fighting), by part name. usage: check-overlaps.sh <out dir> [hotel ...]
set -u
OUT=${1:?out dir}; shift
D=$(dirname "$(readlink -f "$0")")
for h in ${*:-lagune kirschbluete fjordhus hacienda_sol riad_atlas alpenrose maharadscha skyline savanne belle_epoque}; do
  blender -b --factory-startup --python-exit-code 1 -P "$D/build.py" -- --hotel "$h" --out "$OUT" --no-render 2>&1 | grep -E "Error|Traceback"
  blender -b "$OUT/$h.blend" -P "$D/zfight_visible.py" 2>/dev/null | grep -E "^ZF|@@VISIBLE_ZF@@" > "$OUT/$h-overlaps.txt"
  echo "$h: $(sed -n 's/^@@VISIBLE_ZF@@ //p' "$OUT/$h-overlaps.txt") visible overlaps"
done
