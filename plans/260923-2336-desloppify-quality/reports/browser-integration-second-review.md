# Browser document and subtitle integration coverage

Status: DONE

Added seven integration cases without changing application behavior, dependencies, or manifests. The controller smoke test obtains the shipped document through production `staticResponse(GET /)`, verifies the HTML response, parses it with Bun's HTMLRewriter, then mounts a bundle of the actual application and player against controlled browser IO.

## Coverage

- The new strict HTML fixture preserves the document's elements, IDs, tags, attributes, hierarchy, and selector lookups. Missing IDs/classes return null; controllers cannot silently obtain invented controls. The existing lightweight fixtures remain available for deterministic lifecycle cases.
- The application smoke exercises real film-page navigation/open, player dialog/video containment, play/pause label, skip, mute, subtitle appearance, and close/detach. It verifies the served document references `/app.js`.
- Three negative cases rename required controls (`play-pause`, `sub-track`, `close`) in the served HTML fixture. Actual controller initialization rejects each mutated document.
- Eventful TextTrackList and track elements replace the shared fixture's no-op track listener. Tracks enter and leave with DOM attachment; mode changes emit change; tests control cue arrival through load events. These are browser IO boundaries; player, transport, preference store, subtitle panel, and cue-placement functions run unmodified.
- Subtitle cases cover remembered German after track reordering, explicit Off across episodes, absence of default tracks, late cue load and separate track-list change, idempotent offset placement, panel nudge/reset persistence, converted seek restart, newly loaded cues, next-episode track replacement, panel closure, and final track removal.
- A conversion seek retains existing `<track>` children while replacing the media source; the fixture asserts that actual current behavior. Opening another episode replaces the tracks and reapplies remembered language/offset. No production subtitle defect was found.

## Verification

`/tmp/browser-integration-second-focused.log`: **177 passed, 0 failed, 885 assertions, 14 files**. This includes the seven new cases and existing application, player lifetime/features/initialization, HLS lifetime, transport, keys, seek, adaptation, resume, subtitle helper, up-next, and served module graph suites.

Mutation checks ran in `/tmp/mediagram-subtitle-mutations-2r0ssx33`, an isolated copy of authored source and narrow test/support files. Original production files were never mutated:

| Isolated mutation | Result | Evidence |
| --- | --- | --- |
| Remove track load listener | 5 passed, 2 failed | `/tmp/browser-subtitle-mutation-load.log` |
| Remove TextTrackList change listener | 6 passed, 1 failed | `/tmp/browser-subtitle-mutation-change.log` |
| Ignore remembered subtitle preference | 5 passed, 2 failed | `/tmp/browser-subtitle-mutation-preference.log` |
| Restore production source | 7 passed, 0 failed | `/tmp/browser-subtitle-mutation-restored.log` |

Scoped diff whitespace validation passed. The temporary application bundles clean themselves up; all test processes exited and no HTTP server, native decoder, or live network service was started.

## Integration notes

Root owns the subsequent playback file moves, independent review, and whole-web Bun/TypeScript gate after concurrent edits settle. The new support code uses the project's EventTarget listener type rather than adding DOM libraries globally.

These tests verify served markup/controller wiring and subtitle orchestration through strict, controlled browser boundaries. They do not claim native WebVTT rendering or hardware-decoder coverage. The isolated parser models the selectors used by this application, not a complete browser DOM.

A requested lookup with the older graph-patched scanner did not understand the current `work_items` schema and updated its ignored query artifact. No assessment state, plan, scores, or snapshot source was changed; findings were subsequently read directly from the supplied state JSON. Further scanner operations remain with root.
