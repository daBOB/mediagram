# Android storage and identity-error independent review

Status: DONE — PASS

No actionable finding in the scoped documentation and identity-error changes.
Production/test review was read-only; this report is the only authored file.

## Identity failure handling

- `SettingsViewModel.kt:131` catches cancellation separately, preserving its
  existing propagation. Every ordinary replacement failure is wrapped with the
  fixed neutral sentence, without inferring Telegram rejection or network
  reachability from a broad exception category.
- `StoredCoreProvider.replace` can fail while closing the old client, building
  its replacement, verifying the account or persisting credentials. Those
  boundaries substantiate the neutral message. Success completion is emitted
  only after replacement returns; a failure leaves the form available and the
  action's `finally` clears busy state.
- `SettingsFailure` passes the original exception to `Exception(sentence, cause)`.
  Candidate cleanup failures attached by `CoreProvider.discard` remain reachable
  on that cause chain. The wrapper no longer discards the original context.
  This preserves throwable context during handling; it does not add an error
  logging or persistence mechanism.
- `SettingsViewModel.kt:181` chooses `SettingsFailure.sentence` before calling
  `coreSentence`. The identity failure therefore cannot expose the raw native,
  keystore or RPC message, its cause, or a suppressed cleanup error in UI state.
  The new exact-notice assertions verify the fixed message for all four failure
  boundaries. No hash, path or original exception is interpolated into it.

## Storage contract and caller recovery

- `CoreStorage.kt:34` now describes actual behavior: session, catalog, library
  handles and SQLite state/sidecars are deleted sequentially. An unsuccessful
  deletion throws after any earlier successful deletions, so reset is not atomic.
  The documentation no longer claims the core lacks a session-removal API.
- `SetupViewModel.startOver` awaits clearing before forgetting the library,
  optional metadata key and application identity. A clear failure reaches its
  existing sanitized `RESET_FAILED` state; it neither reports Ready nor removes
  the remaining credentials. The failure message explicitly allows partial state
  and asks the viewer to retry. Existing recovery coverage confirms credentials
  and selection remain when clearing fails.
- `SettingsViewModel.signOut` performs remote sign-out, local clearing and library
  forgetting before publishing `SignedOut`. Any failure keeps completion absent,
  reports the existing fixed retry/start-over sentence and releases busy state.
  Neither caller treats a refused clear as a completed reset. Missing paths are
  tolerated on retry by the storage implementation.

## Evidence

Read the relevant source, `SettingsIdentityFailureTest`, `SettingsViewModelTest`,
the real `StoredCoreProvider` fixture and existing cleanup/recovery tests.

- `/tmp/android-identity-errors-red.log`: 18 selected tests, five intended
  failures (four new failure-boundary cases plus the prior unverified-identity
  expectation).
- `/tmp/android-identity-errors-green.log`: focused Settings gate succeeds.
  XML confirms **4 identity tests + 14 Settings tests passed**, with zero failures,
  errors or skips. The later descriptive test-method rename changes no behavior.
- `/tmp/android-identity-errors-format.log`: supplied formatting gate has no
  diagnostic output.

No Gradle invocation, test rerun, source/test edit, git mutation, scanner action
or process start was performed by this review.

Concerns/Blockers: none in the requested scope.
