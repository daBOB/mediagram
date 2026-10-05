# Phase 03 implementation report

Branch `worktree-agent-a34e39d7ccd20d172`, on main 322c0600 (0.111.0). No version bump, push, adb.

## Commits and RED/GREEN per task

| Task | Commit | RED | GREEN |
|---|---|---|---|
| 1 skip 15 s | 343cbc02 | PlayerFactoryTest compile fail, `SKIP_MS` private | PlayerFactoryTest pass |
| 2 previous / picked row | df81a733 | UpNextRunStepsTest compile fail (previousInQueue, inRun, hasPrevious, playFromRun) | full :feature:player pass |
| 3 seek off the end | f671cd12 | `aSeekBackOffTheEndStopsTheCountdown` AssertionError at :45 (others passed) | full :feature:player pass |
| 4 episode list | cc6f618e | EpisodeListTest compile fail (episodeListOf) | pass |
| 4b distinct run (late edit) | 59630748 | `aTitleTheRunHoldsTwiceIsListedOnce` AssertionError | pass; `run.distinct()` in episodeListOf |
| 5 `episodes` flow | d97b12f5 | compile fail (`episodes`) | feature:player, ui-mobile, ui-tv pass |
| 6 VM previous/playFromRun/restart | 81ea8be6 | compile fail (`previous`) | pass |
| 7 menu/sidebar hold | eac2296c | compile fail (`menuOrSidebarOpen`) | pass |
| 8 CC toggle pin | a2ca3ae8 | n/a, pins existing behaviour: passed first run (4 tests) | pass |
| 9 card surface, placement, icon | 2c2e53de | compile fail (`cardMenuOffset`, main file moved aside) | ui-common, ui-mobile, ui-tv pass |

## Success criteria

- `:core:playback:testDebugUnitTest :feature:player:testDebugUnitTest :ui-common:testDebugUnitTest` exit 0.
- `:ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest lint` exit 0.
- New tests: UpNextRunSteps 7, UpNextSeekEdges 4, EpisodeList 13, PlayerEpisodesWiring 5, PlayerRunSteps 5, SubtitleCardToggle 4, CardMenuPlacement 5, ControlsVisibility +1, PlayerFactory 1 rewritten. Counts from the test sources; build dir is hook-blocked so not re-read from XML.
- Line counts (main): UpNextController 198, PlayerViewModel 200, EpisodeList 98, EpisodeListFlow 52, PlayerViewModelRun 30, UpNextPlayhead 33, UpNextState 33, UpNext 67, ControlsVisibility 47.

## Deviations

- Worktree began 93 commits behind main (0.99.9); fast-forwarded to main before starting.
- Late coordinator edits folded in: `run.distinct()` plus its test (follow-up commit, Task 4 already committed); KDocs on `UpNextController.playFromRun` and the `onSeeked` comment shortened (UpNextController stays 198).
- Test files written with the Write tool instead of shell snippets; content verbatim.
- Code matched the phase everywhere; no other drift found.
- Report written to the worktree copy of the plans dir (tool refused the shared-checkout path); lead should copy it to the main checkout.

## Concerns

- None blocking. TV D-pad still skips 10 s until phase 05 (known interim).
- Task 8 had no RED by design.

## Fix round 1

Review: `reports/phase-03-review-report.md`. All on the same branch; each fix test-first.

| Finding | Fix | Evidence |
|---|---|---|
| H1 hand-built run | `PlayerViewModel.runIsList` flow set in `open()` from `handPicked`; `episodeListFlow` emits null while true | `aHandBuiltListGetsNoEpisodeList` (list with 2 shows + film null; same episode via its show gives list) |
| M1 catalogue read | `loaded` holds (run ids, only the run's titles via `runSetsOf`); null until read lands; failure keeps previous; dropped when run empties (`stop()`) | `noListUntilTheCatalogueHasAnswered`, `aFilmNeverGetsAListEvenWhenTheReadFails`, `aFailedLaterReadKeepsThePreviousListing`, `onlyTheRunsTitlesAreKept`; drop on stop is `leavingThePlayerClearsTheList` |
| L1 restart after credits | `restart()` pauses first when `playbackState == STATE_ENDED`, then seeks | `restartAfterTheCreditsSeeksToTheTopAndStaysPaused` RED before fix; existing not-ended test still asserts no pause |
| L4 web parity | "Other" removed (`OTHER_SECTION` deleted; last group is always `EPISODES_SECTION`); section named by last path segment but keyed by whole trail; watched rows keep progress | EpisodeListTest: 4 tests RED then GREEN; new `foldersSharingANameStaySeparateSections` |
| L3 comment | `cardMenuOffset` KDoc says window coordinates | doc only |
| L2 | parked, unchanged | |

Commits: "fix(player): no episode list for a hand-built run or before the catalogue answers; keep only the run's titles", "fix(player): restart after the credits stays paused", "fix(player): episode sections named as on the web...", "docs(ui): card menu offset is measured in window coordinates".

Interfaces delta for phase doc (Deviations): `OTHER_SECTION` no longer exists; `EpisodeList` sections' titles are folder names (last segment); `EpisodeRow.progress` may be non-null when `watched`; `episodes` is null while the catalogue read is pending and for a hand-built list; new `PlayerViewModel.runIsList` (internal) and `runSetsOf` (internal). `FakeCatalogRepository` gained `setsGate`/`failSets` (test only).

Notes: M1 and H1 tests were written first and failed to compile or, for H1/M1 behaviour, were seen RED only as a compile failure (`runSetsOf`) in the first run, not per test; the behaviour RED was not isolated. `PlayerViewModel.kt` stays at 200 (the `episodes` KDoc moved into `EpisodeListFlow.kt`'s). Final run: `:core:playback :feature:player :ui-common :ui-mobile :ui-tv testDebugUnitTest` and `lint` exit 0.
