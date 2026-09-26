# Review: sync-watched removal tombstone + write-debounced publish

Worktree `fix/sync-watched-removal` (base 0ae9670), uncommitted. 22 files, +543/-101.
Tests: web state suites 249/249 pass, `cargo test -p mediagram-core state` 101 pass.
Mixed-version tie verified by a throwaway probe against the new `WatchState`/`mergeStates` (deleted afterwards).

## Critical

### C1. Mixed versions: an old device turns a removal into a live mark with the *same* timestamp, then device-id tie-break decides. The removal is lost for good about half the time, and it does not heal when the old device updates
`web/src/state/merge.ts:197`, `crates/mediagram-core/src/state/merge/tie_break.rs:45`, `web/src/state/store.ts:470-772`, `exchange.rs:140-142`

What happens step by step (old = any build before this one, e.g. Android 0.49; the old parsers drop `removed`, as shown by the `-` lines in `sync-record.ts:197` / `parse.rs:119`):
1. New device A has 01A watched at F. The user un-marks it at R, and A publishes `{01A, R, removed}`.
2. Old device B parses that as a **live** completion at R. Old import: `standing F < R`, so it upserts `finished_at = R` **and runs `DELETE FROM progress … updated_at <= R`**. B keeps showing "watched" and now publishes a live `{01A, R}` forever.
3. A's next merge has `{R, removed}` from A and `{R, live}` from B. That is an exact tie. `keep` breaks it on device id, and device ids are `crypto.randomUUID()` (`store.ts:345`), so the outcome is a coin flip per device pair.
   - **B's id > A's**: live wins everywhere. A's import sees `tied` as false (the removed flags differ) and rewrites the row as live (`finished_at=R, removed_at=NULL`). It also deletes every position ≤ R. Probe output with B=`bbb`, A=`aaa`: `A watched: [{01A, finishedAt R}]`, `A progress: []`. **The un-mark is lost on every device.**
   - **B's id < A's**: the removal wins on new devices. B shows "watched" until it updates. Probe with B=`000`: `A watched: []`.
4. No ping-pong: rows are fixed and the tie-break is deterministic, so every device converges. But in the "B wins" case, updating B does not fix it. B's DB now holds a live row at R, the tie is still there, and the same tie-break still picks live. The removal only takes effect if the user un-marks again after every device has updated.
5. Side effect even while the removal is winning: the old device deletes its own positions in (F, R], for example a partial rewatch after finishing. It also filters them out of its own merge.

What users get until every device updates: an un-mark on the web shows "watched" again on old devices (expected). On new devices it is randomly kept or reverted, and a revert is permanent. Old devices lose any rewatch position older than the un-mark.

Fix options (pick one, apply to both engines, pin with a fixture):
- **Minimal:** at equal `updatedAt`, a `removed` row beats a live one, checked *before* the device-id tie-break, in `keep` for `WatchedRow` (or for all kinds; the list kinds have the same shape). Result: new devices always keep the removal, and B heals when it updates (its import sees removed R vs live R and `tied` is false, so it writes the removal). The existing fixture "a tie between a watched tombstone and a live completion breaks on device id" (`merge.json`) encodes exactly the losing behaviour and must flip. Old devices still delete (F, R] positions locally during the transition.
- **Cleaner compat:** send removals under a new key (for example a `unwatched: [{setId, updatedAt}]` array per profile) that old readers drop. That follows the existing "new keys, not a format bump" rule in `sync-record.ts:27-32`. Old devices then never see R, keep exporting live F < R, and the removal wins by time with no tie and no old-side progress deletion.
- A `SYNC_FORMAT` bump is the wrong tool. Old readers return `null` for format > 1 (`sync-record.ts:136`, `parse.rs:28`), which cuts off *all* sync from new devices to old ones.

The changelog claim that the fix means "an un-mark from one device is not silently re-imported from an older device" is false for older *builds* until C1 is fixed.

## High

### H1. `WriteDebounce` fires a full sync round, and a Telegram message edit, about every 10 s for the whole of playback
`web/src/routes.ts:79-80`, `web/src/index.ts:542,551`; cadence at `web/public/lib/playback/player.js:470` (`setProgress` every 10 000 ms) with a 5 000 ms trailing debounce.

Each progress PUT re-arms the timer. The next PUT arrives 10 s later, so the 5 s of quiet always elapses and a round fires every 10 s. Each round does `channel.list()`, and because `at` changed, `pushIfChanged` edits this device's message (`sync.ts:141-152`). A 2-hour film means about 720 message edits plus 720 channel reads. Every other device then hears a `state` push and runs its own round (`lifecycle.ts:35`, and Android `WatchSync`). That invites FLOOD_WAIT on the same account the player streams bytes through (see the known pin flood limits). It is also a parity break: Android calls `soon()` only when a film is left (`PlayerViewModel.kt:152`), not on the 10 s ticker.

Fix: don't `touch()` on periodic progress writes. Options: filter `/progress/` in `onWrite`, and have the client's final/pagehide flush (and pause) hit a route that does touch; or add a minimum spacing between write-triggered rounds (for example at most one per 60 s, trailing). The test `state-write-triggers-sync.test.ts` "fires once for a successful write" uses a progress PUT, so it pins the problem behaviour.

## Medium

### M1. A removal un-suppresses positions from *stale* records, so an ancient position can come back on Continue
`merge.ts:144-154`, `merge.rs:140-159`, import `store.ts:459-470`.
Scenario: phone played 01A to 80% at P and has been off since (its channel document still holds P). Laptop finished at F > P, deleted P locally, and imports on other devices deleted P ≤ F. Weeks later the user un-marks at R. Now `finishedAt` has no entry for 01A, P passes the filter, and every device re-imports P. 01A reappears on Continue at a weeks-old 80%, even though the device where the un-mark happened shows no position locally. The same happens after a re-mark then removal (F1, R1, P1, F2, R2) when the P1 holder has not synced between F2 and R2.
This is a design trade-off. The alternative, "a removal still suppresses ≤ R", would kill a legitimate rewatch position in (F, R]. The only precise fix is carrying the last `finishedAt` on a removed row (suppress progress ≤ F_last, not ≤ R). Decide explicitly, and document it in `merge.ts` rather than calling the resurrection "the point".

### M2. An un-mark can lose to its own mark when the mark came from a device whose clock runs ahead
`store.ts:290`, `rows.rs:112`: `removed_at = now()`. If the imported `finished_at` F > local now (another device's clock is ahead), the export is `{R < F, removed}`. The merge picks live F, and the import (`standing_at R < F`) restores the mark. The un-mark silently fails until the clocks catch up. Fix: `removed_at = MAX(now, finished_at + 1)` on both engines. The list tombstones have the same pre-existing pattern.

## Low

- **L1** `write-debounce.ts:48` `stop()` doesn't latch. A write reaching the router between `writeDebounce.stop()` (`lifecycle.ts:93`) and `server.close()` (`lifecycle.ts:100`) re-arms a 5 s timer, which then runs `exportRecord` on a closed DB. `round` catches the error, so the only result is a warning, and the timer can hold the event loop for 5 s. Add a `stopped` flag.
- **L2** Preference writes (per-device, unsynced), profile writes and no-op writes (un-marking an already un-marked title) still cost a `channel.list()` round. `pushIfChanged` suppresses the edit, so this is cheap. It becomes irrelevant once H1 narrows what touches.
- **L3** Downgrade: a pre-v8 web build (or pre-v4 core) opening a migrated DB runs no migration and reads without `removed_at IS NULL`, so tombstones show as watched. Its re-mark upsert doesn't clear `removed_at`, so after re-upgrading that title reads as un-marked. It only matters for dev rollbacks, since an Android downgrade wipes data.
- **L4** `rows_tests.rs:423` `taking_a_mark_back_never_touches_a_position` is vacuous. `set_watched(true)` already cleared the position, and the test asserts only `watched_for`, never the progress. Set progress *after* the mark, un-mark, then assert the progress is still there.
- **L5** File size: `web/src/state/store.ts` 785 lines (grew about 45), `web/src/index.ts` 372, `sync-record.ts` 267, `merge.ts` exactly 200. All were already over the limit before this change; the export and import of watched rows could move into their own module the way `lists-exchange.ts` did.

## Verified OK
- Merge symmetry: TS `keep` and Rust `tie_break::keep` are identical (`>` time, then `device >`). Device ids are ASCII UUIDs, so UTF-16 and UTF-8 ordering agree. The import `tied` predicate is the same in TS (`store.ts:770`) and Rust (`exchange.rs:141`). The shared fixtures cover both engines.
- Migrations: v7→v8 and v3→v4 are a single nullable `ALTER ADD COLUMN`, and existing rows stay live (`migration_tests.rs` asserts it). Each group runs in its own transaction.
- Readers: every watched read on both surfaces goes through `WatchState.snapshot` (`store.ts:207`) or `rows::watched_for` (`rows.rs:76`; Android through `api/state.rs:85`), and both filter `removed_at IS NULL`. There are no other SQL readers (grep `FROM watched`). Export and import are the only tombstone-aware readers.
- Overlap: `StateSync.once` joins an in-flight round and sets `again`, so a write round during a push or timer round is not dropped, and `once` never rejects.
- The debounce is null when sync is off (`index.ts:542`). Router `onWrite` fires only for state routes with status < 400, and all state mutations live in `state/routes.ts`.

## Recommended actions
1. C1: removed-wins-ties, or a separate key, in both engines. Flip the device-id tie fixture, and add a fixture that simulates an old device re-exporting live R.
2. H1: stop touching on periodic progress PUTs (or throttle), to match Android's `soon()`-on-leave.
3. Decide M1 explicitly, and apply the M2 clamp.
4. L1 and L4 are quick.

## Unresolved questions
- Is it acceptable that old devices keep showing "watched" after an un-mark until they update, or should the release note say so?
- M1: should an un-mark bring back a pre-finish position from a dormant device? That is a product decision.

**Status:** DONE_WITH_CONCERNS
**Summary:** Tombstone logic is symmetric and correct among new builds, but in a mixed fleet an old device turns a removal into a same-timestamp live mark and the device-id tie-break loses the un-mark, permanently, about half the time (C1). The write debounce also triggers a sync round and message edit every ~10 s during playback (H1).

---

## Re-review (after revision: `unwatched` key, removal-wins-ties, `lastFinishedAt`, narrowed debounce)

Tests: web `bun test` 1777 pass / 0 fail; `cargo test -p mediagram-core` all green. The shared fixtures run in both input orders on both engines (`shared-watch-state-fixtures.test.ts:84`, `shared_watch_state_fixtures.rs:104`), and 12 `unwatched` cases are in `merge.json`.

Probe method: a throwaway bun test ran the **real base-commit code** (a temporary `git worktree` at 0ae9670: old `WatchState`, `mergeStates`, `parseRecord`) as device B against the new code as device A, with both device-id orders. The probe and the temporary worktree have both been removed.

### 1. C1 end to end: fixed
Sequence: B (old) marks 01A at F and then rewatches to 120 s (P > F). A (new) un-marks at R. Three alternating rounds follow. Then B's database is reopened with the new code, which migrates v7→v8. Then B re-marks. Same result for `aaa/bbb` and `bbb/aaa`:
- A keeps the removal with no watched mark, and the rewatch position of 120 s survives through `lastFinishedAt`.
- Old B still shows 01A as watched, as expected, keeps its 120 s position, and cannot overturn the removal. B never sees R, so it keeps exporting live F, which is older than R.
- After B updates, B heals: `importUnwatched` sees live F < R and writes the tombstone, and B keeps 120 s.
- B's re-mark after that wins everywhere: A has 1 watched and 0 unwatched, and B's `unwatched` is `[]`.

### 2. Symmetry: OK
`reconcileWatched` / `merge::watched::reconcile` have the same predicate (`removed.updatedAt >= live.updatedAt`) and the same `finishedAt` source. Within each kind, `keep` still uses the identical device-id tie-break. Nothing depends on order: Rust sorts titles, TS iterates a Set, and both harnesses canonicalise the output.

### New findings

**R1 (Medium-Low). A re-mark can lose to a removal whose time was pushed into the future. This is the M2 fix moved one step along.**
Locations: `web/src/state/store.ts:283-292` and `crates/mediagram-core/src/state/rows.rs:106-111`. The mark writes `finished_at = now` with no clamp.
Scenario: a device whose clock runs ahead marks 01A at F = now+60 s. A un-marks, and the clamp sets R = F+1, which is also in the future. A few seconds later A re-marks at M = now, so M < R. The next merge sees the live mark at M and the removal at R (still held by other devices), so the removal wins. `importUnwatched` then overwrites A's re-mark (M < R) and the re-mark silently disappears.
Probe confirmed it: A ends with `watched: []` after re-marking. The window lasts as long as the clock difference.
Fix: on both engines, the mark should write `finished_at = MAX(now, COALESCE(removed_at, 0) + 1)` in the `ON CONFLICT` update. That keeps a local mark and un-mark ordered however far another device's clock was ahead.

**R2 (Low). Merge and import disagree on an exact tie between the local live mark and the removal.**
Locations: `web/src/state/watched-exchange.ts:95` and `crates/mediagram-core/src/state/watched_exchange.rs:~112`. `importUnwatched` skips the write when `standing.updatedAt >= row.updatedAt`. When the local row is a *live* mark at exactly R, the merge picks the removal (ties go to the removal) but the import keeps the live mark. The device then disagrees with the rest of the fleet indefinitely. It stays quiet (`changed` = 0 every round) but never converges.
Probe: local live mark at T plus a merged removal at T gives `changed 0` in both rounds, and the device still shows watched.
Reachability is low: after the clamp it needs a live mark on some device at exactly the ms of the removal. Fix: skip only when `standing.updatedAt > row.updatedAt`, or when the standing row is already a removal at the same time, to mirror `reconcile`.

**R3 (Low). Deleting a progress entry never triggers a round.**
Location: `web/src/routes.ts` `writeWorthSyncing`. `path.includes("/progress/")` returns true only for POST, so DELETE is excluded too. The one client caller is `markFinished` (`web/public/lib/watch-state.js:372-374`). When the title is *already* watched, as when a rewatch reaches the end, it sends only `DELETE /progress/…`. No round fires, and the cleared position reaches other devices on the 5-minute timer instead. Fix: `return request.method !== "PUT"` for `/progress/`.

### 3. Other regressions checked: none found
- A re-mark newer than the removal wins and clears the tombstone. `importWatched` upserts `removed_at = NULL`, confirmed by the probe.
- Unwatched rows grow without pruning, but at most one row per (profile, title) ever marked, the same bound as watchlist tombstones. That is not a real growth risk.
- An un-mark for a title never marked on this device is a no-op (`UPDATE … WHERE removed_at IS NULL` matches nothing). The UI can only offer un-mark for a title it shows as watched, so that case can't come from the UI.
- A device with nothing to say always exports `unwatched: []` on both engines, which old readers drop. The only effect is one extra push per device on the first round after upgrade, because the body changes once.
- An incoming removal row for a title this device has never seen inserts a tombstone and deletes only positions ≤ `lastFinishedAt`. That is correct.

### 4. Pause change in `player.js:804`: OK
- Every pause now goes through `flushProgress`, which uses `sendBeacon` (POST) with a PUT fallback. The client's local state is updated the same way as before, so resume behaviour is unchanged. Before this change a pause already sent a PUT, so the pause adds no extra requests.
- Toggling pause/resume faster than 5 s collapses into one round. Slower toggling gives one round per pause, which is bounded by user action and acceptable.
- A final save arrives as POST only through `sendBeacon`. If `sendBeacon` throws or returns false, the fallback PUT does not trigger a round, a gap the code documents. Pagehide and title close use the same function.

### File size
`store.ts` is down to 741 lines, `sync-record.ts` is 325, `merge.ts` is 208 (just over 200), and the new modules are all under 200.

### Unresolved
- Does Chromium accept `sendBeacon` with a `Blob` of type `application/json` for this same-origin POST? Older Chrome versions threw on that type. If current ones still do, every pause and pagehide save falls back to a PUT, and a pause or leaving a title never triggers the write round. Verify on the target browser and watch for a `POST /progress` in the player log.

**Status:** DONE_WITH_CONCERNS
**Summary:** C1 and H1 are fixed and verified end to end against the real old code (removal survives, old device can't overturn it, rewatch position kept, heals on update, re-mark wins). Remaining: R1, a clock-skew re-mark lost to a future-clamped removal (fix: clamp the mark to `removed_at+1`); R2, import vs merge disagreement on an exact tie; R3, a progress DELETE not triggering a round.
