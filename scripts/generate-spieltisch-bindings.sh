#!/usr/bin/env bash
# Cross-compiles Spieltisch's native library for every ABI it ships, then
# generates the Kotlin bindings from it. The bindings are committed; the .so
# files are not. scripts/check.sh regenerates the bindings from a host build
# and fails when they differ from the committed ones.
set -euo pipefail
cd "$(dirname "$0")/.."

JNI_LIBS_DIR="android/spieltisch/src/main/jniLibs"
BINDINGS_OUT_DIR="android/spieltisch/src/main/kotlin"

scripts/build-spieltisch-core.sh "$JNI_LIBS_DIR"

cargo run -p spieltisch-core --features cli --bin uniffi-bindgen -- \
  generate \
  --library "$JNI_LIBS_DIR/arm64-v8a/libspieltisch_core.so" \
  --language kotlin \
  --no-format \
  --out-dir "$BINDINGS_OUT_DIR"

# UniFFI emits whitespace-only lines that otherwise fail `git diff --check`.
bindings_file="$BINDINGS_OUT_DIR/uniffi/spieltisch_core/spieltisch_core.kt"
sed 's/[[:blank:]]*$//' "$bindings_file" > "$bindings_file.tmp"
mv "$bindings_file.tmp" "$bindings_file"
