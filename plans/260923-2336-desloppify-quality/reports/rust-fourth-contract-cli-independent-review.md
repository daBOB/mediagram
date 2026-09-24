# Rust contract and CLI evidence review

Status: DONE — PASS

Root-authored profile/count, dependency and upload naming changes pass independent
source review. The identified read-contract documentation correction and normal
binding regeneration are now verified. The second reviewer independently passed
the CLI implementation below.

## Findings

1. **Correct the zero-length read qualification.** `api/mod.rs` initially said
   a missing channel address produces `NotFound` even for `len == 0`.
   `api/read.rs::read` returns an empty buffer for an in-range zero-length read
   before loading channel handles. The existing
   `a_zero_length_read_at_a_nonzero_offset_returns_empty` regression also uses a
   catalog with no stored channel handle. EOF/unplayable-set checks still run
   for zero length, while channel-address checks apply only to nonempty reads.
   The controller applied the precise correction; the revised documentation
   matches source and keeps `api/mod.rs` at 200 lines. No runtime change was needed.
2. **Regenerate Kotlin bindings after API documentation settles — resolved.**
   The initial generated `mediagram_core.kt` retained the old
   BuildConfig/local.properties description. The controller's normal regeneration
   now carries the setup-owned identity and corrected read documentation, matching
   the authored Rust/CoreModule sources. Both native targets and Kotlin generation
   completed in `/tmp/rust-fourth-android-bindings.log`; the regenerated app and
   instrumentation compile gate passes in `/tmp/android-regenerated-binding-gate.log`.

## Verified contracts

- `profiles::profile_named` keeps its existing public result type and name
  normalization. The private helper reports whether it inserted the row;
  `exchange::import_merged` counts creation inside its existing transaction and
  returns the count only after commit. Existing profiles are not counted again.
  This fixes empty-profile imports without changing profile selection or naming.
- The new full sync test exercises serialization, merge, SQLite import and a
  repeat round. `SyncOutcome.pulled > 0` reaches Android WatchSync's repository
  reload, so a newly imported empty profile can refresh the picker.
  The final test delta also repeats import with a normalized variant of the
  profile name, and checks that a failed publication still reports the imported
  profile before a successful retry reports zero new imports.
- Standalone core disables grammers SQLite storage while continuing to use
  `MemorySession`; CLI still opts into default/SQLite session features. The
  supplied core/CLI feature trees confirm this distinction. No package version
  or dependency lockfile changed in this adjustment.
- Renamed upload implementations differ from their HEAD predecessors only in
  names, documentation and deprecated direct re-exports. `add`, `add-course`
  and `add-show` call the new names; legacy module/function paths and the
  `Document` type remain importable. The requested compile-only legacy-path
  probe passed and was removed. Architecture documentation uses the new names.
- Aside from the zero-length qualifier above, the read documentation matches
  range clamping, absent/unplayable sets, error propagation and rejection of
  partial download buffers. Exported Core/UniFFI signatures are unchanged.

## Evidence and independence

No tests were repeated during this review. Inspected evidence:

- `/tmp/rust-profile-count-red.log`: empty-profile import fails before the fix.
- `/tmp/rust-profile-count-green.log`: 71 state tests pass, including creation,
  repetition, normalization, rollback and failed-publication behavior.
- `/tmp/rust-core-session-features.log` and
  `/tmp/rust-cli-session-features.log`: actual feature ownership.
- `/tmp/mediagram-cli-failure-integration.log`: successful temporary legacy-path
  compile probe, without invoking upload IO.

This reviewer authored the CLI survey/ffmpeg/removal implementation. Its
[54-test report](rust-cli-failure-reassessment.md), strict package Clippy and four
failing mutation groups are implementation evidence, not a second independent
review from the same author. The controller's earlier CLI diff review identified
and corrected preservation of the original progress-read error when cleanup
also fails. The separate implementation owner is providing the independent CLI
review; this report does not substitute for it.

Concerns: none remaining after normal binding regeneration and its compile gate.
No additional runtime defect was found in the independently reviewed root changes.
Only this report was authored by the reviewer for this subtask.

## Independent CLI review — second reviewer

Status: DONE — PASS. This reviewer did not author the reviewed CLI changes.
The current source, surrounding callers, regression tests, and recorded mutation
failures were inspected; no additional actionable defect was found.

- The survey preserves contextual probe failures as unknown compatibility.
  Failures and known blockers both enter the existing confirmation decision.
  The four-probe bound, input ordering, dry-run exit before library/session access,
  nonterminal continuation, and explicit `--yes` behavior remain intact.
- Ffmpeg owns both readers inside its run future. `try_join!` drains them together;
  either read error kills/waits for the child. A cleanup failure adds context to
  the original read error rather than replacing it. Cancellation retains
  kill-on-drop and no detached stderr task. Invalid UTF-8 stderr is preserved
  lossily for the final diagnostic. Real-process tests check reaping, cancellation,
  non-UTF-8 errors, and output larger than a pipe buffer.
- Removal validates every requested set before entering the connection callback.
  Dry-run wins even with `--yes`; missing confirmation cannot reach deletion.
  The production callback preserves the apply result across shutdown. Each set's
  complete remote deletion precedes local row deletion; a failed batch retains
  recovery rows and stops subsequent sets. Message IDs are converted for the
  entire set before its first remote batch.
- Deprecated module and function re-exports preserve
  `upload::plan_set::plan_set` and
  `upload::plan_document::{Document, plan_document}`. They alias the renamed
  implementations directly, preserving type identity and signatures. All three
  command callers use the new names; the temporary external-path compile probe
  passed without invoking IO.
- The latest profile-count tests also cover normalized-name reuse and an empty
  remote profile whose first publication fails. The count is returned after the
  import transaction commits, survives failed publication, and becomes zero on
  retry. Public `profile_named` behavior and result type remain compatible.
- Final read documentation correctly separates the zero-length return from
  nonempty channel resolution. The manifest and inspected feature trees retain
  CLI SQLite session storage while standalone core uses MemorySession.

Verification: inspected `/tmp/mediagram-cli-failure-final-focused.log` (28 passed),
`/tmp/mediagram-cli-failure-final-integration.log` (26 passed), strict CLI Clippy,
the legacy-path compile log, and all four failing mutation groups. No CLI tests
were unnecessarily repeated. The latest root state changes also passed the
separate final core gate: 332 passed, one intentional network test ignored,
`/tmp/rust-foreground-session-core.log`; strict core Clippy passed.

Concerns/Blockers: none in this review slice. The controller retains the already
planned binding regeneration and workspace-wide final gates. This review changed
no CLI/core source and started no process.
