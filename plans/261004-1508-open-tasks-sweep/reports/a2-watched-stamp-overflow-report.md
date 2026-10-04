# A2: `watched` finish-stamp overflow (core and web)

Status: DONE. Fix commit `335873d0` on branch `worktree-agent-a9f175435056842d2`. Not pushed, no version bump.

## Failure, reproduced first (RED)

Every test below goes through the real sync path: `parseRecord` on a peer's JSON text, merged with this device's freshly built export, then `importMerged`. Stamps tested: 2^53, 2^53+1 and i64::MAX (written as JSON text), plus a local row that is already saturated.

**Core** (`cargo test -p mediagram-core --lib watched_exchange`: 6 pass, 4 fail)

| Test | Output on main |
|---|---|
| `a_peer_stamp_past_the_safe_range_is_not_taken_in` | `left: [WatchedRow { set_id: "01A", finished_at: 9007199254740992 }] right: []`. **Wrong winner**: the peer's mark at 2^53 beat this device's real removal. The same import also tombstoned the 01C position. |
| `a_peer_stamp_past_the_safe_range_cannot_stop_this_devices_document` | `9223372036854775807, finished false: Invalid column type Real at index: 2, name: removed_at`. **Export stops**: `as i64` saturated the import, the next own un-mark's `finished_at + 1` overflowed into a REAL, and every `export_record` after that failed. |
| `own_writes_on_a_row_already_at_the_largest_integer_stay_integers` | `left: ["integer/real"] right: ["integer/integer"]`. **Overflow**: `+ 1` on i64::MAX stored a REAL. |
| `a_row_already_holding_a_real_stamp_still_exports_and_lists` | `the document is still written: InvalidColumnType(1, "finished_at", Real)` |
| shared `record_parse_fixtures_match_the_web` (new case) | failed: rows past 2^53−1 were parsed and kept |

**Web** (`bun test`: 5 fail)

- `a peer's watched time of 9007199254740992 / …993 / 9223372036854775807 is not taken in`: all three failed. `watched` came back as `[{ setId: "01A", finishedAt: 9007199254740992 }]` (and `9223372036854776000` for the largest), the same wrong winner as the core.
- `an own write on a row already past the safe range mints a time within it`: `Expected: <= 9007199254740991, Received: 9223372036854776000`.
- `record-parse fixtures > a watched or unwatched time beyond 2^53 - 1 drops the row; 2^53 - 1 itself is kept`: failed.

## Fix

Same shape as the stats fix (`824dc99e`):

- **Parse boundary, both engines.** A `watched`/`unwatched` row with `updatedAt` or `lastFinishedAt` above 2^53−1 is dropped (`parse.rs::is_watched_stamp`, `sync-record.ts::isWatchedStamp`). 2^53−1 itself is kept. A shared fixture case in `record-parse.json` pins this, so both engines must agree.
- **`+ 1` cannot overflow, both engines.** `set_watched`/`setWatched` now compute `MIN(x, MAX_STAMP - 1) + 1`, so an own write never stores a REAL and never mints a stamp that a peer would drop.
- **Export survives a bad row (core).** `export_watched` and `standing_for` read stamps as `f64`, which accepts INTEGER and REAL alike. `watched_for` reads `CAST(finished_at AS INTEGER)`, so a REAL comes back saturated to i64::MAX instead of failing the whole list. The achievements code already handles i64::MAX (`6ede0945`). The web needs none of this because bun reads REAL fine.
- **DRY.** `MAX_STAMP` now lives once in `record.rs`, and the private copies in `stats_record.rs` and `preference_record.rs` are gone.

Merge rules are unchanged for every stamp ≤ 2^53−2. Reconcile and tombstone code was not touched. The only change in behaviour is at the ceiling: a row stamped exactly 2^53−1 can no longer step past it. An un-mark ties at 2^53−1, and a tie goes to the removal, which is the existing rule. No real clock gets there (that would be the year 285 616).

## GREEN

- `cargo test -p mediagram-core`: 626 passed, 0 failed
- `cargo clippy --all-targets --all-features -- -D warnings`: clean
- `cargo test -p mediagram --test code_standards`: passes. `rows.rs` is at 199/200 lines.
- web `bun run lint`: clean. `bun run typecheck`: clean. `bun test`: 2633 pass, 0 fail. `sync-record.ts` is at 327/328 and `store.ts` at 791/800 in the line-limit ratchet.

## Android / uniffi

**No binding change.** `rows::WatchedRow`, `watched_for` and `set_watched` keep their signatures, and `MAX_STAMP` is `pub(crate)`. The generated `mediagram_core.kt` does not need regenerating. **The fix ships to Android only with a native-core rebuild** (`scripts/build-android-core.sh`), because the parse, the clamp and the readers all live in the `.so`.

## Concerns / follow-ups

- **Same unbounded peer stamps elsewhere.** The parsers for `progress`, `watchlist`, `kids`, `collections` and `editorsChoice` still accept stamps above 2^53−1. None of them does `+ 1` arithmetic, so nothing overflows, but a far-future peer stamp would still beat every later edit of that row ("poison"). That is the same class of problem, out of scope here.
- **Already-poisoned local rows are not repaired.** A row an older build saturated stays in place, and its own export carries it. Peers now drop it on parse. In this device's own merge, a poisoned live mark still tombstones that title's positions until the title is re-marked, which resets `finished_at` to now. I chose not to add a repair migration.
- **2^53−1 is a valid stamp but still far in the future.** Accepting it is consistent with the stats and preferences boundaries. The bound only keeps the arithmetic and the JSON exact; it does not judge whether a time is plausible.
- `web/node_modules` was installed in this worktree (`bun install --frozen-lockfile`) so the web suite could run. It is gitignored.
