# Rust contract and dependency review

Status: DONE_WITH_CONCERNS

Independent read-only review of the profile import count, public read documentation, core session features, and upload entrypoint renames. No runtime correctness defect found in the inspected changes. No Cargo commands were run because another worker owned the build slot; verification below distinguishes inspected logs from source review.

## Findings and integration requirements

1. **Preserve the exported Rust upload paths.** `mediagram::upload` is public, so removing `plan_set` and `plan_document` would break Rust callers despite preserving CLI behavior. Root accepted direct deprecated module/function re-exports. This is a suitable compatibility repair: it preserves `upload::plan_set::plan_set` and `upload::plan_document::{Document, plan_document}` without another implementation. All application callers should use the new names. This repair was agreed but not yet present at review completion. Validate both legacy paths with a compile-only integration test using a local deprecation allowance, and check normal CLI compilation and warning-denied rustdoc.
2. **Finish the owning documentation and generated bindings.** `docs/system-architecture.md:50` still names `upload::plan_set`. Generated Kotlin still carries the old BuildConfig identity description at lines 1583 and 1785. Root owns the architecture correction and normal binding regeneration after concurrent API edits settle. These are pending integration work, not defects in generated runtime behavior.

## Verified behavior

- `profile_named` retains its public `Result<Option<String>>` signature and existing normalization/display-name behavior. The new helper is restricted to the state module and returns whether this call inserted the profile. Import adds one change for that row and counts nothing on reuse.
- Profile creation still occurs inside the same import transaction as all imported marks. Errors cannot return an apparently committed count; the existing failed-import and failed-push cases continue to distinguish rolled-back imports from committed imports whose publication failed.
- The count now reaches the actual `SyncOutcome.pulled` boundary. Android `WatchSync` reloads the repository when `pulled > 0`, so a profile containing no marks can refresh the picker. The new full-round test exercises real serialization, merge, import, and repeated-round behavior through a fake channel IO boundary.
- The read documentation matches the existing range implementation: absent/unplayable sets and EOF are refused, in-range zero-length requests return empty without network access, reads clamp to EOF, and a failed transport/drain does not return its partial buffer. No read signature or implementation changed.
- Core uses `MemorySession` with its existing auth-key persistence on all platforms. CLI uses `SqliteSession` in its Telegram client and SQLite initialization helper. The supplied feature trees show no `grammers-session` default/`sqlite-storage` feature for standalone core, while CLI still enables both. Workspace feature unification can still enable SQLite when CLI is built, as intended.
- Comparing both new upload files to their HEAD predecessors after replacing the function names produced empty diffs. The renamed production imports and calls resolve consistently; no remaining source callsites use the old names. The only stale authored documentation reference found was the architecture line above. Concurrent add-show compatibility changes were outside this review.

## Test evidence and limits

- `/tmp/rust-profile-count-red.log`: the new empty-profile import regression fails before the fix, returning `Some(0)` instead of `Some(1)` (0 passed, 1 failed).
- `/tmp/rust-profile-count-green.log`: 71 state tests passed, 0 failed, including the new import and full-sync regressions, existing name-normalization coverage, rollback coverage, and failed-publication coverage. Integration binaries were filtered out in this focused run.
- `/tmp/rust-core-session-features.log` and `/tmp/rust-cli-session-features.log`: inspected the actual core and CLI feature trees.
- No fresh build, lint, rustdoc, Android regeneration, or legacy-alias compilation was performed by this reviewer. Those gates remain with the coordinating agent. No production, test, configuration, scanner state, or live data was modified during the review.

Concerns: completion requires the accepted legacy aliases and normal documentation/bindings/build integration gates above; no additional runtime blocker found.
