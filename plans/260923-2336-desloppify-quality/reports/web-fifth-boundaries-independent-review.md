# Browser playback and server failure boundaries independent review

Status: DONE — PASS

## Conclusion

No actionable defect found in the reviewed delta. Subtitle toggling now restores
the last selected track within a title, the type/documentation additions match
their actual contracts, and shared failure formatting preserves the existing
fallback, retry and cleanup order.

## Browser playback

- `public/lib/playback/transport.js:281–307` records the current non-Off index
  when applying a selection. Turning subtitles off leaves that remembered
  index intact; turning them back on uses it instead of hard-coded zero.
- `transport.js:351–387` resets that index before rebuilding each title's
  picker, then applies the existing remembered language/Off policy. The
  production `player.js:584–592` attaches the new title's tracks before this
  rebuild. A remembered language therefore establishes the new title's correct
  ordinal, while remembered Off uses that title's first track when re-enabled.
  Keyboard toggling does not change the existing preference-persistence policy.
- The added served-HTML application test selects index 2, toggles off/on, uses
  explicit Off, then changes to a two-track title and verifies repeated toggling.
  This catches both the original first-track reset and a stale ordinal crossing
  titles. Existing tests cover remembered-language reordering and conversion
  seeks without replacing subtitle elements.
- `public/lib/playback/audio-chooser.js` imports the real server `AudioTrack`
  contract from `src/catalog/audio-tracks.ts` in JSDoc. Required nullable fields
  and stream ordinals match the producer; the two fixture additions supply the
  actual nullable `title` field. No runtime import or parallel contract was added.
- `public/lib/playback/streaming/hls-playback.js:37–63` truthfully documents
  `beginTranscode` as returning `{ playlist, copied }`. Its implementation checks
  the playlist string and normalizes `copied` to a boolean, matching the new
  return annotation and existing server response.

## Server failure handling

- `StateSync.round` now uses the shared non-throwing formatter. The coalesced
  drain still retains queued news; committed import counts survive publication
  failure, and failed sends do not update `lastSent`. The expanded tests assert
  real SQLite progress import, published contents, all waiting callers' outcomes
  and a later successful/no-op round across arbitrary rejection values.
- `CatalogFollower.replace` still closes the rejected candidate database before
  reporting failure and leaves the served database, serving timestamp, status,
  cache expectations and events unchanged. Its retry test uses the real local
  HTTP listener and a real installed snapshot, confirms only one download,
  checks the failed handle is closed, then succeeds at the same timestamp.
- Poster spawn failures now return useful `PosterFetch` reasons for Error,
  null, undefined, string and unprintable values. The spy controls only the
  throwing spawn boundary, is restored in `finally`, and a real temporary
  executable succeeds afterward.
- `installChannelIndex` keeps validation, atomic pointer publication and cleanup
  in the same order. Its private validation/count helper retains original causes
  and closes SQLite in `finally`; only its descriptive name and formatter
  changed. Partial-download regressions verify old bytes/timestamp/current
  remain intact, staging disappears, and the same newer timestamp can install.
- `WatchState.open` retains its existing null fallback. Reusing `failureMessage`
  removes the unsafe conversion without adding an artificial database seam.
  This defensive startup change is supported by existing storage-failure tests,
  not claimed as a newly reproduced SQLite rejection.

## Evidence and limits

Read existing evidence; no test execution was needed for a new concern:

- `/tmp/browser-subtitle-toggle-red.log`: the new subtitle test failed by
  selecting English instead of the previously chosen German track.
- `/tmp/browser-third-playback-green.log`: **65 pass, 0 fail, 232 assertions**
  across five files. Matching typecheck and browser lint logs are clean.
- `/tmp/web-remaining-failure-red.log`: **45 pass, 11 fail**, matching the unsafe
  conversion/diagnostic cases. Final focused gate: **56 pass, 303 assertions**.
- `/tmp/web-remaining-failure-scoped.log`: **281 pass, 0 fail, 833 assertions**
  across 21 state/channel/catalog/startup/lifecycle files. The corresponding
  TypeScript log is clean. Scoped whitespace validation is also clean.

The browser test mounts shipped HTML and production modules with controlled DOM
and media boundaries; it does not establish real-browser codec playback. Server
tests use temporary files/SQLite and controlled IO, not live Telegram. Concurrent
app/watch-state changes, other media/shutdown work and whole-suite integration
are outside this review. This report is the sole written artifact; no production,
test, git, scanner or process state was changed. Concerns: none within scope.
