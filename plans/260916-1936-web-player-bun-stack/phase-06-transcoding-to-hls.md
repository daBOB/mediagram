---
phase: 6
title: "Transcoding to HLS"
status: pending
priority: P2
effort: "2d"
dependencies: [4, 5]
---

# Phase 6: Transcoding to HLS

## Overview
Play what browsers refuse. ffmpeg reads from the Range server, transcodes to
H.264 and AAC, and writes HLS segments that Bun serves. Also the bitrate fix:
a 13.9 Mbit/s source does not fit a 25 Mbit/s uplink with headroom to spare.

## Key insight
Two things make or break this, and both are properties of the Range server
from phase 1 rather than of ffmpeg:

1. **`Content-Length` must be present on the initial response.** ffmpeg cannot
   seek an HTTP source without it, and without seeking it either fails or
   buffers from byte zero. Phase 2's routes must always send it, as phase 1's
   do.
2. **Keyframes must align with segment boundaries**, or seeking within a
   transcode lands mid-segment and stalls.

## Corrections to the research report
Two errors in
`plans/reports/researcher-260916-1940-web-player-streaming-and-transcode-report.md`
that would have gone straight into the code:

- **`-g 2` is wrong.** `-g` counts **frames**, not seconds. At 23.976 fps a
  2-second GOP is `-g 48`. Taken literally, `-g 2` makes nearly every frame a
  keyframe and the bitrate explodes, which is the opposite of the goal. Use
  `-g 48` together with
  `-force_key_frames "expr:gte(t,n_forced*2)"` so alignment holds even when
  the frame rate is not what we assumed.
- **"`-ss 30` sends `Range: bytes=30000-`" is wrong.** Seconds are not bytes.
  ffmpeg parses the container to find the byte offset for that timestamp, then
  issues a Range for it. The practical consequence is the same — the server
  must support Range and report length — but the mechanism matters when
  debugging why a seek landed in the wrong place.

## Requirements
- Functional: a set the browser cannot direct-play starts within a few
  seconds and is seekable; output bitrate stays under the uplink; sessions are
  cleaned up when the viewer leaves.
- Non-functional: no more than one ffmpeg per viewer; a hung ffmpeg is killed
  rather than accumulating; NVENC absence falls back to CPU rather than
  failing.

## Architecture
```
browser ──/hls/:session/index.m3u8──> Bun ──spawns──> ffmpeg
                                        │                │ reads Range
                                        │                ▼
                                        │        Bun's own /api/sets/:id/stream
                                        └── serves segments from <work>/:session/
```

Baseline command, with the corrections applied:

```
ffmpeg -hwaccel cuda
       -ss <seek-seconds>            # before -i: fast, keyframe-approximate
       -i http://127.0.0.1:<port>/api/sets/<id>/stream
       -c:v h264_nvenc -rc vbr -cq 26 -maxrate 8M -bufsize 16M
       -g 48 -force_key_frames "expr:gte(t,n_forced*2)"
       -c:a aac -b:a 160k -ac 2
       -hls_time 2 -hls_list_size 0 -hls_playlist_type event
       <work>/<session>/index.m3u8
```

`-maxrate 8M` is the uplink guard: comfortably under 25 Mbit/s with room for
everything else. `playlist_type event` rather than `vod` because the playlist
grows while encoding; `vod` is for a finished file.

`-ac 2` downmixes 5.1 to stereo deliberately. Without it, an AC3 5.1 source
can produce an AAC stream whose centre channel dominates and dialogue sounds
wrong on stereo speakers.

## Related Code Files
- Create: `web/src/transcode/session.ts` (spawn, supervise, clean up),
  `web/src/transcode/args.ts` (pure: build the argument list),
  `web/src/transcode/registry.ts` (sessions by id, one per viewer)
- Modify: `web/src/server.ts` (routes for playlist and segments),
  `web/public/app.js` (hls.js when not Safari), `web/src/playable.ts`

## Implementation Steps
1. `args.ts`, pure: from codecs, seek offset and a bitrate cap, produce the
   argument list. Tested, because this is where the flags above live and
   getting `-g` wrong is silent.
2. Startup capability check: does `h264_nvenc` exist? Fall back to `libx264`
   with a warning rather than failing at first play.
3. Session registry: one ffmpeg per viewer, keyed by session id, with the work
   directory under a configured path.
4. Routes: `/hls/:session/index.m3u8` and `/hls/:session/:segment`, served as
   static files once ffmpeg has written them.
5. Wait for the first segment before returning the playlist, so the player
   does not get a playlist with nothing in it.
6. Seeking: a seek outside the buffered range restarts ffmpeg at the new
   offset with a fresh session. Accept the restart in v1.
7. Supervision: kill on client disconnect, on idle timeout, and on process
   exit; never leave an orphan holding an NVENC session.
8. Player: hls.js for Chrome and Firefox, native HLS on Safari.

## Success Criteria
- [ ] A film plays in Chrome, having been refused before
- [ ] Startup to first frame is under about six seconds
- [ ] Output bitrate stays under the cap, measured, not assumed
- [ ] Seeking works, including backwards
- [ ] Closing the tab leaves no ffmpeg process
- [ ] Two sequential plays do not leak NVENC sessions
- [ ] With NVENC unavailable, playback still works via CPU encode
- [ ] Dialogue is audible in a stereo downmix of a 5.1 source
- [ ] `args.ts` tests pin `-g`, the keyframe expression and the bitrate cap

## Risk Assessment
- **NVENC session exhaustion.** Consumer cards allow a small number of
  concurrent sessions and an orphaned ffmpeg holds one indefinitely.
  Supervision is the mitigation, and the leak test is a success criterion.
- **Seek restarts feel slow.** Each seek outside the buffer restarts encoding.
  Acceptable for one viewer; revisit only if it grates.
- **Transcoding masks Range bugs.** ffmpeg is tolerant and may paper over a
  server that mishandles ranges. The phase 1 and 2 correctness tests stay the
  source of truth, and must keep passing without ffmpeg in the picture.
- **Disk churn from segments.** Segments accumulate per session. Clean the
  work directory on session end, and on startup for sessions that died.

## Open questions
1. Bitrate cap value: 8 Mbit/s is a starting point, not a measurement. Tune
   once a real remote play is observed.
2. Whether hardware decode (`-hwaccel cuda`) helps at all here, given the
   bottleneck is likely the network rather than decode. Measure before keeping
   the flag.
