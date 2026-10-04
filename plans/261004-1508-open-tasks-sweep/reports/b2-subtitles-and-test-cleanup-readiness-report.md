# B2 readiness — subtitles leftovers + Android watch-state test cleanup

2026-10-04, research subagent against `main` `ffea35b2` (0.100.1). Read-only.

## Changed since the plans were written
- Phase 06's real end-to-end upload is effectively done: the other machine (uploader 0.92.1)
  uploaded The Deuce S3E1–8 on 2026-10-01, the web served its VTT; uploads keep attaching
  bundles (190 newer on 10-03, 59 on 10-04, last 14:39).
- The MP4 channel backfill (07) stopped unlogged: the 2026-10-02 21:35 reboot ended run 2
  (last at 21:15). 671 of 2,482 MP4 sets bundled; last publish message 16298; **87 bundles
  exist only in the local index**; ~1,811 left (~3.6 days at 2.9 min/set). Runbook: one
  machine at a time, no upload running on either.
- Phase 04's device contract run already happened (tablet, 2026-10-03, `f1c915fc`, 34/34).

## Subtitles `260930-0303`
| Item | Open? | Closes it | Runnable |
|---|---|---|---|
| 03 web "player restart pending" | no — `bun --watch` reloads; `/api/sets` lists 2,662 with subtitles | plan edit | now (docs) |
| 04 tablet | yes (tablet has 0.99.8, has the code) | lesson opens Off, language shows cues, next lesson follows | now (device, test profile) |
| 05 tablet | yes | CC toggles / hidden without subtitles; Settings › Profile shows Subtitles row (look only) | now (device) |
| 06 e2e upload | done in substance; device half open (forced lines while Off, picker lists regular only, CC) | Q1 + tablet walk on a "German (Forced)" set e.g. `01M3JM17B5WYP004MM7DMQTJ95` | after Q1 |
| 07 move-inline | done 10-01 (message 13597, 0 inline rows) | — | — |
| 07 MP4 channel backfill | stopped, unlogged | resume `backfill --channel` | Q2 (outward) |
| 07 `--mkv` (1,051 sets, TB reads), local folders (62) | not started | those runs + rollout log totals | Q3 |
| 08 cross-device | yes; TV leg blocked (adb); web sync on (`MEDIAGRAM_SYNC_STATE=1`) can stand in | test profile Subtitles=English on tablet → sync → web has it → back to Off | Q4 |
| 09 remove inline read path | gated: follower copy 0 inline rows for ≥ a week (week ends **2026-10-08**; today 0, 2,662 bundles) + both uploaders ≥ phase 06 (`mediagram --version` on the other machine) | ~2 h code, revertible | Q5, ≥ 10-08 |
| plan.md unresolved question 2 | settled: `COURSE_HOLD_NEXT = 10` (`crates/mediagram-core/src/api/subtitles.rs:22`) | doc edit | now |

## Watch-state fake `260928-0130` (phase 01 shipped 0.70.2 `82ccff7e`)
| Phase | Applicable? | Size | Runnable |
|---|---|---|---|
| 02 | yes: `clearProgress` at `WatchStateRepository.kt:71,299` (5 test overrides); only `chosenProfileId`; 3 hand-rolled chosen-profile lookups (`CatalogViewModel.kt:62-79`, `PlayerMarksController.kt:41`, `ProfileSettingsViewModel.kt:42`); `FakeCoreProvider` lacks a failure hook | S–M ~3 h | now (worktree) |
| 03 | yes: four fakes — `feature/player/src/test/kotlin/FakeWatchStateRepository.kt` (164 l, 33 uses/17 files; `ProgressRecorderTest:23-61` call log guards 40a43589 until 02 lands), `ui-tv/src/test/kotlin/ui/tv/FakeWatchState.kt` (1 use `TvAppFixture.kt:188`), `CatalogViewModelTest.kt:96 FakeCatalogWatchState` (32 uses), `profile/ProfileViewModelTest.kt:25` (18 uses); plus failure wrappers in `PlayerActionFailureTest`/`PlayerActionNoticeTest`, relaxed mockk `TvPlayerFixture.kt:68`, `PlayerLifecycleFixture`. Keep `WatchSyncTest`'s `RecordingRepository`. | L ~6 h, ~20 test files | after B1 test work lands (shares `MobileAppFixture.kt`, `TvAppFixture.kt`, `CatalogViewModelTest.kt`) |
| 04 | device part done 10-03; docs + version left; re-run contract only if 02/03 change it | S | now / after 03 |

## Run-now order
1. Doc reconcile (subtitles plan.md + rollout-log.md). 2. Tablet walk 04/05/06-device-half with
B1's walk (one install). 3. Watch-state 02 (worktree; land before B3). 4. Watch-state 03 after
B1 merges. 5. Watch-state 04.

## Questions (put to the user 2026-10-04)
Q1 accept The Deuce S3 upload as phase 06's e2e proof (rec. yes) · Q2 resume MP4 backfill
now/nightly/not yet + is the other machine idle (rec. now, once idle) · Q3 then `--mkv` and
local folders, paced (rec. yes) · Q4 flip test profile Subtitles to English and back for 08
(rec. yes) · Q5 remove inline path on/after 10-08 if gates hold, without asking again (rec.
yes) · Q6 TV legs on next box visit (rec. yes).
