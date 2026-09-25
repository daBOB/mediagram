# Typed failures within watch-state synchronization

Status: DONE

## Change

`state/sync/error.rs` defines the private `SyncError<E>` boundary: it retains the original channel error and `serde_json::Error`, and labels unavailable local import/read operations with fixed variants. `round` and `publish_state_if_changed` return that type. Only `once`, where `SyncOutcome.failed` is constructed, converts the failure to a string.

`merge_and_import_documents` now returns `StateDb.with`'s existing `Option<u64>` directly; `round` labels `None` as an import failure. `StateDb` already logs and discards its SQLite/opening causes, so this change neither invents a missing cause nor changes the database API. Existing local failure messages remain exactly `the local state could not be imported` and `the local state could not be read`. Channel and serialization display text remains transparent.

There is no change to public/wire shapes, the `StateChannel::Error: Display` bound, import transactions, committed pulled counts, publication order, memo assignments, retry policy, or locking. Existing user/controller changes were preserved. Production files are 185 lines (`sync.rs`) and 15 lines (`sync/error.rs`).

## Evidence

- Existing focused suite passed before adding the new checks: 22 sync tests (`/tmp/sync-typed-errors-first.log`).
- Added a test channel whose error borrows a local render counter and implements neither `Debug` nor `std::error::Error`. Its original error code survives both the list-failure and put-failure paths from `round`, with zero formatting calls. `once` renders the same error exactly once. This also verifies no stronger Debug, Error or static-lifetime bound was added and failed calls do not update send memo state.
- Added a real inaccessible local path fixture: public import failure and private publish/read failure retain their distinct messages, do not publish, and leave the occupying file unchanged. The existing SQL rollback regression now asserts the exact public import message.
- `cargo test -p mediagram-core state::sync::tests --lib`: **24 passed**, including rollback, committed-import counts after failed send, future retry, memo suppression, wire format, and production lock concurrency. Log: `/tmp/sync-typed-errors-verified.log`.
- `cargo clippy -p mediagram-core --all-targets -- -D warnings`: **passed**. Log: `/tmp/sync-typed-errors-clippy.log`.
- Focused rustfmt check and `git diff --check`: **passed**.

No test-only serializer seam was introduced to force an otherwise unavailable serialization failure. The concrete serde error is retained by the typed conversion and transparent display implementation. No scanner action, project commit, network connection, user database access, or lingering process was created. Controller owns independent review and queue resolution.
