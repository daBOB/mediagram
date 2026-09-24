# Android fixes: independent review

Status: DONE

Verdict: **PASS**. The diagnostic-retention correction is verified and the P2
finding is closed. No additional supported ownership, cancellation, cache-state
or presentation regression was found in the reviewed implementation.

Reviewed the ten assigned production files against `67ed2bce`, their modified
regressions, and the new `CoreSupplyTest`, `SettingsWatchOwnershipTest`,
`CacheBudgetBlockTest` and `SettingsProfileRetryTest`. Requirements came from
the fifth assessment at
`android/.desloppify/subagents/runs/20260924_033846/holistic_issues_merged.json`
and the controller's bounded review brief. The implementer's final report was
not yet available; formatting/final gates remained worker-owned.

## Diagnostic correction verified

The initial review found that sanitized login and catalog failures discarded
their original causes. The follow-up review was limited to that correction,
the production caller that excludes cancellation, and its regression evidence.

- `android/feature/setup/src/main/kotlin/login/LoginViewModel.kt:119` rethrows
  cancellation before logging, passes the original throwable to `Log.w`, then
  produces the same controlled step-specific text or existing core mapping.
  Its diagnostic message contains only the operation and step; no submitted
  phone, code, password or sign-in token is appended.
- `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt:79` logs the
  original failed-refresh Result before the local read can fail. Its read and
  regrouping catches log their original exceptions after explicit cancellation
  catches. Presentation still uses controlled fallback text and the known core
  mapping. `LibraryUpdateCoordinator.kt:44` rejects cancellation Results before
  invoking this private callback, so they cannot enter the refresh log path.
- `android/ui-mobile/src/test/kotlin/ui/FailureDiagnosticsTest.kt` instantiates
  the actual ViewModels and update coordinator and inspects `ShadowLog`.
  Identity assertions confirm the original throwable reaches diagnostics;
  UI assertions confirm controlled text. Login tests cover every step and
  check that diagnostic messages exclude submitted arguments. Cancellation
  tests cover all login steps plus refresh Result, initial read and regrouping,
  asserting neither failure diagnostics nor refusal presentation.

This closes the missing requirement without changing UI contracts or adding
auth arguments to diagnostic messages. Original throwable content is retained
as requested; the review does not claim arbitrary exception text is redacted
inside diagnostic causes. Player and cache already retain throwable diagnostics.

## Other requirements and inspected boundaries

- `CoreProvider.kt:129` checks publication under the lifecycle mutex before
  construction or credential writes. The sequential and gated concurrent
  supply tests check one build, preserved credentials and the original owner.
- `SettingsViewModel.kt:138` reconciles watch state after successful replacement
  and also after a refused replacement retires the old core. A failed recovery
  keeps the primary replacement refusal, records the secondary exception as
  suppressed, and exposes a separate profile-read retry. Accepted credentials
  remain stored; retry never calls replacement again. Cancellation propagates
  and leaves reconciliation pending without a false success/refusal message.
- `SettingsViewModel.kt:177` publishes `ApplicationChanged` only after reload;
  a successful retry publishes once. Refresh preserves the pending retry flag,
  duplicate identity submissions are blocked, and sign-out clears pending
  reconciliation. Both Settings surfaces render the retry. The real provider
  and watch repository regression verifies writes reach the replacement after
  a zero-pull sync, and separately checks refused replacement recovery.
- `CacheBudgetViewModel.kt:72` serializes reads/changes, retains the last
  confirmed occupancy on failure, clears the failure after success, and
  propagates cancellation. `CacheBudgetBlock.kt:39` renders feedback/retry even
  with null occupancy. Compose tests exercise initial failure, resize refusal,
  failed confirmation, cancellation and recovery through the real ViewModel.
- `CoreErrors.kt:22` retains the known core mapping. `DefaultPlayerHandle.kt:78`
  and line 101 retain causes in logs while presenting controlled error text for
  playback and construction respectively. Existing reopen/lifetime regressions
  continue to assert actual player behavior, with updated presentation text.

## Evidence and limits

Fresh scope-limited `git diff --check 67ed2bce` exits 0. Inspected logs show:

- `/tmp/android-fifth-initial-red.log`: seven failures across the original
  supply, refresh, login, accepted-owner and construction regressions.
- `/tmp/android-fifth-refused-owner-red.log`: both refused-owner cases fail
  before the additional repair.
- `/tmp/android-fifth-focused-verified.log`: BUILD SUCCESSFUL; reported fresh
  module counts are core data 18, catalog 23, setup 37 and mobile UI 11, all
  passing. This log does not print a fresh player test count.
- `/tmp/android-fifth-diagnostics-red.log`: three diagnostic-retention tests
  fail before the correction; the two cancellation cases already pass.
- `/tmp/android-fifth-diagnostics-green.log`: BUILD SUCCESSFUL; setup 13,
  catalog 23 and the five diagnostic regressions all pass. These completed
  worker-run logs were read directly during the follow-up; no future gate
  result is claimed. The follow-up scope's `git diff --check` also exits 0.

No Gradle invocation or device test was run by this reviewer. Evidence uses
Kotlin orchestration and Robolectric/Compose tests with external/native
boundaries substituted; it does not prove real-device native behavior. The
implementation report, final formatting and final whole-project gates were
pending at review time. Encrypted provisioning design, native code and
unrelated concurrent work were not changed or reviewed.

Only this report was written. Unresolved findings/questions: none.
