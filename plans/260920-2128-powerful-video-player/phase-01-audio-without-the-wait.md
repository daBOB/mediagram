# Phase 01 — Audio without the wait

**Status:** done — measured at 116x realtime against 15.2x

## Context

- `web/src/transcode/args.ts:75` — `-map 0:v:0 -map 0:a:N`, and a `-c:v` that
  is always an encoder
- `web/src/transcode/registry.ts` — `sessionId(setId, seek, maxrate, audioTrack)`
- `web/public/lib/player.js` — the `audioTrackPicker` change listener, whose
  comment already explains why this is a conversion
- `web/src/audio-tracks.ts` — why the index cannot answer this

## The waste

312 of 566 sets carry more than one language. Picking one runs the file
through `h264_vaapi` from end to end, because the only way this player has of
choosing an audio stream is to build a new video alongside it.

For the 215 sets the browser plays natively that is pure loss: the video was
already acceptable, and it is re-encoded to a bitrate cap, at a quality cost,
on a GPU, so that a different audio stream can sit next to it.

## The change

A conversion that is only about audio copies the video.

```
-map 0:v:0 -map 0:a:N -c:v copy -c:a aac -b:a 192k
```

`-c:v copy` moves bytes. No decode, no encode, no quality loss, and it runs
far faster than realtime, so the wait becomes the container muxing rather than
the picture.

**When it applies.** Only when the video stream is already something the
browser accepts and nothing else forces a re-encode:

- the codec is one `playbackFor` calls direct (h264), and
- no bitrate cap is in force — a cap exists because the link cannot carry the
  original, and copying the original ignores the thing the cap was for.

Otherwise the existing path runs unchanged. The decision is one function, and
it is the phase's only real logic.

## Also here

- **A stereo downmix that is not quiet.** A 5.1 track folded to stereo by
  ffmpeg's default loses the centre channel's level, which is where dialogue
  lives. `-af aclutter`-style guesswork is not the answer; an explicit
  downmix matrix is.
- **The registry key gains the copy decision**, or a copied session and an
  encoded one at the same position collide and the viewer gets whichever
  started first.

## Files

**Modify** `web/src/transcode/args.ts`, `web/src/transcode/registry.ts`,
`web/src/routes.ts` (the request carries whether a copy is allowed),
`web/public/lib/player.js` (the note the viewer is shown while it happens)

**Create** `web/src/transcode/video-copy.ts` + `web/test/video-copy.test.ts`

## Todo

- [ ] `canCopyVideo({ vcodec, container, capBits })`, pure, tested first
- [ ] `args.ts` emits the copy form when it applies
- [ ] the session id distinguishes a copy from an encode
- [ ] the stereo downmix, with the matrix written down and why
- [ ] the note says "changing language", not "converting as you watch" —
      the existing wording describes a thing that is no longer happening
- [ ] measure it: seconds-to-first-frame, copy vs encode, on a real set
- [ ] `./scripts/check.sh`

## Success criteria

- Switching language on an h264 set starts in a fraction of the time and the
  picture is bit-identical to the direct stream.
- Switching language on an HEVC set behaves exactly as it does today.
- A set under a bitrate cap still re-encodes.
- Two viewers on the same set at the same second, one copying and one not, get
  their own session.

## Risks

| Risk | Mitigation |
|---|---|
| A copied stream the browser then refuses | `canCopyVideo` only says yes to what `playbackFor` already calls direct |
| Copy chosen while a cap is in force | The cap is an input to the decision, and a test says so |
| fMP4 segmenting a copied stream needs keyframes it does not control | Measured on a real set before this is called done; falls back to encoding if the segmenter complains |
