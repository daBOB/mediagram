#!/usr/bin/env bash
# Builds Spieltisch's release APK, proves it carries this machine's release
# key, and publishes it to the library channel under its own caption tag. The
# TV boxes' Spieltisch picks it up through the media app, which is not
# released with it. Only this machine holds the key.
#
#   scripts/release-spieltisch.sh              build, check, publish
#   scripts/release-spieltisch.sh --no-publish build and check the signature, then stop
set -euo pipefail
cd "$(dirname "$0")/.."

PUBLISH=1
case "${1:-}" in
  "") ;;
  --no-publish) PUBLISH=0 ;;
  *) echo "usage: $0 [--no-publish]" >&2; exit 2 ;;
esac

EXPECTED_CERT=5840181d3da5f44c9f0185bf4ac3345e5fe08786347350db04aaa6e69df4f576
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)
export PATH="$BUILD_TOOLS:$PATH"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$SDK/ndk/28.2.13676358}"

OUT=android/spieltisch/build/outputs/apk/release
APK=$OUT/spieltisch-release.apk

# An unsigned build leaves no signed APK; a stale one from an earlier build
# must not stand in for it, nor its dex metadata for this one's.
rm -rf "$OUT"/*.apk "$OUT"/baselineProfiles

# The 3D avatars for the game-night ceremony are generated, gitignored and
# made only where assets/game-night/avatars exists; without them the app
# would ship flat figures and nobody would notice.
AVATARS=android/spieltisch/src/main/assets/game-avatars
if [ -d assets/game-night/avatars ]; then
  bun crates/mediagram-game/tools/make-ceremony-avatars.mjs --out="$AVATARS"
fi
if [ "$(ls "$AVATARS"/*.glb 2>/dev/null | wc -l)" -lt 22 ]; then
  echo "refusing to release: $AVATARS must hold all 22 .glb (bun crates/mediagram-game/tools/make-ceremony-avatars.mjs --out=$AVATARS, needs assets/game-night/avatars)" >&2
  exit 1
fi

# Makes the game client the library embeds, then the library for every ABI.
scripts/build-spieltisch-core.sh
(cd android && ./gradlew -q :spieltisch:assembleRelease)

if [ ! -f "$APK" ]; then
  echo "refusing to publish: no $APK (an unsigned release leaves none: is the release key configured?)" >&2
  exit 1
fi

# A release without its native library dies in MainActivity.onCreate, before it can ever update itself away.
LIBS=$(unzip -Z1 "$APK")
for abi in arm64-v8a armeabi-v7a; do
  if ! grep -qx "lib/$abi/libspieltisch_core.so" <<<"$LIBS"; then
    echo "refusing to publish: $APK has no lib/$abi/libspieltisch_core.so (scripts/build-spieltisch-core.sh builds it)" >&2
    exit 1
  fi
done

CERT=$(apksigner verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
if [ "$CERT" != "$EXPECTED_CERT" ]; then
  echo "refusing to publish: $APK is signed by '${CERT:-nothing}', not the release key" >&2
  exit 1
fi
echo "signed with the release key: $APK"

if [ "$PUBLISH" -eq 0 ]; then
  echo "--no-publish: stopping before the dex metadata and the upload"
  exit 0
fi

# AGP writes one dex-metadata file per ART profile format when the app has a
# baseline profile; the channel carries the one for API 31 and up. Spieltisch
# has none today, so with nothing there the release goes out without a profile.
PROFILE=()
if [ -f "$OUT/output-metadata.json" ]; then
  DM_NAME=$(jq -r '[.baselineProfiles[]? | select(.minApi == 31) | .baselineProfiles[0]][0] // empty' "$OUT/output-metadata.json")
  if [ -n "$DM_NAME" ]; then
    if [ ! -f "$OUT/$DM_NAME" ]; then
      echo "refusing to publish: $OUT/output-metadata.json names $DM_NAME, which is not there" >&2
      exit 1
    fi
    PROFILE=(--profile "$OUT/$DM_NAME")
  fi
fi

# From source, so the command always matches this checkout (the installed
# uploader can lag main).
cargo run -q --release -p mediagram -- publish-app "$APK" ${PROFILE[@]+"${PROFILE[@]}"}
