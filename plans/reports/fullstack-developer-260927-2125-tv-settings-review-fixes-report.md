# TV Settings two-pane: review fixes before 0.69.3

Worktree `mediagram-channel-index`, branch `feat/android-settings-redesign`. Fixed all 9 findings plus the style items from `plans/reports/code-reviewer-260927-2110-tv-settings-two-pane-review-report.md`. Nothing committed; version left at 0.69.3.

## Findings, fixed how / test name

**1+2. Up at a section's top jumped to the index; Left inside a row left the section.**
`TvSettingsPanes.kt` — the content `Column` gained `.focusProperties { onExit = … }` + `.focusGroup()`, the reviewer's tested fix verbatim: Up/Down `cancelFocusChange()` at an edge, Left `requestFocus()`s the selected index row only once the group's own search already failed (i.e. only from the leftmost control). Tests: `upAtTheTopOfASectionStaysThereRatherThanJumpingIntoTheIndex`, `leftAmongTheAccentSwatchesMovesToThePreviousOneRatherThanLeavingTheSection`, `leftFromTheLeftmostSwatchReturnsToTheAppearanceRow` (all in `TvMenuTest.kt`, driven by real `KeyEvent.KEYCODE_DPAD_*` via a new `key()` helper mirroring `TvSearchAndGenreTest`'s).

**3. One scroll position shared by all four sections.**
`TvSettingsPanes.kt` — `rememberScrollState()` → `val scroll = remember(section) { ScrollState(0) }`. Test: `eachSectionOpensScrolledToItsOwnTopRatherThanKeepingAnotherSectionsOffset` — scrolls Storage 6 D-pad-downs deep (Cache heading → its one budget choice, under the fixture's cap → the two "Where" rows → the home cache server's switch/address/token rows), Backs out, moves Up to Telegram, and asserts the "TELEGRAM" page-head's `boundsInRoot.top` matches a freshly-opened baseline.

**4. Page-head eyebrow 11sp on TV, spec wants 16sp.**
`designsystem.PageHead` gained `eyebrowStyle: TextStyle = Eyebrow` (phone's call site untouched, so its own default is unchanged); TV's `PageHead` call in `TvSettingsPanes.kt` passes `Eyebrow.copy(fontSize = TvTypeScale.eyebrow)`. Confirmed on-device (`tv-06-settings-index.png` on): "YOUR ACCOUNT AND WHERE IT'S SIGNED IN" now reads the same size as the index's own "SETTINGS" eyebrow.

**5. TV's home cache server block only ever showed Status.**
`TvLanCacheBlock.kt`: `rows = listOf("Status" to lanCacheStatusLine(current))` → `rows = lanCacheRows(current)`. `lanCacheStatusLine` had no remaining production caller, so it's deleted from `LanCacheStatusLine.kt` along with its own KDoc; its "Not found"/"Searching"/etc. coverage is folded into `LanCacheStatusLineTest.kt`'s `theFourStatusWordsMatchTheirConnectionState`, now asserting through `lanCacheRows(...)[0].second`, and the redundant "omits the comma" test (a `lanCacheStatusLine`-only concern — the comma doesn't exist once Status and Holding are separate rows) is gone; the other three `lanCacheRows` tests already covered Holding/Chunks. On the real TV box the paired server isn't reachable right now (`tv-14-storage-home-cache.png` reads "Status: Not found" only, correctly — Holding/Chunks are `null` and `TvInfoBlock` skips null rows, same as the phone); `LanCacheStatusLineTest.theStorageLedgerShowsTheServersOwnBudgetAndChunkCount` is what proves the connected case renders both rows.

**6. Full-screen profile-reload panel lost its overscan padding.**
`TvTelegramSection.kt`'s `TvProfileReload` — its `Column` now takes `modifier = if (takesFocus) Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical) else Modifier`, restoring what `TvSettingsRows.kt` had before the move (the non-full-screen call from inside Telegram, `takesFocus = false`, is unaffected).

**7. A failed poll could steal focus even with a stale snapshot on screen.**
`TvSystemSection.kt` (renamed from `TvSystemScreen.kt`) — root cause was the effect re-firing on every later `failure`/`current` flip, not just on entry. Added a `landed` guard (`remember(focusInContent) { mutableStateOf(false) }`, set `true` once the effect actually places initial focus) so the retry-vs-Catalogue decision runs once per visit; the retry branch is additionally guarded to `current == null && failure != null`, so a later failure with an existing snapshot moves nothing. Test: `aFailedPollWithAStaleSnapshotDoesNotStealFocusFromWhereTheViewerAlreadyIs` — flips a real `MutableStateFlow` failure after landing on Cache, asserts focus is still on Cache. This needed `TvAppFixture.system` to stop being `private` (now `val`, matching `settings`/`cacheBudget`/`lanCache`).

**8. OK on Storage before the first cache read could lose focus entirely.**
`TvCacheBudgetBlock.kt` — the loading-state and loaded-state `TvInfoBlock("Cache", …)` calls (two different nodes sharing one `FocusRequester`, only one of which was ever the one actually focused after the read replaced the other) are merged into one call site whose `rows` are computed from `current` (`current?.let { listOf(...) }.orEmpty()`), so the heading node itself never gets swapped out from under the requester.

**9. Tests thinner than the spec asked; System's Back test proved less than it looked.**
Covered by the tests above, plus: `systemLandsOnItsIndexRowThenOkEntersItAndBackWalksOutOneStepAtATime` now also asserts `"WHAT THIS PLAYER IS DOING, REFRESHED AS IT HAPPENS"` is gone after the final Back, not just that a node named "System" is focused (which the index row also satisfies).

## Style (item 10)

- `TvSettingsScreen.kt` was 215 lines. Extracted the four full-screen panels (`TvSettingsPanel` enum + their `when` branches) into a new `TvSettingsPanel.kt` (`TvSettingsPanelScreen` composable, one shared `BackHandler`). `TvSettingsScreen.kt` is now 183 lines.
- `git mv TvSystemScreen.kt TvSystemSection.kt` — it only ever held `TvSystemContent` since the phase's own move; matches `TvTelegramSection`/`TvStorageSection`. No references to the old file name existed outside the file itself.
- `ui-common/.../SettingsSections.kt:9` — "phase 04's own two-pane build" → "the television's own two-pane Settings".
- `TvMenuTest.kt`'s `systemLandsOnItsIndexRow…` — "One more level than before the index existed" (a history comment) → describes what the next Back actually reaches and why the eyebrow assertion below is the real proof, not the history of how many levels there used to be.

## Files modified (this session's own edits, beyond what the worktree already carried)

- `android/core/designsystem/src/main/kotlin/PageHead.kt`
- `android/feature/system/src/main/kotlin/LanCacheStatusLine.kt`
- `android/feature/system/src/test/kotlin/LanCacheStatusLineTest.kt`
- `android/ui-common/src/main/kotlin/ui/settings/SettingsSections.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsPanes.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsScreen.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsPanel.kt` (new)
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvTelegramSection.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSystemScreen.kt` → `TvSystemSection.kt` (git mv, content edited)
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvCacheBudgetBlock.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvLanCacheBlock.kt`
- `android/ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt`
- `android/ui-tv/src/test/kotlin/ui/tv/TvMenuTest.kt`
- `docs/project-changelog.md`

Untouched: `TvAppearanceBlock.kt`, `TvFocus.kt` (the lead's two fixes kept as-is), `TvSettingsIndex.kt`, `TvStorageSection.kt`, `TvInfoBlock.kt`, `TvMenuBranches.kt`, `LanCacheUiState.kt`, `LanCacheViewModel.kt`, `TvAppearanceBlockTest.kt`, `SettingsPanesTest.kt` and the phone/`ui-mobile` files — none needed a change for these findings, and phone's own `PageHead` call keeps its default `eyebrowStyle`.

## Tests status

- `./gradlew testDebugUnitTest lint` (whole project): **green**. Ran twice — once split, once combined as instructed.
- New/changed tests all in `ui-tv`'s `testDebugUnitTest`, confirmed via a targeted `:ui-tv:testDebugUnitTest --tests "ui.tv.TvMenuTest"` run (exit 0).
- `LanCacheStatusLineTest` (feature:system) green with the folded test.

## Device verification (TV box `192.168.0.35:5555`, "andre" profile, Settings only — never started playback)

- `ANDROID_SERIAL=192.168.0.35:5555 ./gradlew :app:installBenchmark` then `adb -s 192.168.0.35:5555 shell cmd package compile -m speed -f com.mediagram.android` — both succeeded. Tablet `caad49da` and the emulator were left alone throughout (serial pinned on every `adb`/`gradlew` call).
- Walked Menu → Settings → Telegram → `Up` at "Change library" (the section's own top): stayed on "Change library", page stayed Telegram. Screenshot: `plans/reports/tv-07-up-at-top.png`.
- `Left` from "Change library" (the section's own leftmost/only control at that depth): returned to the Telegram index row. Screenshot: `plans/reports/tv-08-left-from-change-library.png`.
- Appearance: Coral → Right → Right (Violet) → `Left`: landed on Blue, not the index — the accent-ring is visibly on the Blue swatch. Screenshot: `plans/reports/tv-11-appearance-left-blue.png`.
- Storage → scrolled to the home cache server block: `Status: Not found` shown alone, correctly (no server reachable on this network right now, so Holding/Chunks are genuinely null and skipped — same behaviour the phone has always had for a disconnected server). Screenshot: `plans/reports/tv-14-storage-home-cache.png`.
- Eyebrow size visibly matches the index's own "SETTINGS" label on every section visited (Telegram, Appearance, Storage). Screenshots: `tv-06-settings-index.png`, `tv-09-appearance-index.png`, `tv-12-storage.png`.
- Backed out through Storage → index → menu → Home with no crash; `adb logcat -d "*:E"` filtered for `mediagram`/`FATAL` after the walk: empty.

## Anything left

- The real home cache server wasn't reachable from the TV box during this session, so the "Holding"/"Chunks" rows couldn't be screenshotted live on-device — their correctness for the connected case rests on `LanCacheStatusLineTest.theStorageLedgerShowsTheServersOwnBudgetAndChunkCount` plus the unchanged `TvInfoBlock` null-row skipping. Worth a follow-up on-device check once a paired server is on the same network, but nothing here is unverified logic — it's the same `lanCacheRows` the phone already ships.
- `TvMenuTest.kt` is now 408 lines (was 317, pre-existing before this session and already over 200). Item 10 only named `TvSettingsScreen.kt`/`TvSystemScreen.kt` for a split/rename; I left the test file as one class rather than inventing an unrequested split.

**Status:** DONE
**Summary:** All 9 review findings plus the style items are fixed, tested (including new D-pad-driven tests against `TvAppFixture`), and verified on the real TV box; `testDebugUnitTest` and `lint` are green project-wide; changelog extended in its own voice; version and commit left untouched as instructed.
**Concerns/Blockers:** None blocking. One follow-up noted above (on-device Holding/Chunks screenshot once a home cache server is reachable from the TV box).
