#!/usr/bin/env bash
# Cross-compiles mediagram-core's cdylib for the Android ABIs the app ships:
# arm64-v8a and armeabi-v7a for real hardware, x86_64 and x86 for the
# emulator. The 32-bit pair matters for televisions: many Google TV boxes
# have a 64-bit CPU but run a 32-bit userspace, and load only 32-bit
# libraries. Requires ANDROID_NDK_HOME and cargo-ndk.
set -euo pipefail
ABIS="arm64-v8a armeabi-v7a x86_64 x86"
OUT="${1:-android/core/rust/src/main/jniLibs}"
for abi in $ABIS; do
  cargo ndk -t "$abi" -o "$OUT" build -p mediagram-core --release
done
