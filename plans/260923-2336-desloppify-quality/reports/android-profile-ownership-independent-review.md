# Independent profile and reset ownership review

Status: DONE

Verdict: **PASS** on the final reviewed source. No unresolved actionable defect remains in this scope.

## Scope and resolved findings

Reviewed the pending repository, profile ViewModel, Settings/Setup reset changes and their focused tests/fake adaptations. Review expanded, with root authorization, to the provider reset boundary, native local-state retirement, generated Kotlin method, and `DefaultCoreClient.close` adapter.

The independent review found two additional concrete races and verified their corrections:

1. An old choice could finish its snapshot after a newer choice. The repository discarded the obsolete snapshot, but the old successful return still changed `ProfileViewModel` to the old name; a late failure could similarly replace the newer screen with an error. The final ViewModel uses operation ownership for success and failure, including settle, add, reopen and stay. Real ViewModel plus real repository tests reproduce late success, late failure and A→B→A failure. They failed before correction in `/tmp/android-profile-review-red.log`.
2. A reload queued between two choices could borrow the newer queued request's publication revision, publish the intermediate choice, and invalidate the newer choice's acknowledgement. The final repository separates request ordering from publication revision. It advances the publication revision only inside the choices mutex, immediately before persistence, and acknowledges before unlocking. Snapshot IO remains outside that mutex. The exact queued schedule also failed in `/tmp/android-profile-review-red.log` before repair.

Root's earlier persisted-choice/reload acknowledgement regression is retained. The source now serializes that metadata read with choice persistence and acknowledgement.

## Verified invariants

- [WatchStateRepository](../../../android/core/data/src/main/kotlin/WatchStateRepository.kt) checks core identity and selection revision before publishing suspended reads or mutation snapshots. Returning to the same profile cannot revive an older generation. Reset invalidates pending reads and profile creation, and a request waiting for its core cannot overtake a newer request. Publication locking contains no suspending IO; mutex ordering has no inverse acquisition path.
- [ProfileViewModel](../../../android/feature/catalog/src/main/kotlin/profile/ProfileViewModel.kt) stops and expires its shared state immediately when the last collector leaves. The retained instance re-runs its existing reconciliation on rapid re-entry. Cancellation remains cancellation, and obsolete completion tokens do not change the new entry's mode.
- [SettingsViewModel](../../../android/feature/setup/src/main/kotlin/SettingsViewModel.kt) and [SetupViewModel](../../../android/feature/setup/src/main/kotlin/SetupViewModel.kt) invalidate retained profile state only after the reset operations succeed and before completion/navigation. The failed-storage-reset control emits no success and retains its recovery path.
- [CoreProvider](../../../android/core/data/src/main/kotlin/CoreProvider.kt) excludes rebuilding while unpublishing/closing the old client and clearing account files. Sign-out retains device credentials. A failed close remains privately owned, blocks rebuilding while it still fails, and is retried before deletion. Cleanup completes under `NonCancellable`; cancellation is still delivered to the caller afterward.

The worker correctly identified that closing an idle retained core was necessary: native `StateDb` owns its connection separately from the session state reset by sign-out. This reviewer then verified a further concrete late-open schedule: an Arc-owned `create_profile` task queued on `spawn_blocking` could run after handle release and file removal, lazily reopen `state.db`, and persist the old caller's data.

The authorized [StateDb retirement fence](../../../crates/mediagram-core/src/state/mod.rs) fixes that cause. Under the existing connection mutex, retirement drops any connection and installs a terminal state. Later operations return their established fallback without opening files. It recovers a poisoned guard only to retire and close; ordinary poisoned reads retain their prior fallback behavior. Already-running database work completes before retirement acquires the lock. Files remain intact for a replacement core.

[Core.retire_local_state](../../../crates/mediagram-core/src/api/state.rs) exposes that bounded operation. The normal generated Kotlin binding includes the method and checksum. [DefaultCoreClient.close](../../../android/core/data/src/main/kotlin/DefaultCoreClient.kt) synchronizes retirement before handle release, preserves idempotency using the generated destroyed flag, and leaves the handle available if retirement fails. Its tests use the generated `Core(NoHandle)` extension point rather than a new production seam.

## Verification inspected

No duplicate Gradle/Cargo runs were performed by this reviewer. The final source, controlled interleaving tests, and complete relevant log results were inspected:

- Original ownership/reset red logs establish four stale publication failures, two successful-reset invalidation failures, rapid retained-VM re-entry failure, and the root acknowledgement race.
- `/tmp/android-profile-review-red.log`: four additional race assertions fail before correction.
- `/tmp/android-profile-core-retention-red.log`: real-provider sign-out fails to release the retained core before reloading.
- `/tmp/android-profile-adapter-red.log`: two adapter assertions fail before retirement wiring; `/tmp/android-profile-adapter-green.log`: both pass afterward.
- `/tmp/android-profile-consumers-green.log`: **364 passed, zero failures/skips**, including data 111, setup 58, catalog 108, system 20, player 57 and mobile integration 10; app Kotlin/Hilt compilation passed.
- `/tmp/android-profile-format.log` and `/tmp/android-profile-adapter-format.log`: scoped formatting clean. Scoped diff whitespace checks clean.
- `/tmp/rust-local-state-unopened-red.log`: removing the export's retirement call makes the actual queued old-owner write succeed incorrectly. `/tmp/rust-local-state-terminal-red.log`: reopening after retirement makes all three API regressions fail. The native worker restored the exact source afterward.
- `/tmp/rust-local-state-restored-green.log`: 74 state unit tests pass. `/tmp/rust-local-state-api-verified.log`: 3 real queued/replacement retirement API tests and 8 API surface tests pass. `/tmp/rust-local-state-clippy.log`: strict all-target/all-feature Clippy passes; explicit changed-file rustfmt passes.
- `/tmp/rust-local-state-bindings.log`: normal arm64-v8a and x86_64 release generation completed, followed by Kotlin generation. Android consumer compilation verifies the generated API matches its call site.

## Limits

This is a state-specific retirement fence, not a general native task drain. Other in-flight native work may finish separately; the updated close contract says so. Android tests exercise real orchestration with controlled core/native boundaries, while the Rust regressions exercise the actual native API and blocking queue. No live Telegram or production user data was accessed, and no device end-to-end JNI runtime test is claimed. Root owns the final broad gates.

Only this report was written for the independent review. Concerns: None within the accepted scope.
