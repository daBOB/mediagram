---
phase: 3
title: "Media inspect and faststart remux"
status: pending
priority: P2
effort: "0.5d"
dependencies: [1]
---

# Phase 3: Media inspect and faststart remux

## Overview
Fill caption technical fields from ffprobe and ensure MP4 sources have `moov` at the front before splitting.

## Requirements
- Functional: `inspect(path) -> MediaInfo { container, duration_s, vcodec, acodec, quality, hdr, alang, slang, size }`; `ensure_faststart(path) -> PathBuf` returning original or remuxed temp path; `--no-remux` bypass.
- Non-functional: subprocess only (no ffmpeg bindings); temp file in `config.tmp_dir` (default: alongside source); temp removed after upload completes (phase 5 owns deletion).

## Architecture
```
crates/mediagram/src/media/
  inspect.rs     # runs `ffprobe -v error -print_format json -show_format -show_streams`, parses with serde
  classify.rs    # height→quality (2160p/1440p/1080p/720p/480p/SD), hdr from side_data_list (DOVI→"DV") / color_transfer (smpte2084→"HDR10", arib-std-b67→"HLG") else "SDR"; language tags from stream tags.language (ISO 639-2 → 639-1 map for common codes)
  mp4_atoms.rs   # reads top-level atom headers; returns true if `mdat` precedes `moov` (needs remux); handles 64-bit sizes
  remux.rs       # `ffmpeg -v error -i in -c copy -movflags +faststart -y out.tmp.mp4`
```

## Related Code Files
- Create: files above; `crates/mediagram/tests/media_inspect.rs` (generates fixtures with `ffmpeg -f lavfi -i testsrc=duration=1:size=64x64 ...`, skipped if ffmpeg missing)
- Modify: `Cargo.toml` deps: serde_json, tempfile

## Implementation Steps
1. Implement `inspect.rs` with a minimal serde model (format.duration, format.size, streams[].codec_type/codec_name/height/tags.language/color_transfer/side_data_list[].side_data_type).
2. Implement `classify.rs` pure functions with unit tests.
3. Implement `mp4_atoms.rs` reading atoms until `moov` or `mdat`; treat `.mkv/.webm` as no-op.
4. Implement `remux.rs`; verify output with `mp4_atoms` afterwards; return error if still not faststart.
5. Fixture tests: build a 1-second MP4 with moov at end (`-movflags -faststart` default) → detect → remux → detect false.

## Success Criteria
- [ ] `inspect` on an HEVC/DV MKV fixture yields `hdr = "DV"`, correct language arrays
- [ ] Trailing-moov MP4 is detected and remuxed; faststart MP4 is left untouched
- [ ] MKV path never invokes ffmpeg

## Risk Assessment
- Language tags missing on streams → arrays empty, not an error; `add --alang en,de` override flag.
- HDR detection heuristics vary by encoder → allow `--hdr` override; store whatever ffprobe says in `variant` notes if unknown.
