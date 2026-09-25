# FFmpeg shipped in the app

`libffmpegJNI.so`, in every ABI of the APK, statically contains parts of
**FFmpeg 6.0.1** (libavcodec, libswresample, libavutil), licensed under the
GNU Lesser General Public License 2.1 or later — the text is
`LGPL-2.1-ffmpeg.txt` beside this file. It is built without `--enable-gpl`,
`--enable-version3` or `--enable-nonfree`, and with only three decoders:
`dca` (DTS, and the DTS core of DTS-HD), `truehd` and `mlp`.

Source: <https://ffmpeg.org/releases/ffmpeg-6.0.1.tar.xz>, unmodified. The
exact configure flags and link step are in `scripts/build-android-ffmpeg.sh`,
which rebuilds the library from that tarball; a relinked library dropped into
`src/main/jniLibs/<abi>/` is what the app loads.

The Java sources under `src/main/java` and `src/main/jni/ffmpeg_jni.cc` are
Media3's `libraries/decoder_ffmpeg` at tag `1.10.1`, unmodified, under the
Apache License 2.0 like the rest of Media3.
