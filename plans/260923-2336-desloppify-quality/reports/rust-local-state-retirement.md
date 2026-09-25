# Native local-state retirement

Status: DONE

## Changes

`StateDb` now holds `Unopened`, `Open(Connection)` or terminal `Retired` under
its existing connection mutex. Retirement acquires that same mutex, waits for
any active database action, replaces the state and drops an open connection.
Every subsequent `with` returns `None` without invoking its closure or opening
storage. Poisoned locks are recovered only for retirement so their connection
is still closed; ordinary reads retain their existing safe failure behavior.

The new synchronous `Core::retire_local_state()` export documents the boundary:
call before clearing local files or releasing the native wrapper, because queued
blocking tasks own separate `Arc<Core>` references. Retirement leaves persisted
files intact for legitimate core replacement. There is no network wait or broad
asynchronous task drain.

Owned changes:

- `crates/mediagram-core/src/state/mod.rs` — 181 lines.
- `crates/mediagram-core/src/state/retirement_tests.rs` — new focused unit tests.
- `crates/mediagram-core/src/api/state.rs` — 198 lines, additive export only.
- `crates/mediagram-core/tests/state_retirement.rs` — new actual API regression.
- `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` —
  generated normally, exposing synchronous `retireLocalState()` plus its FFI and
  checksum entries.

## Regression evidence

The API tests use a real single-slot Tokio blocking pool. A controlled worker
holds that slot while the actual `Core::create_profile` future is polled until
its Arc-owned job is queued. The test retires and releases the old owner,
optionally deletes its previously opened database directory, releases the slot,
and verifies that the queued write returns `None` without recreating storage.
Dropping the release sender on an assertion failure unblocks the worker, so
runtime cleanup cannot strand it.

A third API case verifies that a replacement core reads the preserved profile
while the retired core can neither write nor reopen its state. Unit cases cover
unopened retirement, connection closure with data preservation, idempotence,
and retirement after a database-action panic poisoned the mutex.

Two bounded mutations reproduced the failure, with exact source bytes restored
in `finally` before final checks:

- Omitting the export's retirement call reproduces old wrapper-release behavior:
  the queued write creates a profile in previously absent storage. **1 failed**;
  `/tmp/rust-local-state-unopened-red.log`.
- Closing the connection but resetting it to `Unopened` instead of `Retired`
  lets old-owner writes reopen storage, including after directory deletion.
  **3 failed**; `/tmp/rust-local-state-terminal-red.log`.

Restored verification:

| Command | Result | Log |
| --- | --- | --- |
| `cargo test -p mediagram-core --lib state::` | 74 passed | `/tmp/rust-local-state-restored-green.log` |
| `cargo test -p mediagram-core --test state_retirement --test api_surface` | 11 passed | `/tmp/rust-local-state-api-verified.log` |
| `cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings` | Passed | `/tmp/rust-local-state-clippy.log` |
| Explicit-file `rustfmt --edition 2024 --check` | Passed | `/tmp/rust-local-state-fmt.log` |

Initial narrow runs also passed all three API tests and all three retirement
unit tests in `/tmp/rust-local-state-retirement-api-green.log` and
`/tmp/rust-local-state-retirement-unit-green.log`. Scoped whitespace checks pass.

## Bindings and integration

`scripts/generate-android-bindings.sh` completed successfully, rebuilding
arm64-v8a and x86_64 release libraries and generating Kotlin from the former.
Log: `/tmp/rust-local-state-bindings.log`. The generated diff adds only the new
method, documentation and required FFI/checksum declarations.

The Android owner received binding-ready notification before compilation. That
worker owns calling `retireLocalState()` from `DefaultCoreClient.close()` before
UniFFI release, provider reset ordering, and corresponding Android verification.
This report claims the native boundary and regenerated binding, not completion
of that separate adapter integration.

Independent reviewer `/root/rust_commits` inspected the retirement fence,
poison handling, actual queued API regressions, generated binding and Android
adapter, plus the mutation and green logs, and reported **PASS with no defect**.

No live Telegram, user database, manifest, scanner or git-state mutation was
used. All owned test/build processes exited. Root owns coordinated
cross-platform checks.
