# Phase 03 review: Android shared player model

Branch `worktree-agent-a34e39d7ccd20d172`, 10 commits 343cbc02..2c2e53de, reviewed 2026-10-05. This was a read-only review. Probes ran in a `git archive` copy under the scratchpad, never in the worktree.

## Verdicts

- **(A) Spec compliance: ❌, one item.** Every phase task and every name in the Interfaces block is met exactly: package, file, type, default and constant value. Review Focus 1 (the after-the-edge half), 4 and 5 each have a real test. The miss is spec item 5 (the episode sidebar): an episode opened from a hand-built list gets a sidebar built from that list (H1). This gap comes from the phase text, which builds the list from the run without excluding lists.
- **(B) Quality: changes needed.** One High and one Medium; the rest are Low or informational.

## Evidence

- `./gradlew :core:playback:testDebugUnitTest --rerun :feature:player:testDebugUnitTest --rerun :ui-common:testDebugUnitTest --rerun` finished with BUILD SUCCESSFUL. The tasks really executed; none was UP-TO-DATE.
- Mutation probes against `SubtitleCardToggleTest`:
  - Dropping `last` from `toggleOn` fails 1 of 4 tests.
  - Dropping the `preferred` fallback fails 1 of 4 tests.
  - So Review Focus 4 is pinned, not vacuous.
- Review Focus 1: `aSeekBackOffTheEndStopsTheCountdown` failed RED before the fix (the implementer's report agrees with the code).
- Review Focus 5:
  - `anUnknownIdAndASeasonlessEpisodeGoLast` and `aRunOfNothingPlaceableIsOneEpisodesSection` (EpisodeListTest) cover the list.
  - `aRunHoldingAnIdTheCatalogueDoesNotKnowStillSteps` (UpNextRunStepsTest) covers ⏮/⏭.
  - `aTitleTheRunHoldsTwiceIsListedOnce` failed RED before `run.distinct()`.
- Line counts:
  - `PlayerViewModel.kt` is 200 lines, right at the ceiling, so phases 04 and 05 cannot add a line to it.
  - `UpNextController.kt` is 198.
  - Every other touched main file is ≤ 98.
- Comments: none in the diff cites a plan, phase, task or review code.
- Coroutines: there are no leaks. The flow adds two coroutines scoped to the ViewModel (one collector and one `stateIn`), and `collectLatest` cancels a stale catalogue read. Nothing is launched per open.

## Findings

### High

**H1. A hand-built list run gets a sidebar that mixes shows.**
- Where: `EpisodeListFlow.kt:49-51` and `EpisodeList.kt:60-62`.
- What happens: `episodes` is built from `upNext.run`. For a title opened from My List or the Kids wall, that run is the hand-built list itself (`handPicked = at.run != null`, `LibraryFlowBranches.kt:145`, `TvLibraryBranches.kt:105`).
- Probe result for the run `[Show A S1E3, a film, Show B S1E1]` opened on A:
  `[(Season 1, [S1E3 A pilot, S1E1 B opener]), (Chapter 1, [ A film])]`
  Two shows land under one "Season 1", and the film appears under "Chapter 1".
- This matches neither reference:
  - The spec says the sidebar shows "all seasons" of the show and is hidden otherwise.
  - Web phase 02 rules: "☰ is hidden for a film and for a hand-built list" (`app.js` passes `collection: null`).
  - The phase 03 surface rule "☰ shown iff `episodes != null`" would ship this to both phase 04 and phase 05.
- Fix: the ViewModel already receives `handPicked` in `open()` (`PlayerViewModelOpen.kt:31`, used for preload at `:61`).
  - Keep it in a `MutableStateFlow<Boolean>` set in `open()`.
  - Combine it into `episodeListFlow` and return `null` when it is true.
  - Add a `PlayerEpisodesWiringTest` case: open an episode with `handPicked = true` and expect `episodes == null`.
  - Writing this into the Interfaces surface rules lets 04 and 05 inherit it.

### Medium

**M1. The flow starts from an empty catalogue and treats "not loaded" as "unknown".**
- Where: `EpisodeListFlow.kt:41-46`.
- Before the first `catalogRepository.sets()` lands, `combine` runs with `emptyMap()`. The probe used a gated catalogue:
  - **Film from a list.** `episodes` is non-null (one "Episodes" section of two "Unknown title" rows), so ☰ shows. It turns null only once the read lands.
  - **Read fails.** If `sets()` throws, `safely` turns it into an empty map and the film keeps ☰ until the run changes.
  - **Series run.** Inside that window, the sidebar is one "Episodes" section of "Unknown title" rows. On a TV cold start, `awaitCore` makes the window seconds long.
  - **A failed re-read wipes good data.** The failure writes `emptyMap()` over a good earlier listing, so every row turns unknown.
- The whole catalogue (about 10.4k `MediaSet`s on this library) is kept by an activity-scoped ViewModel (`hiltViewModel()` with no NavHost) for the rest of the session, even after `stop()`. That duplicates the catalogue feature's own copy, which matters on the TV box.
- Fix:
  - Make `sets` a `MutableStateFlow<Map<String, MediaSet>?>(null)` and emit `null` until it is loaded.
  - On failure, keep the previous value.
  - Store only `all.filter { it.setId in ids }`.
  - Drop it when the run empties.
  - H1 removes the film-from-list case. The series case and the memory retention remain.

### Low

**L1. ↺ after the credits starts playback.**
- Where: `PlayerViewModelRun.kt:28-30`.
- Cause: at `STATE_ENDED`, media3 keeps `playWhenReady` true, so `seekTo(0)` goes to BUFFERING and plays. Meanwhile the card shows ▶ (`isPlaying` is false).
- The web differs: HTML sets `paused` on end, so `currentTime = 0` stays paused.
- The spec says ↺ "keeps the play/pause state". The test uses a relaxed mock `Player`, so it cannot see this.
- Options: decide and document the difference, or pause first when `playbackState == STATE_ENDED`.

**L2. A run that holds a title twice.**
- Where: `UpNext.kt:54-67`.
- On the second occurrence, `indexOf` finds the first one:
  - ⏮ is disabled;
  - ⏭ goes back to the title after the first occurrence, so ⏭ loops.
- This already happened for ⏭ before this phase; ⏮ now mirrors it. Rows are deduplicated, so the sidebar is safe. Accept it or note it.

**L3. `cardMenuOffset` clamps y to 0.**
- Where: `PlayerCardSurface.kt:39-47`.
- Clamping to 0 is right only in window or root coordinates. The KDoc says "in whatever coordinates". In coordinates local to the card, a menu that belongs above the card would be pushed down onto it.
- Fix: change the KDoc to "window coordinates", or pass a top bound.

**L4. Section words drift from web phase 02, and these phases ran in parallel.**
- Where: `EpisodeList.kt:82-98`.
- Differences:
  - **Course folder header.** Android shows `"Basics › 1. Start"`; the web shows the folder's own title, `"1. Start"`.
  - **Season-less group.** Android shows "Other" (or "Episodes" when it is the only group); the web shows "Episodes" placed last.
  - **Watched row with a position left.** Android drops the progress line; the web's `lessonRow` draws both the ✓ and the line.
- All three were written by the plan, not invented by the implementer. The lead should pick one wording before 04 and 05 draw it.

**L5. Recompute cost (informational, acceptable).**
- `episodes` re-derives on every watch-snapshot refresh. A progress save refreshes the snapshot about every 10 s while playing, and the open row's `progress` changes each time, so the sidebar recomposes every 10 s.
- The cost is O(run + snapshot) on Main, well under 1 ms at these sizes.
- Countdown ticks do not trigger it: the run passes through `distinctUntilChanged`, as planned.

## Scenarios walked

| Scenario | Result |
|---|---|
| `previous()` on the first title, or with no run | nothing happens (`previousInQueue` returns null); tested |
| `playFromRun` on the open id, or on an id outside the run | no switch; tested |
| `restart()` while paused | stays paused; tested. At the end it plays (L1) |
| Seeking back off the end after the countdown started | `ended` is cleared and the countdown stops; tested |
| A seek that lands on the end, or stays on it | the ordinary ended path counts down and switches; a 1 s slack applies; tested |
| Episodes when the run changes, the snapshot changes, or after `stop()` | follows all three; tested. Before the first catalogue read, see M1 |
| A run with duplicates, or with unknown ids | rows are unique and unknown ids go last. ⏮/⏭ on a duplicate: see L2 |
| CC with no tracks | `ccVisible` is false and the toggle does nothing; tested |

## Unresolved questions

- H1: should hand-built lists hide ☰, as the web's ruling does? Or should a list episode show its own show's seasons, as the spec's "the show's collection" suggests? Hiding it is the one-line parity fix.
- L1: should ↺ after the credits play or stay paused?

## Re-review

Scope: be577931, 73363358, 56081f85, d69bd36d (`2c2e53de..d69bd36d`). Read-only; mutations were reverted and `git status` shows a clean tree. `:feature:player:testDebugUnitTest :ui-common:testDebugUnitTest` passes (300 tests in feature:player).

| Item | Verdict |
|---|---|
| 1 Hand-picked run gives `episodes == null` | fixed (`runIsList` set in `open()`, `EpisodeListFlow.kt` combine) |
| 2 Catalogue read: null until read, failure keeps old, run's titles only, dropped on stop | fixed in code; the last two are only partly pinned by tests (below) |
| 3 `restart()` after the end stays paused | fixed (`PlayerViewModelRun.kt`: pause only at `STATE_ENDED`; the not-ended test still asserts no pause) |
| 4 Sidebar parity: last group "Episodes"; last path segment, keyed by whole trail; watched keeps progress | fixed |
| 5 KDoc says window coordinates | fixed |

### Teeth check
- Ignore `handPicked` (`runIsList.value = false`): FAILS 1 test. Drop `isList` from the combine: FAILS 1 test. Item 1 is pinned.
- Pending catalogue read treated as an empty catalogue (`?: emptyMap()`): FAILS 2 tests. Item 2 "null until read" is pinned.
- Failed read overwrites the listing (`?: ids to emptyMap()`): FAILS 2 tests. Pinned.
- Start `loaded` from empty instead of null: survives, but this is an equivalent mutant. The run-keyed `first == ids` check and the collector's `ids.isEmpty() -> null` already guard it, so no behaviour changes.
- Keep the whole catalogue (`ids to it.associateBy(...)`): SURVIVES. `onlyTheRunsTitlesAreKept` tests `runSetsOf` directly, not the call site.
- Do not drop `loaded` on an empty run: SURVIVES. Not observable through `episodes`, since an empty run already gives null. The memory release is untested.

### New findings
- Low: the two surviving mutants above mean the "only the run's titles" and "dropped on stop" retention claims are unpinned. A test on an internal accessor would pin them; this is not blocking.
- No new defect found in the diff. Unknown ids in a run (not in a good earlier read) re-read the whole catalogue on every run change; that is acceptable.

merge-ready: yes
