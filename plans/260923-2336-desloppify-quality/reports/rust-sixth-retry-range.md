# Retry policy coverage and range contracts

Status: DONE

The two accepted findings in `.desloppify/subagents/runs/20260924_034603/holistic_issues_merged.json` are addressed without changing production behavior or public signatures. The retry wrapper now has direct closure-driven coverage, and the range helpers document their existing unchecked preconditions and failure behavior.

## Changes

- `crates/mediagram/src/telegram/retry_tests.rs`: moved the five existing retry tests here and added five cases exercising the real `with_flood_wait_only` implementation. Server 500 and dropped-transport errors each invoke the operation exactly once, return the original error type, and consume no retry delay. A FLOOD_WAIT succeeds after its server delay plus slack; distinct FLOOD_WAIT values per attempt prove budgets 0, 1, and 3 return the final error and sum only preceding waits; an ambiguous error following an allowed retry stops immediately.
- `crates/mediagram/src/telegram/retry.rs`: includes that test module and documents the already-existing zero-budget normalization. The retry algorithm and policy are unchanged; source is 92 lines.
- `crates/mediagram/Cargo.toml`: parent-authorized dev-only Tokio `test-util` feature. Effective features lacked it before this change. All async retry cases now use paused virtual time, including the two existing cases that previously slept. Production dependency features, versions, and `Cargo.lock` are unchanged.
- `crates/mediagram-core/src/range.rs`: documents ordered, representable inclusive lengths; `sent <= take`, representable resumed offsets and `u32` chunk quotients; ordered contiguous complete part coverage and arithmetic bounds. It explicitly describes incomplete plans, unchecked overflow/underflow, and narrowing casts. The adjacent `total_size` contract now states its sum bound and lack of geometry validation. Source is 175 lines; all changes are documentation.

## Regression strength

The original implementation passes all ten retry tests. An isolated mutation replaced only the flood-only predicate with `is_retryable`, which would repeat ambiguous server/transport failures. Both new single-invocation cases failed on their call-count assertions: actual 3 versus expected 1. This was an assertion failure, not a compilation failure. The mutation runner restored the original `retry.rs` bytes in `finally`, verified equality, and the restored suite passed again. No live Telegram calls or real sleeps were used.

- Mutation: `/tmp/rust-sixth-retry-mutation-red.log`, 0 passed / 2 expected failures.
- Initial green: `/tmp/rust-sixth-retry-green.log`, 10 passed.
- Restored green: `/tmp/rust-sixth-retry-restored-green.log`, 10 passed, virtual test duration 0.00s.

## Bounded adjacent-boundary audit

This was one source/test audit of the immediate retry and range callers, providing evidence for `close-api-contract-recurrence` and `stabilize-test-boundary-coverage`; it does not mutate their disposition or claim a repository-wide audit.

| Boundary | Evidence and conclusion |
| --- | --- |
| Non-idempotent sends | `upload/transport.rs`, `telegram/index_publish.rs`, and `commands/smoke_upload.rs` use `with_flood_wait_only` around `send_message`. The direct policy tests cover their shared safety decision, without substituting a fake transport for the wrapper. Uploading raw unreferenced bytes has a separate rewind/retry loop; it is not a committed message send. |
| Repeatable operations | Index publication pins only after a successful send and uses the general retry wrapper for pinning. Smoke cleanup uses it for deletion. No caller changes were needed. |
| Range construction | `serve/routes.rs` obtains ranges through response planning/`parse_range`; `api/read.rs` handles empty/past-end reads and zero length before constructing an ordered clamped inclusive range. Existing `range_plan` tests cover malformed/backwards/out-of-file ranges, cross-part reads, exact total coverage, empty input, and zero-length parts. |
| Resume accounting | `transport/fetch.rs` passes `step.take - cursor.remaining()` to `Step::after`. `StepCursor` only reduces remaining by the minimum of remaining and available bytes. Existing `stream_cursor` tests reproduce source bytes across part boundaries and resumed chunk offsets. |
| Geometry and transport limits | Normal `mlib_spec::part_plan::plan_parts` output is contiguous and bounded by `MAX_PART_SIZE`, comfortably within chunk-index limits. `catalog::part_locations` orders by part index; `PLAYABLE_SQL` checks status/count/summed length, not arbitrary imported offset geometry. Thus this report does **not** claim malformed imported catalogs are validated. The range API retains that caller precondition. The concrete grammers adapter additionally narrows to an `i32` skip count; normal part sizes fit it. |

No range test changes were needed for documentation-only edits: the existing suites already exercise the supported ordered geometry, empty behavior, and exact resumption contract. Invalid public-field construction remains unchecked as documented.

## Verification

| Command | Result | Evidence |
| --- | --- | --- |
| `cargo test -p mediagram --lib telegram::retry::tests` | 10 passed | `/tmp/rust-sixth-retry-restored-green.log` |
| `cargo test -p mediagram-core --test range_plan --test stream_cursor` | 19 + 7 passed | `/tmp/rust-sixth-range-green.log` |
| `cargo clippy -p mediagram --lib --tests -- -D warnings` | PASS | `/tmp/rust-sixth-clippy.log` |
| `RUSTDOCFLAGS='-D warnings' cargo doc -p mediagram-core -p mediagram --no-deps` | PASS | `/tmp/rust-sixth-doc.log` |
| Scoped `rustfmt --edition 2024 --check` for the three Rust files | PASS | `/tmp/rust-sixth-format.log` |
| Scoped `git diff --check` | PASS | No whitespace errors |

All owned commands exited; the Cargo slot is released. No whole-workspace gate, scanner/state update, version bump, commit, or unrelated source change was performed. After root review, the exhaustion case was strengthened with distinct error values per attempt; the final ten-test retry run and scoped formatting pass include that change. Clippy passed before this test-only assertion strengthening; strict rustdoc includes the zero-budget documentation.

Concerns: none blocking the accepted scope. Public range-field validation and live Telegram behavior are outside this bounded change; the geometry limit above remains explicit.
