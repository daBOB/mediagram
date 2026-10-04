# A8: far-future stamps on every other synced row type (core and web)

Status: DONE. Fix commit `84f061b9` on branch `worktree-agent-a6afa7ad52f148d84`, based on `e87bd0ca`. Not pushed. No version bump.

## 1. Every stamp the sync record parses

Parsers: core `state/record/{parse,list_record,preference_record,stats_record}.rs`; web `state/{sync-record,preferences-record,stats-record}.ts`. Both engines parse the same fields.

| Row (key) | Stamp field(s) | Before | Now |
|---|---|---|---|
| document | `writtenAt` | unbounded (NaN → 0) | unchanged, see below |
| `progress` | `updatedAt` | finite, > 0 only | **≤ 2^53−1** |
| `watched` | `updatedAt` | ≤ 2^53−1 (d7319e44) | same check, shared helper |
| `unwatched` | `updatedAt`, `lastFinishedAt` | ≤ 2^53−1 (d7319e44) | same check, shared helper |
| `watchlist` | `updatedAt` | finite, > 0 only | **≤ 2^53−1** |
| `collections` | `updatedAt` (one stamp for the whole list; `items` carry none) | finite, > 0 only | **≤ 2^53−1** |
| `kids` (household) | `updatedAt` | finite, > 0 only | **≤ 2^53−1** |
| `editorsChoice` (household) | `updatedAt` | finite, > 0 only | **≤ 2^53−1** |
| `preferences` | `updatedAt` | safe integer ≥ 1 | unchanged |
| `titleStats` | `startedAt`, `lastWatchedAt`, `againAt`, `updatedAt` | ≤ 2^53−1 (824dc99e) | unchanged |
| `dayStats` | `updatedAt` | ≤ 2^53−1 (824dc99e) | unchanged |

The following fields are numbers but not stamps, and are out of scope: `progress.at` and `progress.duration` (seconds), `titleStats.seconds`, and `dayStats.seconds` (already capped at 86 400). None of them decides a merge.

`writtenAt` is left alone. Nothing reads it during a merge (`sync.rs:163`, `sync.ts:143`), so a far-future value wins nothing. Dropping the whole document over it would lose good rows.

**Own-write `+ 1` paths.** None of the newly bounded rows has one. Progress, watchlist, Kids, editor's choice and collection writes all stamp with a plain `now` in both engines (`rows.rs`, `lists.rs`, `editors_choice.rs`, `store.ts`, `stats-recorder.ts`). The only `+ 1` in `lists.rs:118` and `store.ts:645` is an item *position*. The `+ 1` stamp paths that do exist (watched, stats, preferences) were already bounded by d7319e44, 824dc99e and the preference parse.

## 2. RED

**Shared fixture:** `web/test/fixtures/watch-state/record-parse.json` has two new cases.

- Profile rows: `progress`, `watchlist` and `collections`.
- Household rows: `kids` and `editorsChoice`.

Each row type is tested at 2^53−1 (kept) and at 2^53, 2^53+1, i64::MAX, `1e300` and the string `"9007199254740992"` (all dropped). Both engines run the same file.

**Sync-round tests** go through the real path: the peer's JSON text goes through `parse_record`/`parseRecord`, is merged with this device's freshly built export, then `import_merged`/`importMerged`. In each test the peer row is synced in, this device makes a later real edit, and the same peer row is synced in again. The stamps are 2^53, i64::MAX and 1e300.

Core: `crates/mediagram-core/src/state/exchange_tests.rs` (+5 tests). On `e87bd0ca`, all 5 failed for every stamp. I checked i64::MAX and 1e300 one at a time as well, because the loop stops at the first stamp that fails.

| Test | Output on main |
|---|---|
| `a_peer_position_past_the_safe_range_cannot_outrank_a_later_one` | `left: [5.0] right: [900.0]` |
| `a_peer_watchlist_mark_past_the_safe_range_cannot_outrank_a_later_removal` | `left: ["01A"] right: []` |
| `a_peer_collection_past_the_safe_range_cannot_outrank_a_later_rename` | `left: [("Stolen", ["01A"])] right: [("Mine", [])]` |
| `a_peer_kids_mark_past_the_safe_range_cannot_outrank_a_later_removal` | `left: ["01A"] right: []` |
| `a_peer_editors_choice_past_the_safe_range_cannot_outrank_a_later_pick` | `left: Some("01B") right: Some("01A")` |
| `record_parse_fixtures_match_the_web` (shared) | failed: `a position, watchlist or collection time beyond 2^53 - 1 drops the row…` |

Web: new `web/test/state-synced-stamp-bounds.test.ts` has the same five scenarios, each run for all three stamps. On `e87bd0ca`, **17 failed**: all 15 sync-round tests and both new fixture cases.

- Positions came back as `[5]`.
- The watchlist and Kids still held `["01A"]`.
- The collection came back as "Stolen" with the peer's item.
- The editor's choice came back as `"01B"`.

## 3. Fix

- **One helper per engine.** `is_watched_stamp` (`parse.rs`) became `is_stamp` in `record.rs`, next to `MAX_STAMP`. It is private to `record` and reached by its child modules. `isWatchedStamp` became `isStamp` in `sync-record.ts`. No new copy of the constant.
- **Callers:** `progress_row`/`progressRow`, `list_row`/`listRow` (watchlist, Kids, editor's choice), `collection_row`/`collectionRow`, and the existing watched/unwatched rows.
- **Drop, not clamp.** A clamped row would still outrank every real edit.
- **Unchanged below the bound.** NaN and ±Inf are still rejected, and every stamp ≤ 2^53−1 is accepted as before.

## 4. GREEN

- `cargo test -p mediagram-core`: **631 passed, 0 failed**, 1 ignored. It was 626 at d7319e44; the 5 new tests account for the difference.
- `cargo clippy --all-targets --all-features -- -D warnings`: clean.
- `cargo test -p mediagram --test code_standards`: passes. `record.rs` is 158 lines, `parse.rs` 162, `list_record.rs` 84; `rows.rs` was not touched.
- web `bun run lint`: clean. `bunx tsc --noEmit -p .`: clean. `bun test`: **2653 pass, 0 fail**, 197 files.
- **Line cap:** `sync-record.ts` is now **328/328**, exactly at its ceiling. It did not go over, so no split was needed. The next change to that file will need one; the natural seam is the row parsers (`progressRow` … `collectionRow`), moved into their own module.

## 5. Android / uniffi

**No binding change.** No `uniffi::Record` type and no exported function was touched. `is_stamp` is private, and `rows.rs`, `lists.rs` and `editors_choice.rs` are untouched. The generated Kotlin does not need regenerating. Android has no sync-record parser of its own outside tests. **The fix reaches Android only with a native-core rebuild** (`scripts/build-android-core.sh`), because the parse lives in the `.so`.

## Concerns

- **Already-poisoned local rows are not repaired.** This is the same position A2 took.
  - A row an older build took in stays in that device's DB and in its own export. Peers on this build now drop it.
  - On the poisoned device itself, the next local edit replaces it: progress, rename and pick overwrite outright, and removals set their own stamp.
  - One exception: a watchlist or Kids row that is live at a far-future stamp and gets re-added locally keeps its stamp (`WHERE removed_at IS NOT NULL`). It still exports live, which is the same answer.
- **2^53−1 itself is accepted.** That is consistent with watched, stats and preferences. The bound keeps the integers exact; it does not judge whether a time is plausible.
- `sync-record.ts` is at its line cap; see the line-cap note in GREEN.

## Unresolved questions

None.
