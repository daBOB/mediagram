#!/usr/bin/env bash
# Builds the Android release APK, proves it carries this machine's release
# key, and publishes it to the library channel, where every installed
# release build picks it up on its own. Only this machine holds the key.
set -euo pipefail
cd "$(dirname "$0")/.."

EXPECTED_CERT=5840181d3da5f44c9f0185bf4ac3345e5fe08786347350db04aaa6e69df4f576
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)
export PATH="$BUILD_TOOLS:$PATH"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$SDK/ndk/28.2.13676358}"

APK=android/app/build/outputs/apk/release/app-release.apk

# An unsigned build leaves no app-release.apk; a stale signed one from an
# earlier build must not stand in for it.
rm -f android/app/build/outputs/apk/release/*.apk

scripts/build-android-core.sh
(cd android && ./gradlew -q :app:assembleRelease)

CERT=$(apksigner verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
if [ "$CERT" != "$EXPECTED_CERT" ]; then
  echo "refusing to publish: $APK is signed by '${CERT:-nothing}', not the release key" >&2
  exit 1
fi
aapt2 dump badging "$APK" | head -1

# From source, so the command always matches this checkout (the installed
# uploader can lag main).
cargo run -q --release -p mediagram -- publish-app "$APK"
