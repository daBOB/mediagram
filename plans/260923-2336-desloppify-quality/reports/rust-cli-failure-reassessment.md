# Rust CLI failure-path reassessment

Status: DONE

## Diagnosis and scope

The controller approved this scope after source inspection, before edits.

- `error_consistency::compatibility_survey_discards_probe_failures`: the
  survey converted probe errors to `None` with `.ok()?`, omitting unknown
  compatibility from both the report and the confirmation decision.
- `error_consistency::ffmpeg_progress_failure_skips_child_cleanup`: a progress
  read error returned before waiting for the child; cancellation also dropped
  a child without killing it. The separately spawned stderr reader detached.
- `test_strategy::removal_orchestration_missing_regression_tests`: production
  guards and remote-before-local ordering existed, but their orchestration was
  not covered by the planning and standalone row-deletion tests.

Permanent authored changes are restricted to these ten Rust files:

- `crates/mediagram/src/commands/add_show/mod.rs`
- `crates/mediagram/src/commands/add_show/survey.rs`
- `crates/mediagram/src/commands/add_show/survey_tests.rs`
- `crates/mediagram/src/commands/remove.rs`
- `crates/mediagram/src/commands/remove_tests.rs` (new)
- `crates/mediagram/src/media/ffmpeg_progress.rs`
- `crates/mediagram/src/media/ffmpeg_progress_tests.rs`
- `crates/mediagram/src/remove/apply.rs`
- `crates/mediagram/src/remove/apply_tests.rs` (new)
- `crates/mediagram/tests/add_show_survey.rs` (new)

The controller's accepted upload naming change is integrated in the owned
`add_show/mod.rs` import and call to `prepare_and_record_set`.

## Resulting behavior

The survey records blocked files separately from contextual probe failures.
Failures identify the source path and preserve the error chain; the report says
their browser compatibility is unknown. Both blockers and failures enter the
existing confirmation flow. Dry runs still report without opening a library or
session. Nonterminal continuation and `--yes` behavior are preserved. Existing
four-process concurrency and input ordering remain intact.

Ffmpeg stdout and stderr are polled concurrently within the owning future,
with no spawned stderr task. A read error explicitly kills and waits for the
child before returning. If cleanup itself errors, its diagnostic is attached
to the original read error, nonblocking reaping is attempted, and the
kill-on-drop fallback remains active. Cancellation drops both readers and
kills the child; Tokio reaps it. Stderr is drained as bytes, preserving the
last diagnostic even when it includes invalid UTF-8.

Removal has private callback boundaries around the existing connection/apply
step and remote batch dispatch. The production callback still connects only
after the guards and shuts down after either apply result. The shared apply
loop retains remote-before-local ordering and stops on the first failure.
Existing public deletion functions and retry policy are unchanged.

## Behavioral evidence

- Real ffprobe fixtures cover playable, blocked, and failed probes. Actual CLI
  executions check unknown-compatibility output in dry runs and entry into
  nonterminal confirmation. A deliberate temporary library-path error stops
  the latter command before any upload or connection.
- Real child processes emit malformed progress, block until cancellation,
  produce invalid UTF-8 stderr, or fill more than one stderr pipe. Error-return
  checks require the child to be reaped already. Cancellation checks require
  `kill(pid, 0)` to return `ESRCH`; a zombie would still be visible. Tests have
  bounded waits and explicitly clean up their own children on assertion failure.
- Real temporary SQLite libraries cover dry-run with and without `--yes`,
  missing confirmation, validation of every requested set before connection,
  and connection-error propagation. Their connection/deletion callback must
  not run before the guards, and existing set/part rows remain intact.
- Real removal plans and SQLite rows cover ordered batches of 100, 100, and 5
  messages before local deletion. A later set's second remote batch fails after
  an earlier set completed: failed-set recovery rows remain and subsequent sets
  are untouched. Out-of-range message IDs prevent even the first remote batch.
  Existing `remove_plan` tests also verify cascaded parts, assets and metadata.

## Regression strength

Each temporary mutation was applied only to owned source and restored in a
`finally` block. Each compiled and failed the intended behavioral tests:

| Temporary defect | Observed regression failures | Log |
| --- | --- | --- |
| Discard probe failures | Both failure-retention survey cases | `/tmp/mediagram-cli-survey-mutation.log` |
| Remove explicit cleanup and disable kill-on-drop | Progress-error reaping and cancellation reaping | `/tmp/mediagram-cli-ffmpeg-mutation.log` |
| Bypass dry-run-with-yes and missing-yes guards | Both destructive guard cases | `/tmp/mediagram-cli-remove-guards-mutation.log` |
| Delete local rows before remote requests | Batch-order and partial-failure cases | `/tmp/mediagram-cli-remove-order-mutation.log` |

## Final validation

All four mutations are restored. Final checks passed:

```text
cargo test -p mediagram --lib -- commands::add_show::survey media::ffmpeg_progress commands::remove remove::apply upload::
28 passed, 0 failed

cargo test -p mediagram --test add_show_survey --test remove_plan --test code_standards --test prepare_report --test prepare_probe --test prepare_check
26 passed, 0 failed

cargo clippy -p mediagram --all-targets -- -D warnings
passed
```

Logs: `/tmp/mediagram-cli-failure-final-{focused,integration,clippy}.log`.
Clippy first found one unnecessary error conversion; it was removed, the seven
removal tests reran successfully, and strict Clippy then passed. That focused
rerun is `/tmp/mediagram-cli-failure-final-remove.log`.

The initial compile attempt encountered a lifetime error in concurrent core
work; its owner corrected it before these successful gates. No checks were
disabled or failures suppressed.

A temporary integration compile probe also passed for
`upload::plan_set::plan_set` and `upload::plan_document::{Document,plan_document}`.
It imported and type-checked the old API paths without invoking IO; deprecated
use was allowed only in that probe. The probe file was removed afterward.
Evidence remains in `/tmp/mediagram-cli-failure-integration.log`.

Owned rustfmt and whitespace checks pass. The five production modules are
121, 176, 193, 77, and 93 lines; all remain below the 200-line limit. Process
reconciliation found no remaining owned fixture children. The Cargo slot was
released to the core worker after checks completed.

No manifests, versions, dependencies, commits, scanner state, live Telegram
operations, or user data were changed by this task. Controller-owned upload
renaming and other workers' changes were preserved.

Concerns/Blockers: None. The controller owns final review and finding disposition.
