#!/usr/bin/env bash
# Builds every hotel (no render) and prints one fingerprint line per hotel.
# usage: fingerprint-all.sh <out dir>   (compare the output of two runs with diff)
set -u
OUT=${1:?out dir}
D=$(dirname "$(readlink -f "$0")")
for h in lagune kirschbluete fjordhus hacienda_sol riad_atlas alpenrose maharadscha skyline savanne belle_epoque; do
  blender -b --factory-startup --python-exit-code 1 -P "$D/build.py" -- --hotel "$h" --out "$OUT" --no-render 2>&1 | grep -E "Error|Traceback"
  echo "$h $(blender -b "$OUT/$h.blend" -P "$D/fingerprint.py" 2>/dev/null | sed -n 's/^@@FINGERPRINT@@ //p')"
done
