# Cast to the TV: final review fixes

Date: 2026-10-21. Worktree `/home/andre/Workspace/mediagram-cast`, branch `feat/cast-to-tv`.
It started at `423b6e75` and ends at `2eb57d1b`. The version stays 0.137.0, and nothing was pushed.

## Commits

| Commit | Finding |
|---|---|
| `7c76ea66` fix(cast): the TV refuses a title in a section hidden from the caster | 1 |
| `24cdf209` fix(web): the remote says Finished when the title on the TV ends | 2 |
| `49e778fa` fix(android): a kid's cast over the TV's profile picker follows Next | 3 |
| `9dfa0afd` fix(android): the cast remote listens only while it is on screen | 4 |
| `ffc56b7b` fix(android): one household's cast offer never hides another's | 5 |
| `f82d9426` docs: a hidden section refuses a cast; a TV holds each household's newest request | 6 (plus the spec) |
| `2eb57d1b` test(android): the hidden-section refusal has a file of its own | 1 (keeps a test file under 200 lines) |

## Findings

### 1. The TV refuses a title in a section hidden from the caster (Important)
- `CastReceiverViewModel.refusalOf` now runs `if (media.sectionKey() in profile.hiddenFrom) return sectionReason(profile.name)`. It runs after the library check and before `if (!profile.kids)`, so it also covers profiles that are not kids.
- New word `sectionReason(name)`, which reads "it's in a section hidden from Ben". It sits in `cast.json` `words.lines`, in the Kotlin port (`TogetherCastWords.kt`) and in the web's `cast-words.js`/`.d.ts`. The web sender shows refusals through `refusedLine`, so the web needs the word too. Both fixture tests check it.
- The web hub has no refusal path, because the web is never the receiver, so there was nothing on the web side to mirror. `web/src/together/` only creates, waits for and withdraws offers.
- The spec (`docs/mlib-spec.md`, the paragraph on a cast's states) now lists a hidden section among the refusal reasons.
- Tests: `CastReceiverHiddenSectionTest` (it was first written in `CastReceiverViewModelTest`, then moved so that file stays at 196 lines), and the `section reason` fixture line.

### 2. The web remote never said "Finished" (Minor)
- `hub.ts` `tell` now runs `this.announce(id, session?.ended ? session : shown, lost)` and still returns `shown`. `heardTogether` already turns an ended session into `null` for `live` and passes it on as `ended`. Line count unchanged (200).
- `cast-remote.test.ts` now hears the TV's edits through a real `TogetherHub` over `FakeChannel`, whose announcements go into `heardTogether`, the way the event stream delivers them. It no longer calls `heardTogether` directly.
- Two hub tests asserted `[id, null]` for an ended session. They now assert that the ended session is announced. Their intent is the same: the tabs see the session as over.

### 3. A kid's cast over the TV's profile picker broke on Next (Minor)
- `onThisProfile` is now `suspend`. Its only caller is the `suspend` lambda `titleOf`. With a profile chosen it reads the Kids marks from `snapshot` as before. With none chosen it reads them from `snapshotOf(kid.id)`, the library-wide marks, as `refusalOf` does.
- Test: `PlayerCastTvTest.aKidsCastOverTheProfilePickerFollowsNextToAMarkedTitle`.

### 4. The remote kept the push stream open in the background (Minor)
- `CastRemoteViewModel` has a `started` flag and `screenShown(shown)`. After the join, the remote follows the session through `started.collectLatest { if (it) live.live.collect(::heard) }`. When the screen comes back it does one `live.refresh()`.
- `CastRemoteScreen` reports start and stop with `LifecycleStartEffect`. The player screen uses `screenShown` in the same way.
- Test: `CastRemoteFollowingTest.aRemoteNotOnScreenHoldsNoStreamOpenAndCatchesUpOnReturn`. With the screen stopped the push-stream subscription count drops to 0. On return it is back to 1, and a pause that was never pushed has been applied.

### 5. One household's offer hid another's (Minor)
- I chose "keep the newest per household" over skipping `castElsewhere` in the data layer. Skipping would have needed `HouseholdsRepository` inside `TogetherLive`. That means growing `DataModule.kt` (200 lines) and `DefaultTogetherLive.kt` (199), plus a gap before the TV's households have been read.
- `TogetherLive.requests` is now `StateFlow<List<LiveSession>>`: the newest live request of each household.
- `TogetherSessions` gains `hold` and `drop`. `TogetherReads.discover` holds every request it reads, and the stale-request sweep checks each one.
- `CastReceiverViewModel` answers every request it holds. `answerOf` still ignores another household's.
- `TogetherJoining.takeCast` looks up its offer by id.
- `FakeTogetherLive` and the test call sites changed to match.
- Tests:
  - `CastReceiverHouseholdsTest`: a real `DefaultTogetherLive` over `FakeCore`, with a Home offer and an `h2` offer found in one read. The TV opens the Home offer.
  - `TogetherCastRequestsTest.eachHouseholdKeepsItsNewestOffer`.

### 6. Docs
`docs/system-architecture.md`, in the Household part of Cast to the TV, now says:
- a TV holds the newest request of each household;
- the design assumes one TV per household, and with two of one household open, both answer the same offer.

## TDD evidence (red, then green)

| Finding | Red, before the fix | Green |
|---|---|---|
| 1, web | `cast words > section reason`: `formats[one.fn] is not a function` | pass |
| 1, Android | `aTitleInASectionHiddenFromTheCasterIsRefused`: `NoSuchElementException` (no refusal sent). After the move, the new file was run again with the check removed: `List is empty`. | pass |
| 2 | `the title ending closes with Finished`: expected "Finished", received "Stopped on the TV" | pass |
| 3 | `aKidsCastOverTheProfilePickerFollowsNextToAMarkedTitle`: `AssertionError` at the `pendingSwitch` assert | pass |
| 4 | Against a no-op `screenShown`: `AssertionError` at the subscription-count-0 assert | pass |
| 5 | `CastReceiverHouseholdsTest`: expected `CastOpen(film-1)`, but was `null` | pass |

- The Kotlin fixture test and `eachHouseholdKeepsItsNewestOffer` were written alongside their changes. The second uses the new `List` API, so it could not have been red first.
- The web fixture test is the red for the shared word.

## Verification (final tree)

- `cargo test --all`: exit 0, every `test result: ok`.
- Web:
  - `bun test`: 3925 pass, 0 fail.
  - `bun run typecheck`: clean.
  - `bun run lint`: clean, 0 warnings.
- `./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL.
  - 0 test failures.
  - Lint reports 46 warnings and 2 hints, plus 1 error held by the baseline. None of them name a file this work touched. I did not compare against a pre-change run.
  - After that run, the hidden-section test moved to its own file and `:feature:player:testDebugUnitTest` was run again: green.
- Line limits:
  - Every Kotlin file touched stays at or under 200 (`DefaultTogetherLive.kt` 199, unchanged).
  - `TogetherCasting.kt` and `DataModule.kt` were not touched.
  - `hub.ts` stays at 200, so there is no ratchet entry.
- No devices touched. No `git stash`.

## Concerns

1. **Finding 3 cost.** With nobody chosen on the TV and a kid's cast playing, every 2 s drift check reads the core for the kid's snapshot. This is marked with a `shortcut:` comment. Cache the marks at open if the box shows it.
2. **Finding 5 changes `TogetherLive.requests` from one value to a list.**
   - Every caller in this repository has been updated.
   - A held request that is not in a read is now kept while it is still a request, until the one-minute sweep. Before, a read that found any request replaced it outright.
   - The receiver re-checks requests it ignored (another household's) on every change to the list. That is cheap but repeated.
3. **No device check.** Nothing was checked on a device: not the remote's background behaviour, not two households, not the TV on its picker.
4. **Lint.** I only checked that none of the lint warnings names a touched file. I did not compare against the baseline, and detekt is not wired as a per-module task, so it did not run.

## Unresolved questions
- None.
