#!/usr/bin/env bash
# Cross-compiles the native library for every Android ABI the app ships,
# then generates the Kotlin bindings from it. The bindings are committed;
# the .so files are not — CI reruns this and diffs the Kotlin output to
# catch drift between the Rust surface and what Kotlin was built against.
set -euo pipefail
cd "$(dirname "$0")/.."

JNI_LIBS_DIR="android/core/rust/src/main/jniLibs"
BINDINGS_OUT_DIR="android/core/rust/src/main/kotlin"

scripts/build-android-core.sh "$JNI_LIBS_DIR"

cargo run -p mediagram-core --features cli --bin uniffi-bindgen -- \
  generate \
  --library "$JNI_LIBS_DIR/arm64-v8a/libmediagram_core.so" \
  --language kotlin \
  --out-dir "$BINDINGS_OUT_DIR"
