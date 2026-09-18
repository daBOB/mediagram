# Phase 2 — Transcode a chosen track

**Priority:** after phase 1. **Status:** done.

## Design

The chosen track travels the same road the seek position and the bitrate cap
already travel, because it has the same property: it identifies *which*
encode, so two viewers wanting different languages must not join one session.

- `TranscodeRequest.audioTrack: number` → `-map 0:a:${audioTrack}`.
- `Runner.start(...)` gains the ordinal; `FfmpegRunner` passes it through.
- `registry` adds it to the session key and to `Session`.
- `PlayerRequest.audio` ← `?audio=`, floored, clamped at 0, default 0.
- `hls-playback.js` sends `&audio=` when the ordinal is not 0.

## Why not multi-rendition HLS

`var_stream_map` with every audio track would encode all of them at once —
several times the CPU for tracks nobody plays. Restarting on a switch costs
one interruption, which is what a seek already costs, and the player keeps
the viewer's place across it exactly as the bitrate switch does.

## Files

- modify `web/src/transcode/args.ts`, `ffmpeg.ts`, `registry.ts`
- modify `web/src/routes.ts`, `web/src/server.ts`
- modify `web/public/lib/hls-playback.js`
- modify `web/test/transcode-args.test.ts`, `transcode-registry.test.ts`

## Success criteria

- `-map 0:a:2` appears for track 2 and `0:a:0` stays the default.
- Two sessions differing only in track do not share a directory.
- An absent or junk `?audio=` is track 0, never `-map 0:a:NaN`.
