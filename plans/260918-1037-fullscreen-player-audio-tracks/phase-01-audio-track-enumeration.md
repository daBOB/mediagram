# Phase 1 — Audio track enumeration

**Priority:** first, phase 2 depends on it. **Status:** done.

## Why not the index

`sets.alang` is a JSON array of *distinct* language codes with untagged
streams dropped. A file of `[und, en, en-commentary, de]` stores
`["en","de"]`, so position in `alang` is not the ffmpeg stream ordinal.
Nothing in the index records the ordinal, and adding it means a schema
version the player is forbidden to migrate.

## Design

`web/src/audio-tracks.ts`, one exported async function plus a pure parser.

- `parseAudioTracks(json)` — pure, takes ffprobe's `-show_streams` output and
  returns `AudioTrack[]`. Position in the returned array **is** the `0:a:N`
  ordinal, because `-select_streams a` yields audio streams in order.
- `AudioTrackReader` — runs ffprobe against
  `<baseUrl>/api/sets/<id>/stream`, caches per set id, never throws.

Bounded: `-probesize`/`-analyzeduration` capped and a hard timeout, because
the bytes come from Telegram through the Range server.

## Route

`GET /api/sets/{id}/audio` → `{ tracks: AudioTrack[] }`. `404` for a set the
catalog will not play. A probe that fails answers `{ tracks: [] }` — a
chooser that cannot be built is not an error, it is a title with one track.

## Files

- create `web/src/audio-tracks.ts`
- modify `web/src/routes.ts` (route + wiring), `web/src/index.ts` (construct)
- create `web/test/audio-tracks.test.ts`

## Success criteria

- A stream list with an untagged first track maps `de` to its real ordinal.
- ffprobe missing, timing out, or emitting junk yields `[]`, never a throw.
- Second call for the same set does not spawn a second ffprobe.
