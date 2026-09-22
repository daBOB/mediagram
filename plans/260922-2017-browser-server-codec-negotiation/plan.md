# Browser–server codec negotiation (HEVC)

Status: done · 2026-09-22

## Why

Every HEVC title is re-encoded to H.264 because `playable.js` holds one
static list of "codecs that play everywhere". Firefox 156 on this machine
decodes HEVC (`MediaSource.isTypeSupported('video/mp4; codecs="hvc1…"')`
and `canPlayType` both true), so for it that encode is wasted GPU time and a
second-generation picture. A WebAssembly decoder was rejected: it cannot feed
MSE, decodes on CPU only, and replaces an encode that already scores SSIM
0.988 — the win is in not encoding at all.

## Design

- **Browser probes once** (`public/lib/codec-support.js`): a codec counts only
  if both `canPlayType` (direct play) and `MediaSource.isTypeSupported` (HLS
  via hls.js) accept it. Today the table has one entry, HEVC as `hvc1`.
- **One policy, widened per browser**: `decidePlayback(profile, link)` takes
  `link.decodes`; `link.js` fills it, so shelf badges and the player agree.
  An mp4 holding HEVC now plays directly in a browser that decodes it.
- **Request says what it decodes**: `/transcode?vcodecs=hevc`. The server
  keeps only names it knows (`NEGOTIABLE`), so a client cannot talk it into
  copying anything else; `canCopyVideo` reads the same `decodes`.
- **HEVC copies go out as fMP4**: hls.js plays HEVC only from fMP4, and
  Safari/Chrome want the `hvc1` tag. H.264 copies and encodes stay MPEG-TS —
  nothing that works today changes container. `init.mp4` becomes a servable
  HLS file.

Spike (verified): `ffmpeg … -c:v copy -tag:v hvc1 -hls_segment_type fmp4`
repackaged 30 s of an HEVC mkv in 0.35 s; init segment reads `hevc/hvc1`.

## Steps

- [x] codec-support probe + `link.decodes` + `decidePlayback` widening
- [x] `vcodecs` on the transcode request, whitelisted server-side
- [x] `canCopyVideo` honours `decodes`; args emit fMP4 + `hvc1` for HEVC copies
- [x] serve `init.mp4`; tests for each piece
- [x] verify in headless Firefox against the offline stub (real HEVC title)
- [x] docs table (`docs/running-the-player.md`), changelog, version bump

## Parity

Android plays through ExoPlayer, which asks the device's decoders itself;
whether it already sends HEVC through untouched is to be checked there, not
assumed from this.
