# Android provisioning, watch ownership and failure presentation

Status: DONE. All four reviewed causes repaired; independent diagnostic feedback incorporated. No scanner, Git, Rust, generated binding, dependency, or provisioning-storage changes.

## Repairs and evidence

- **Initial installation:** [StoredCoreProvider.supply](../../../android/core/data/src/main/kotlin/CoreProvider.kt) checks for an already published core inside its lifecycle mutex, before construction or credential persistence. [CoreSupplyTest](../../../android/core/data/src/test/kotlin/CoreSupplyTest.kt) exercises repeated and overlapping real provider calls: the original client and credentials survive, and only one candidate is built. Existing candidate failure/cleanup/retry tests remain green.
- **Replacement ownership:** [SettingsViewModel](../../../android/feature/setup/src/main/kotlin/SettingsViewModel.kt) reloads the real watch repository after accepted replacement. A refused replacement also retired the previous client, so it restores ownership using the credentials still stored, retaining the original refusal and emitting no success. A failed or cancelled reconciliation retains an explicit retry which rereads profiles without replacing credentials again. Accepted changes announce completion only after this read succeeds. Admission closes while the action is busy or reconciliation remains pending. [SettingsScreen](../../../android/ui-mobile/src/main/kotlin/ui/settings/SettingsScreen.kt) exposes the retry both in the form and the Settings rows.
- **Cache outcomes:** [CacheBudgetViewModel](../../../android/feature/system/src/main/kotlin/CacheBudgetViewModel.kt) retains the last confirmed occupancy and publishes controlled read/resize failures. [CacheBudgetBlock](../../../android/ui-mobile/src/main/kotlin/ui/settings/CacheBudgetBlock.kt) remains visible even when the first read fails. Retrying rereads actual storage, accounting for a budget persisted before a later operation failed. Cancellation does not create an error; a later successful read or choice clears the notice.
- **Controlled presentation and diagnostics:** unknown refresh, local catalog, login, player construction and playback failures use operation-specific copy. The existing typed `CoreException` sentence mapping remains intact. Catalog/login terminal handlers log the original throwable with an operation or step, without submitted authentication arguments; player/cache retain their diagnostic throwable too. Cancellation bypasses these diagnostics. The refresh repository still returns its original failure cause; only displayed text is sanitized.

## Regression coverage

[SettingsWatchOwnershipTest](../../../android/feature/setup/src/test/kotlin/SettingsWatchOwnershipTest.kt) uses the real provider, repository and zero-pull sync with distinct controlled native clients. It proves writes reach the replacement without a profile-screen restart, refused replacement restores writes through prior credentials, retry never resubmits accepted credentials, recovery failures retain the original refusal, and cancellation permits recovery without false success/refusal.

[SettingsProfileRetryTest](../../../android/ui-mobile/src/test/kotlin/ui/settings/SettingsProfileRetryTest.kt) submits the actual credential form, observes retained recovery instead of another credential request, and presses the real retry button. [CacheBudgetBlockTest](../../../android/ui-mobile/src/test/kotlin/ui/settings/CacheBudgetBlockTest.kt) drives the actual ViewModel and Compose block against the raw cache IO boundary, covering first failure, repeated failure, resize failure, post-write confirmation failure, cancellation, and later recovery.

[FailureDiagnosticsTest](../../../android/ui-mobile/src/test/kotlin/ui/FailureDiagnosticsTest.kt) executes real login/catalog ViewModels and the catalog coordinator; `ShadowLog` asserts original throwable identity, controlled UI state and cancellation exclusion. Login tests cover all three steps, unknown failures with and without messages, typed core refusals, and reuse of the existing retry flow. Existing player tests retain late-subscriber delivery and reopening behavior while asserting controlled construction/playback text. The previous refresh-log test labelled “core sentence” threw an ordinary `IllegalStateException`; its fixture now raises a real typed `CoreException.Network` to test that stated contract.

## Validation

All logs are local task artifacts under `/tmp/`.

| Gate | Result |
| --- | --- |
| `android-fifth-initial-red.log` | 7 expected failures: repeated/concurrent supply, stale replacement ownership, refresh/login/player raw text |
| `android-fifth-cache-red.log` | Both new cache UI cases failed; the first block disappeared and resize failure had no notice |
| `android-fifth-playback-red.log` | Real playback callback exposed raw exception text; retry test failed as intended |
| `android-fifth-refused-owner-red.log` | Both refused-replacement ownership/recovery regressions failed |
| `android-fifth-focused-verified.log` | 89 focused tests passed; 16 player checks retained their preceding green result |
| `android-fifth-diagnostics-red.log` | 3 missing-diagnostic tests failed; 2 cancellation tests passed |
| `android-fifth-diagnostics-green.log` | 41 tests passed: 5 diagnostic matrices, 13 login, 23 catalog |
| `android-fifth-format.log` | Scoped ktlint passed all 23 owned Kotlin source/test files |
| `android-fifth-full-gate.log` | Whole Android test/lint/app compile/instrumentation compile command passed; 534 tests executed, 504 tasks, 30 seconds |
| `android-fifth-final-gate.log` | Same whole command passed after diagnostic corrections; 314 tests re-executed, unaffected tasks up-to-date, 504 tasks, 25 seconds |

Across the eight test tasks that executed during these full gates, the final counts are: core data 114, core playback 34, catalog 108, player 57, setup 66, system 20, app 2 and mobile UI 138 (539 total). Other unaffected test tasks were up-to-date; this is not presented as a fresh total of every Android test.

Full command: `./gradlew -Duser.country=US -Duser.language=en --no-configuration-cache -I /tmp/android-profile-test-counts.init.gradle testDebugUnitTest lintDebug :app:compileDebugKotlin :core:data:compileDebugAndroidTestKotlin --continue`, with JDK `/home/andre/.local/opt/jdk-21.0.8` and SDK `/home/andre/android-sdk`. `git diff --check -- android` passed.

## Boundaries and handoff

Native, network and filesystem failures are controlled at existing IO boundaries; no live Telegram, user credentials or user databases were used. The successful native retirement/binding work from the prior checkpoint is unchanged. Existing Compose opt-in/stability warnings and Gradle deprecation warnings remain visible in the successful logs.

The exact 23-file source list is `/tmp/android-fifth-owned-files.txt`; hashes are recorded in `/tmp/android-fifth-source-checkpoint.sha256`. All Gradle wrappers started for this batch exited. The shared JDK 21 US/en Gradle daemon remains owned by the parent quality run for reuse, with no new server, watcher, or detached process.
