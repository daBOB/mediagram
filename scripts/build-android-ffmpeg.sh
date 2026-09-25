#!/usr/bin/env bash
# Builds libffmpegJNI.so — Media3's FFmpeg audio decoder extension — for the
# four Android ABIs the app ships, into android/core/ffmpeg/src/main/jniLibs.
#
# Why it exists: Media3 does not publish decoder_ffmpeg to Maven, and many
# devices (Google TV boxes especially) have no DTS or TrueHD decoder, so those
# films played silently. FFmpeg decodes them to PCM instead.
#
# What it builds, following Media3 1.10.1's libraries/decoder_ffmpeg README
# and build_ffmpeg.sh (same configure options, same FFmpeg 6.0 line):
#   - FFmpeg as static libavcodec/libswresample/libavutil, with only the
#     decoders DTS and TrueHD need: dca (DTS, DTS-HD core), truehd and mlp;
#   - Media3's JNI wrapper, android/core/ffmpeg/src/main/jni/ffmpeg_jni.cc,
#     linked against them into one shared library, 16 KB page aligned.
#
# Licensing: FFmpeg is configured without --enable-gpl, --enable-version3 or
# --enable-nonfree, so the result is LGPL-2.1-or-later.
#
# Requires ANDROID_NDK_HOME (tested with NDK 28.2.13676358), curl, make.
set -euo pipefail

FFMPEG_VERSION="6.0.1"
FFMPEG_SHA256="9b16b8731d78e596b4be0d720428ca42df642bb2d78342881ff7f5bc29fc9623"
ENABLED_DECODERS="dca truehd mlp"
ABIS="arm64-v8a armeabi-v7a x86_64 x86"
# Must not exceed the app's minSdk.
API=24

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$root/android/core/ffmpeg/src/main/jniLibs}"
JNI_SRC="$root/android/core/ffmpeg/src/main/jni/ffmpeg_jni.cc"
WORK="$root/target/android-ffmpeg"

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the NDK root}"
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -d "$TOOLCHAIN" ]] || { echo "no NDK toolchain at $TOOLCHAIN" >&2; exit 1; }
JOBS="$(nproc 2>/dev/null || echo 4)"

mkdir -p "$WORK"
tarball="$WORK/ffmpeg-$FFMPEG_VERSION.tar.xz"
src="$WORK/ffmpeg-$FFMPEG_VERSION"
if [[ ! -f "$tarball" ]]; then
  curl -sSfL -o "$tarball" "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz"
fi
echo "$FFMPEG_SHA256  $tarball" | sha256sum -c -
if [[ ! -d "$src" ]]; then
  tar -xf "$tarball" -C "$WORK"
fi

common_options=(
  --target-os=android
  --enable-static
  --disable-shared
  --disable-doc
  --disable-programs
  --disable-everything
  --disable-avdevice
  --disable-avformat
  --disable-swscale
  --disable-postproc
  --disable-avfilter
  --disable-symver
  --enable-swresample
  --extra-ldexeflags=-pie
  --disable-v4l2-m2m
  --disable-vulkan
  --enable-pic
  --disable-zlib
  --nm="$TOOLCHAIN/llvm-nm"
  --ar="$TOOLCHAIN/llvm-ar"
  --ranlib="$TOOLCHAIN/llvm-ranlib"
  --strip="$TOOLCHAIN/llvm-strip"
)
for decoder in $ENABLED_DECODERS; do
  common_options+=("--enable-decoder=$decoder")
done

for abi in $ABIS; do
  case "$abi" in
    arm64-v8a)   triple=aarch64-linux-android; abi_options=(--arch=aarch64 --cpu=armv8-a) ;;
    armeabi-v7a) triple=armv7a-linux-androideabi
                 abi_options=(--arch=arm --cpu=armv7-a "--extra-cflags=-march=armv7-a -mfloat-abi=softfp" "--extra-ldflags=-Wl,--fix-cortex-a8") ;;
    x86_64)      triple=x86_64-linux-android; abi_options=(--arch=x86_64 --cpu=x86-64 --disable-asm) ;;
    x86)         triple=i686-linux-android; abi_options=(--arch=x86 --cpu=i686 --disable-asm) ;;
  esac
  build="$WORK/build/$abi"
  install="$WORK/install/$abi"
  rm -rf "$build" "$install"
  mkdir -p "$build"
  (
    cd "$build"
    "$src/configure" \
      --prefix="$install" \
      --cross-prefix="$TOOLCHAIN/$triple$API-" \
      "${abi_options[@]}" \
      "${common_options[@]}"
    make -j"$JOBS"
    make install-libs install-headers
  )

  mkdir -p "$OUT/$abi"
  so="$OUT/$abi/libffmpegJNI.so"
  # -Bsymbolic is what Media3's CMakeLists adds for arm64 (NDK 23.1+); it is
  # harmless elsewhere. The C++ runtime is linked statically, as CMake's
  # default c++_static would, so no libc++_shared.so has to ship beside it.
  # --exclude-libs keeps FFmpeg's own symbols out of the dynamic table, so
  # only the JNI entry points are exported and --gc-sections can drop what
  # they never reach.
  "$TOOLCHAIN/$triple$API-clang++" \
    -shared -fPIC -O2 -std=c++11 \
    -I"$install/include" \
    "$JNI_SRC" \
    -L"$install/lib" -lswresample -lavcodec -lavutil \
    -landroid -llog -lm \
    -static-libstdc++ \
    -Wl,--no-undefined -Wl,-Bsymbolic \
    -Wl,--exclude-libs,ALL -Wl,--gc-sections \
    -Wl,-z,max-page-size=16384 \
    -o "$so"
  "$TOOLCHAIN/llvm-strip" --strip-unneeded "$so"

  # Android 15+ devices with 16 KB pages refuse a library whose LOAD segments
  # are aligned to less.
  if "$TOOLCHAIN/llvm-readelf" -lW "$so" | awk '$1 == "LOAD" { print $NF }' | grep -qv '^0x4000$'; then
    echo "$so is not 16 KB aligned" >&2
    exit 1
  fi
  echo "built $so ($(stat -c %s "$so") bytes)"
done
