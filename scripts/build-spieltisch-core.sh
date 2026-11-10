#!/usr/bin/env bash
# Cross-compiles spieltisch-core's cdylib for the ABIs Spieltisch ships:
# arm64-v8a and armeabi-v7a for real hardware, x86_64 and x86 for the
# emulator. The 32-bit pair matters for the TV box: many Google TV boxes run
# a 32-bit userspace and load only 32-bit libraries. Requires
# ANDROID_NDK_HOME and cargo-ndk.
set -euo pipefail
cd "$(dirname "$0")/.."
# The game night's web client is embedded into the library at compile time, so
# it must be built before cargo runs.
command -v bun >/dev/null || { echo "bun is required to build the game night client" >&2; exit 1; }
(cd crates/mediagram-game/client && bun install --frozen-lockfile && bun run build)
OUT="${1:-android/spieltisch/src/main/jniLibs}"
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  cargo ndk -t "$abi" -o "$OUT" build -p spieltisch-core --release
done
