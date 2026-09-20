#!/usr/bin/env bash
# Cross-compiles mediagram-core's cdylib for the Android ABIs the app ships:
# arm64-v8a for real hardware, x86_64 for the emulator. Requires
# ANDROID_NDK_HOME and cargo-ndk.
set -euo pipefail
ABIS="arm64-v8a x86_64"
OUT="${1:-android/core/rust/src/main/jniLibs}"
for abi in $ABIS; do
  cargo ndk -t "$abi" -o "$OUT" build -p mediagram-core --release
done
