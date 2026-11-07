# Profiles polish — 0.136.2 (2026-10-20)

Worktree `/home/andre/Workspace/mediagram-polish`, branch `fix/profiles-polish`, from `main` 9a34a7b3 (0.136.1).
Source list: `plans/reports/profiles-polish-parked-items-261020-report.md` (10 items). All 10 fixed.

## Items

| # | Surface | What was done |
|---|---|---|
| 1 | web | `profile-manage.js:113`: the given one-liner. Manage now treats the session as gone unless the actor is one of this device's `profiles()`, so it asks "Who are you?" again. Before the fix, Maja, moved home elsewhere, still saw "As Maja" and Home's people (andre with Reset PIN and Remove) from a Flat device. Android already did this (`ManageUiState.kt:60` looks the actor up in this device's `profiles`), so the web now matches it. File still 200 lines. |
| 2 | web | `profile-manage-households.js`: dropped the third spread (rows' own households) and `?? id`. The line numbers in the list (198–199, 210) were stale; the code is at :34 and :45. The fallback is dead because the server places a gone or unknown household's profiles in Home (`household-rules.js:17-23`) and always lists Home (`web/src/state/households.ts:54`). The comment now says why. `.name` is used without `?.`, trusting that rule. |
| 3 | all | New word `noneToJoin` / `NONE_TO_JOIN` = "No household can be joined from here." It is in `household-words.js`, its `.d.ts`, the `words` section of `household-rules.json`, and Android `HouseholdWords` (constant and map). The web's `joinChoices` builds an `offered` list and adds the line when it is empty. Mobile `JoinHousehold` shows the line in place of the FlowRow when `read && households.isEmpty()`. TV `TvChoicesScreen` gets a `none` param (a quiet WhoNote line where the rows would be), which `TvJoinHousehold` passes once read. |
| 4 | android | `rereadAfterJoin` became `joinThenReread`. It now sends the join itself, because `repository.manage` already reloads and clears the chosen id before the old helper ran. It reads whether anyone was chosen before sending and calls `leftBehind` only if someone was. Otherwise the failure is rethrown: the prompt shows "did not go through" and asks again, and the retry joins and reads again. Both callers use it. `HouseholdsOwnerViewModel.kt` stays at 200 lines (one line for one line). |
| 5 | TV | `TvChoicesScreen`: a row's `onFocusChanged` clears `holding` when focus leaves it while it is still shown (`rememberUpdatedState(choices)`, the same pattern as `held()` in `TvProfilesPage`). **Extension beyond the item:** the landing effect is now keyed on `land != null`, not `land`. With the holding fix alone, the first row (the landing row) going while the remote was in the bar still pulled the remote back, because the landing row had changed. The test proved it: with the old key, `theFirstRowGoingKeepsTheRemoteInTheBar` fails. Landing now happens only when the list arrives and when the held row goes. This also affects the owner's Households steps, which share `TvChoicesScreen`; their tests pass. |
| 6 | TV | `TvProfileGate`: `LaunchedEffect(joining.joinsElsewhere) { if (!joining.joinsElsewhere) joiningElsewhere = false }`. |
| 7 | android | `ProfileGate.kt:70` comment reworded: "…before the join has said so. Who is watching is asked again, and Manage closes." |
| 8 | TV test | `TvManageHouseholdsStateTest`: dropped the false focus assertion after Reset PIN, kept the call check with a comment (in the app the pad stands in for the screen), and renamed the test to `anotherHouseholdsRowsAreChangedByRemoteAndAKidsRowKeepsIt`. |
| 9 | core | `writes.rs` `admin()`: the no-row error is now `SqliteFailure(SQLITE_NOTFOUND, "no household {id} to give an admin")`, the same words as the web's `households-write.ts:28`. It shows in `StateDb::with`'s log line. |
| 10 | core | Test `a_profile_made_in_home_is_undated_and_one_made_elsewhere_is_dated` (grown-up and kid in Home → 0; in Flat → `T`). |

## TDD evidence

- **Red first, for the right reason:**
  - Web 1: still "As Maja", with Home's rows drawn.
  - Web 2: a `loft` heading appeared.
  - Web 3: the fixture-words test failed, then the word was undefined.
  - Android `HouseholdRulesFixtureTest`: the map differed.
  - Item 4: `ChosenProfileLeftBehindTest.aJoinWithNobodyChosenAsksAgain…` failed at the error assertion; the prompt had closed.
  - Item 5: both bar tests (`TvJoinHouseholdStateTest:140,151`) failed; the bar lost focus.
  - Item 6: `TvJoinElsewhereGateTest:50` failed; the join screen reopened by itself.
  - Item 3 on Android: mobile `JoinHouseholdFlowTest:100` and TV `noHouseholdThisBoxCanJoinSaysSo` failed.
  - Item 9: the test failed on "Query returned no rows".
- **Item 10** pins behaviour that was already correct, so it passed first. A mutation check (`household_updated_at: now` always) made it fail on "Paul", and the code was then restored.
- **Item 5 key change:** with the landing key put back to `land` and the holding fix kept, `theFirstRowGoingKeepsTheRemoteInTheBar` fails, so both changes are needed.
- **The first run after the item 4 fix** broke two existing tests. The chosen id read inside the helper had already been cleared by `manage`'s own reload. Fixed by capturing it before the join is sent; all green after that.

## Tests and results (final tree)

- `cargo test --all`: 121 suites, 2241 passed, 0 failed.
- `cargo clippy --all-targets --all-features -- -D warnings`: clean.
- `bun test`: 3784 pass, 0 fail.
- `bun run typecheck`: clean.
- `bun run lint`: clean.
- `./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
- After the version bump: `code_standards` (Rust) still passes.

## Commits (`9a34a7b3..d3328e2b`)

```
12d2d973 fix(web): Manage profiles asks who you are once you have left this household
0088f253 refactor(web): Manage heads a household by its listed name only
09a51c19 fix: Join a household says so when no household can be joined from here
5aff0ea4 fix(android): a join with nobody chosen says when the read after it failed
0d2b768c fix(android): TV Join a household leaves the remote where it went when a row goes
47243227 fix(android): TV Join a household reopens only when pressed
817ac024 chore(android): a stale gate comment and a TV test's focus claim
75ad0cf5 fix(core): an admin given to no household names it
e6cc1c69 test(core): a profile made in Home is undated, one made elsewhere dated
d3328e2b chore: 0.136.2, profiles and households polish
```

The version is 0.136.2 in `Cargo.toml`, `web/package.json` and `versionName`, and `Cargo.lock` was updated offline. The changelog entry is at the top of `docs/project-changelog.md`.

## Files changed, and line counts near the limits

**Near the limits:**
- `web/public/lib/profile-manage.js`: 200 (unchanged count).
- `android/feature/catalog/src/main/kotlin/profile/HouseholdsOwnerViewModel.kt`: 200 (unchanged count).
- `JoinHouseholdViewModel.kt`: 158.
- `TvProfileGate.kt`: 163.

**Other changed files:**
- `TvJoinHousehold.kt`: 115.
- `JoinHouseholdScreen.kt`: 98.
- `household-join.js`: 44.
- `profile-manage-households.js`: 49.
- `writes.rs`: 55.

**Tests:**
- New: `ui-tv/src/test/kotlin/ui/tv/TvJoinElsewhereGateTest.kt` (53 lines). It is a new file because `TvHouseholdsFlowTest.kt` (193) would have gone past 200.
- `TvJoinHouseholdStateTest.kt`: 164.
- `JoinHouseholdFlowTest.kt`: 130.
- `ChosenProfileLeftBehindTest.kt`: 93.

No web ratchet ceiling was raised, and no Kotlin file over 200 lines grew.

## Concerns

- **Item 5 goes beyond the item as written.** The landing key change is needed to stop the first row going from pulling the remote, but it also changes `TvChoicesScreen` for the owner's Households steps. Within one composition, a changed `landing` no longer moves the remote. No current flow relies on that, and all TV tests pass. Not walked on a device.
- **Item 2 drops the defensive `?.`/`?? id`.** A malformed list, where the actor's household is unlisted while more than one household exists, would now throw in Manage's draw instead of showing an id heading. The server rule above rules this out.
- **Item 4 changes the owner's Use on this device too.** With nobody chosen and the read after the join failing, it now reports "did not go through" instead of emitting "left behind" (which closed Manage over the picker). This is consistent with its KDoc ("a read that fails throws, so the join is said not to have gone through").
- **Intermediate commits were not built one by one.** Three files were split across commits by staging prepared versions: the owner test, `TvJoinHousehold.kt` with its state test, and `manage_tests.rs`. Only the final tree was fully verified.
- **The TV "bar" in the tests is a stand-in** (a `TvTextRow` to the list's left). It is not the real chrome bar in Settings.
- No device walks were run, per the brief.
