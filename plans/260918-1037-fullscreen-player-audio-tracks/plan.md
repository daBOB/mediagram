# Fullscreen player, preload-without-play, end time, audio track chooser

Four asks against `web/public` and the transcode path. Three are contained;
the fourth needed a decision because the index cannot answer it.

## Decisions taken (user, 2026-09-18)

1. **Track source: ffprobe on demand.** `sets.alang` exists but
   `collect_langs` (`crates/mediagram/src/media/inspect.rs:114`) keeps
   *distinct* codes and drops untagged streams, so `alang[i]` is not
   ffmpeg's `0:a:i`. Mapping it would silently play the commentary. The
   player probes the set's own Range URL instead and caches the answer.
2. **A non-default track on a direct-play title converts**, and says so
   through the existing note line. Chrome and Firefox do not implement
   `HTMLMediaElement.audioTracks`, so there is no other way to honour it.
3. **Fullscreen means a HUD**: video edge to edge, furniture floating over
   it, fading when the pointer rests.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Audio track enumeration](phase-01-audio-track-enumeration.md) | done |
| 2 | [Transcode a chosen track](phase-02-transcode-a-chosen-track.md) | done |
| 3 | [End time](phase-03-end-time.md) | done |
| 4 | [Fullscreen HUD](phase-04-fullscreen-hud.md) | done |

## Dependencies

- Phase 2 needs Phase 1's audio-relative index to have a meaning.
- Phase 4 needs 1–3 for the things it puts on screen.
- Requires `ffprobe` on the player host. `ffmpeg` is already required, and
  the two ship together.

## Constraints

- The player opens the index **read-only** and must not migrate it. No
  schema change: `EXPECTED_SCHEMA` stays 4.
- `-map 0:a:N` stays a single explicit stream. The comment in
  `transcode/args.ts` explains why letting ffmpeg pick "best" is wrong, and
  that reasoning is unchanged — the viewer now picks instead of track 0.
- Session identity already covers `(setId, seek, maxrate)`; the chosen track
  joins it, or two viewers on different languages would share one encode.

## Review

See `reports/` for the verification run.
