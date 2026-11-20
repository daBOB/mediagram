# Phase 05 — Web: native MP3 playback, cover, lock screen

**Context:** spec § Web player, user decisions "everything should handle mp3 native" and iPhone listening.
**Status:** todo. **Depends on:** Phase 04.

## Requirements

- `decidePlayback` plays an MP3 book directly: `mp3` is a direct-play container, and a profile with **no** video codec (`vcodec` null or empty) has no picture to judge, so it never adds "unknown video". A file with a video codec the policy does not know still transcodes, as today.
- The server serves `container: "mp3"` as `audio/mpeg`.
- The player stage shows the book's cover in place of a picture (the `<video>` element's `poster`, shown whole), cleared when anything else opens or the player stops.
- Lock screen / OS media controls: when any title opens, `navigator.mediaSession.metadata` carries its title line and, for a book, its cover as artwork; action handlers for `play`, `pause`, `seekbackward`, `seekforward`, `seekto` act on the same element the transport drives. Cleared on stop.
- Nothing in warm-up or the film leader waits for a video frame an audio-only file never paints.
- iPhone: a book keeps playing in Safari with the screen locked. Checked on a real iOS device by the user (Phase 09). If it pauses on lock, the follow-up is routing books through an `<audio>` element; that is a separate change, not part of this phase.

## Files

- Modify: `web/public/lib/playable.js:24` (`CONTAINERS` gains `"mp3"`), `:117-120` (judge the picture only when there is a video codec)
- Modify: `web/src/response.ts:66` `contentType` (`case "mp3": return "audio/mpeg";`)
- Modify: `docs/running-the-player.md:151-155` (Container row gains `mp3`; a line that audio-only titles play direct) — held to `playable.js` by `web/test/codec-policy-doc.test.ts`
- Modify: `web/public/lib/playback/player.js` (`openPlayer` ~:634-672 sets `video.poster`; `stop()` ~:206 clears it; `:421` mediaSession block grows `metadata` + seek handlers)
- Create (if `player.js` passes 200 lines of new code): `web/public/lib/playback/media-session.js` — `export function showOnLockScreen(set, video, titleLine)` and `export function clearLockScreen()`
- Check: `web/public/lib/film-leader.js`, `web/public/lib/playback/stage-warmup.js` for first-frame waits
- Test: `web/test/playable.test.ts`, `web/test/codec-policy-doc.test.ts`, a response content-type test, `web/test/media-session.test.ts` (with a fake `navigator.mediaSession`)

## Interfaces

- Consumes: `CatalogSet.kind`, `.poster` (Phase 04 library).
- Produces: `showOnLockScreen(set, video, titleLine)`, `clearLockScreen()`.

## Steps

- [ ] **1: Failing tests**
  - `playable.test.ts` `"an MP3 book plays directly"`: `decidePlayback({ container: "mp3", vcodec: null, acodec: "mp3", total: 30_993_477, duration: 3863 })` → `{ kind: "direct" }`; remote at `maxBitrate: 4e6` → still direct.
  - `"an mp4 with no codecs still converts"` keeps the existing :114 case green (null audio).
  - `"an unknown video still converts"`: `{ container: "mp4", vcodec: "mpeg2video", acodec: "aac" }` → transcode with reason `"mpeg2video video"`.
  - content type: `contentType("mp3") === "audio/mpeg"`.
  - `media-session.test.ts`: `showOnLockScreen(book with poster "/art/x.jpg", video, "Die unendliche Geschichte")` sets `metadata.title === "Die unendliche Geschichte"`, `metadata.artwork[0].src === "/art/x.jpg"`, and registers handlers for all five actions; `seekto({ seekTime: 120 })` sets `video.currentTime` to 120; `clearLockScreen()` sets `metadata` to `null`. With no `navigator.mediaSession` both are no-ops.
- [ ] **2: Run, expect failures:** `cd web && bun test test/playable.test.ts test/codec-policy-doc.test.ts test/media-session.test.ts`.
- [ ] **3: Implement** per Files; update the doc table until `codec-policy-doc.test.ts` passes.
- [ ] **4: Web checks** green.
- [ ] **5: Look at it** in the stub harness: open a book → cover whole on the stage, transport moves, seek works, no "Converting" note; open a film after it → no cover left behind.
- [ ] **6: Commit** `feat(web): play MP3 books as they are, with the cover on the stage and the lock screen`.

## Success criteria

A book opens with no conversion note; the stage shows its cover; the media-session test passes.

## Risks

`player.js` holds the one `<video>` element and hands it to 13 modules; nothing here changes which element plays.
