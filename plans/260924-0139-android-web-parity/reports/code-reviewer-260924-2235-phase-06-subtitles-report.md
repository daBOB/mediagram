# Phase 06 subtitles — code review

Scope: uncommitted work in `feat/android-web-parity` (19 untracked + 21 modified files).
`:core:playback` and `:feature:player` unit tests pass (`testDebugUnitTest`, exit 0).

## Verdict
The core logic is sound and matches the web. I found one real defect: the default subtitle
language is picked once before the profile's remembered choice has loaded, then picked again
after. Everything else is low severity.

## Verified correct (no action)
- **Default rule matches the web.** `chooseSubtitleLanguage` (SubtitleChoice.kt:19) matches
  `transport.js:372-383`: a remembered `off` is kept, a remembered language is matched
  case-insensitively, otherwise the first language is used. Language order matches as well:
  core `catalog_assets.rs:17` and web `assets.ts:47` both use `ORDER BY lang` (BINARY). The
  remembered value is the language itself, not its position in the list, on both surfaces.
- **Offset math.** `shiftedTimes` (ActiveCues.kt:23) is an exact port of `subtitle-style.js:77`:
  the offset is absolute, start is clamped at 0, and end is never before start. A cue is active
  for `[start, end)`, the same as the HTML rule. The sign matches (+ means later), and so do the
  0.1 s rounding and the ±30 s clamp. The stored value (`"0.3"`, `"-1.0"`) is readable by the web
  (`Number()`).
- **VTT tags are shown as plain text.** `Cue.text?.toString()` drops the Spanned styling, the
  parser decodes entities, and Compose `Text(String)` interprets no markup. Bitmap cues
  (null text) are skipped.
- **Text renderer stays disabled.** It is set once in PlayerFactory.kt:75-79. The only other
  place that writes `trackSelectionParameters` (AudioTrackSelection.kt:60,69) uses `buildUpon()`,
  so the disabled text type survives every reload.
- **Phase-05 synthetic empty Tracks.** Not reachable: subtitle state never reads `Tracks`
  (pinned by `subtitleStateIsUntouchedByExoPlayersOwnTracksEvents`).
- **No state carried across titles.** A different title calls `reset()` on both controllers
  (PlayerChoicesController.kt:81-82), which cancels `loadJob` and clears cues. Rotation
  (`sameTitle`) skips the reset and does not re-resolve. A stale load is guarded by cancellation
  (uniffi `setText` is suspend, so it can be cancelled) plus the `language == forLanguage` check.
- **No leaks.** Jobs run in `viewModelScope`. The ticker is a `LaunchedEffect(player, cues)` and
  is torn down with the layer, which returns early when there are no cues. No listeners are
  registered.
- **File size.** Every main source file is ≤200 lines (PlayerViewModel.kt is exactly 200,
  PlayerScreen.kt is 198).

## Findings

### M1 — The default is applied before preferences load, so the track is fetched twice and may flash (Medium)
`SubtitleChoiceController.kt:69-96`, `PlayerChoicesController.kt:116-131`

`onLanguagesKnown` runs `settleIfReady()` before `preferences.load` returns. With
`preferencesLoaded == false` it applies `chooseSubtitleLanguage(languages, null)`, which picks the
first language and **launches a real `setText` + parse**. On `Main.immediate` that call starts
straight away. When preferences do arrive, `applyLanguage` runs a second time and always cancels
and relaunches the job (line 106), even when the language has not changed.

- **Remembered `off`:** the first language's VTT is fetched anyway. If `setText` + parse
  finishes before `core.preferences(profileId)` does, the cues are published. A viewer resuming
  mid-film whose resume point falls inside a cue sees subtitles appear for a series they turned
  off. They show at default size and zero offset, because `subtitleStyle` has not loaded yet
  either. The web never does this: `recall` is synchronous there.
- **Nothing remembered, or the same language remembered (the common path):** every open of a
  subtitled title does `setText` twice. The parse runs on `Dispatchers.Default` and does not
  check for cancellation (`WebvttParser` is blocking), so a ~150 KB film VTT can be parsed twice.
- **Why tests miss it:** `aRememberedOffStaysOffAndLoadsNoCues` asserts "off never fetches a
  track" and passes only because `FakePlayerPreferences.load` never suspends and the test's Main
  dispatcher queues the launch. With the real, suspending `DefaultPlayerPreferences.load`, that
  claim is false.
- **Fix:** don't apply before preferences load. In `settleIfReady`, return unless *both*
  `languagesKnown && preferencesLoaded`. `resolve` always calls `onPreferencesLoaded`: it gets an
  empty map when there is no profile or the load fails, and it only skips the call on the
  superseded-title early return, where a reset follows anyway. Also make `applyLanguage` a no-op
  when the language is unchanged and `loadJob?.isActive == true`. To pin this, give the fake
  preferences a gate so the test really suspends.

### L1 — The two style values are written together, so one can be overwritten with the default (Low)
`SubtitleStyleController.kt:55-60,102-108`

`setSize`/`setBacking` set a single `userChoseAppearance` flag, and `rememberAppearance` writes
**both** `cue-size` and `cue-backing`. Suppose a viewer changes size while `resolve` is still
running. `onPreferencesLoaded` then skips loading the backing too, and writes the default
`shadow` over a remembered `box`. The web writes only the key that changed
(`subtitle-panel.js:127-129`). Fix: use a flag per value, or write only the key that changed.
The window is short (sheet opened within the resolve round trip).

### L2 — Bar clearance is measured from the video, not the screen (Low, UX)
`SubtitleLayer.kt:40,86`

The layer sits inside the video box, but the control bar sits at the bottom of the *screen*.
Take a portrait phone with a 16:9 film (a video box about 220 dp tall, centred on screen): the
bar never overlaps the picture, yet showing it lifts the cue 96 dp, which is almost half the
picture. The clearance should apply only when the bottom of the video box actually meets the
bar's area. Phase 08 already re-checks placement, so this can be folded in there.

### L3 — The ticker runs whenever cues exist (Low, perf)
`SubtitleLayer.kt:62-69`

The ticker runs every 100 ms whenever cues exist, including while paused or backgrounded, but the
spec's Risks section says "only while playing". Keeping the tick while paused does cover a seek
made while paused, so that part is defensible. Separately, `activeCues` maps every cue and
allocates a `TimedCue` per cue per tick: about 1,500 allocations at 10 Hz for a film. A binary
search over the sorted parsed cues (the offset is uniform) would remove this.

### L4 — One test file is over 200 lines (Low)
`SubtitleChoiceControllerTest.kt` is 288 lines. The 200-line guideline applies to code files, so
split it into a choice test and a style test, the same way the controllers are split.

## Plan status
All todo items except the device run look complete. The device check stays open, as the phase
file says. M1 should be fixed before that run, because the "switch Off → next lesson stays Off"
step is exactly the case M1 can break.

## Unresolved questions
- None blocking. Whether the M1 flash actually shows on the tablet depends on how
  `core.preferences` latency compares with `setText` + parse there; either way the double fetch
  is certain.
