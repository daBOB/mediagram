# Android retained profile ownership

Status: DONE — retained profile ownership, serialized account reset and native retirement integration are verified. Root owns the final workspace gate and scanner disposition.

## Confirmed causes and repairs

- `DefaultWatchStateRepository` published suspended reload/write snapshots without checking their originating core or selection. Publication now checks the captured core and revision under a small synchronous lock. Reset invalidates pending publication, including profile creation. Selection metadata and native choice acknowledgement share a coroutine mutex; snapshot IO stays outside it. A separate request counter prevents a choice waiting for its core from overtaking a newer request without letting a queued request's revision leak into reload.
- `ProfileViewModel` depended on a five-second subscriber timeout before reading state again. Its profile-owner subscription now stops and expires immediately, so rapid setup/profile re-entry executes the existing `onStart` reconciliation. Operation ownership also prevents a late choice/reload success or error from replacing a newer UI choice, including A→B→A. `ProfileGate` itself needs no extra entry callback.
- Successful sign-out/start-over now invalidate the repository before reporting navigation/setup completion. Failures and cancellation retain their existing propagation and retry handling.
- Sign-out formerly retained the native core after unlinking its SQLite files. Rust `StateDb` retains an open connection independently of session `State`; clearing the session alone does not close it. `CoreProvider.resetAccount(storage)` now holds the provider lifecycle mutex across unpublishing/closing the old client and clearing account files. It keeps device credentials. A failed close is retained privately and must succeed before storage deletion or a new core can be built. Cleanup runs in `NonCancellable`; a cancelled caller remains cancelled after cleanup.

The independent reviewer also established that releasing a UniFFI handle does not join already-dispatched native calls. A queued native state operation could lazily reopen the deleted database. Root authorized a separate Rust worker to add terminal state retirement under the database mutex. `DefaultCoreClient.close()` now invokes generated `retireLocalState()` before releasing the handle, synchronizes repeated close, and keeps the handle available if retirement fails. Retirement fences local database work; other in-flight native operations may finish separately. Normal identity replacement preserves the files.

## Regression evidence

Tests exercise the real repository, ViewModels and provider. Controlled `CoreClient` implementations provide only raw IO answers and deferred completion; orchestration is production code.

| Evidence | Result |
| --- | --- |
| `/tmp/android-profile-ownership-red.log` | Four expected failures: delayed reload, delayed mutation/list creation, delayed choice snapshot, replaced core |
| `/tmp/android-profile-reset-red.log` | Two successful-reset invalidation failures and one immediate retained-VM re-entry failure; failed-storage-reset control passes |
| `/tmp/android-profile-choice-red.log` | A persisted-but-suspended choice incorrectly reports refusal after a concurrent reload |
| `/tmp/android-profile-review-red.log` | Four expected failures: queued reload/choice acknowledgement plus real ViewModel/repository late success, late error and same-profile ABA error |
| `/tmp/android-profile-core-retention-red.log` | Sign-out does not close the old core before subsequent profile reload |
| `/tmp/android-profile-reset-lifecycle-green.log` | 113 pass: 61 core/data, 34 setup, 18 profile; zero failures/skips |
| `/tmp/android-profile-format.log` and `/tmp/android-profile-adapter-format.log` | Scoped authored Kotlin formatting passes |
| `/tmp/android-profile-adapter-red.log` → `/tmp/android-profile-adapter-green.log` | Two intended runtime failures → two pass: retirement before release/repeated close; retirement failure preserves retry ownership |
| `/tmp/android-profile-consumers-green.log` | 364 pass / zero failures or skips; app Kotlin/Hilt/KSP compilation passes |
| `/tmp/rust-local-state-bindings.log` | Native worker's normal two-ABI build and generated Kotlin binding regeneration complete |

Additional green cases cover A→B→A snapshots, a replacement core with the same profile ID, reset during a pending read/create, serialized choices, a request waiting for `awaitCore`, stale reload errors, close-before-clear, blocked concurrent reopening, retained credentials, failed-close ownership/retry, and cancellation during cleanup.

The initial setup regression fixture attempted to inspect model types not exposed on that module's classpath. Those compile errors were corrected by asserting the selected profile through the existing accessible contract; the final red logs above contain runtime assertion failures. An early empty test filter was likewise replaced with the actual added regression. Neither is claimed as behavioral failure evidence. The first adapter fixture also exposed that core/data does not depend on MockK; it was replaced with the generated bindings' explicit `Core(NoHandle)` subclass seam. Its final red evidence is two runtime assertions, with no new test dependency.

## Changed surfaces

Production owners:

- [WatchStateRepository](../../../android/core/data/src/main/kotlin/WatchStateRepository.kt): captured publication ownership and explicit reset invalidation.
- [CoreProvider](../../../android/core/data/src/main/kotlin/CoreProvider.kt): serialized account-storage reset and failed-close retry ownership.
- [ProfileViewModel](../../../android/feature/catalog/src/main/kotlin/profile/ProfileViewModel.kt): prompt re-entry reconciliation and current-operation UI publication.
- [SettingsViewModel](../../../android/feature/setup/src/main/kotlin/SettingsViewModel.kt) and [SetupViewModel](../../../android/feature/setup/src/main/kotlin/SetupViewModel.kt): account reset boundary and successful invalidation before navigation.
- [DefaultCoreClient](../../../android/core/data/src/main/kotlin/DefaultCoreClient.kt) and [CoreClient](../../../android/core/data/src/main/kotlin/CoreClient.kt) close KDoc: state retirement before native release; other CoreClient documentation belongs to root.

New tests: [WatchStateOwnershipTest](../../../android/core/data/src/test/kotlin/WatchStateOwnershipTest.kt), [CoreAccountResetTest](../../../android/core/data/src/test/kotlin/CoreAccountResetTest.kt), [DefaultCoreClientCloseTest](../../../android/core/data/src/test/kotlin/DefaultCoreClientCloseTest.kt), [ProfileOwnershipTest](../../../android/feature/catalog/src/test/kotlin/profile/ProfileOwnershipTest.kt), [ProfileResetTest](../../../android/feature/setup/src/test/kotlin/ProfileResetTest.kt). Existing `ProfileViewModelTest` adds rapid retained-instance re-entry. Constructor/interface adapters are confined to `SetupFixture`, `MobileAppFixture`, data/catalog/player watch-state fakes, and data/catalog/setup-login/system core-provider fakes.

## Verification and resource boundary

Gradle commands use JDK 21.0.8, the configured Android SDK and explicit `-Duser.country=US -Duser.language=en`, reusing quality-run daemon 3120845. A temporary init script prints suite totals and full assertion failures without reading generated test-report directories. No live Telegram, user database, scanner state, manifests, commits or pushes are involved. All worker-owned Gradle/ktlint commands have exited; shared quality-run daemons remain available to root.

Final consumer suites: core/data **111**, setup **58**, catalog **108**, player **57**, system **20**, MobileApp/LibraryFlow UI **10**. The final command also ran `:app:compileDebugKotlin`, exercising generated bindings and Hilt/KSP consumers. Existing Compose stability-file, opt-in and Gradle deprecation warnings remain visible; no failure was hidden. `git diff --check` passes for the changed scope.

The adapter test uses the generated `Core(NoHandle)` test constructor and its actual destruction flag, while overriding only retirement/release raw IO. It does not load a native library. The [native retirement report](rust-local-state-retirement.md) records real temporary SQLite and queued production `create_profile` work, with 74 state and 11 API tests passing before normal binding regeneration. Its two bounded mutations reproduce the missing-fence failures. Root owns the combined workspace checks and native review.

The [final independent review](android-profile-ownership-independent-review.md) is PASS for the repository/VM interleavings, provider reset ownership, native retirement and adapter integration; the reviewer verified the gate logs and found no unresolved scoped defect. No known scoped behavior defect remains. No report claims a live-device run, live Telegram use, or a global drain of all native operations.

## Root broad gate

After the scoped handoff, root verified whole-project Android `testDebugUnitTest lintDebug :app:compileDebugKotlin :core:data:compileDebugAndroidTestKotlin`: **PASS**, 500 Gradle tasks in 23 seconds, `/tmp/android-fourth-full-gate.log`. Root also verified **1,025 Rust tests passed, four intentional ignores**, formatting, all-feature strict Clippy and rustdoc in `/tmp/rust-retirement-final-{tests,format,clippy,doc}.log`. Root independently reviewed the native retirement source as PASS. The worker read the broad log completions and summed the Rust test-result lines; source stayed unchanged.

## Source checkpoint

Exact authored scope at the final 364-test gate, SHA-256 (also `/tmp/android-profile-source-checkpoint.sha256`):

```text
c4ed17f2054a78066a3fd7909e5de2558a4b656c11f387d351bdf91304f84c35  android/core/data/src/main/kotlin/CoreProvider.kt
4e7ea3c2d38b7bd1a4e8070d411a7530e4933668167a1463c1f43a9083b76ae8  android/core/data/src/main/kotlin/WatchStateRepository.kt
edffd93dfb483b0c370b2fb2bdb7e301d7f2208d3629dfcd8982e6927fc0a723  android/core/data/src/main/kotlin/DefaultCoreClient.kt
773d171eaccf382fc634a65debe4338618152225742868ea7994c725bbe8d73e  android/core/data/src/main/kotlin/CoreClient.kt
ade74231ba9d52f19978efb8bcf66dd532155953191c63b41957fcf18697fa80  android/feature/catalog/src/main/kotlin/profile/ProfileViewModel.kt
1b9e743df53d20ae1b006dc6dc5016cbcfaca65483843dc44e273fa9ca96c9b8  android/feature/setup/src/main/kotlin/SettingsViewModel.kt
379f76fb6430724568e7d76c8c878e0f5b5eb1fb89ad1cf77f1914f3e0cc7158  android/feature/setup/src/main/kotlin/SetupViewModel.kt
41b5f6fa85bac807b970190eaa64585cfbf7c8e5f659c8e2d0b92eb908445f15  android/core/data/src/test/kotlin/WatchStateOwnershipTest.kt
b11c3b16e2b9bed4a82a8b8e70db4f5cd3cd2e990bbe43840caa059df5d00e5f  android/core/data/src/test/kotlin/CoreAccountResetTest.kt
0eb19ee9a4e3366ee352412c7ed745a0dc672c9db3fc572999b9bf2fcc0eae76  android/core/data/src/test/kotlin/DefaultCoreClientCloseTest.kt
565f02739ece713c23ed8cbd176716fe3514dac4ace3a5ddf9c848235ebc94ec  android/feature/catalog/src/test/kotlin/profile/ProfileOwnershipTest.kt
94df3f6c8e0b21f6680bae98dce892e88ded93c01aa761bb0602ce7c7cbc9b0c  android/feature/setup/src/test/kotlin/ProfileResetTest.kt
37caa97545d64d8b22bafcc31ddb21f4efdb1f85340e1b41c7b646f8c720b514  android/core/data/src/test/kotlin/FakeCore.kt
6645444ed4c34210aacec73c2413ffb38dc7131ceef312b4749edbc19796a804  android/core/data/src/test/kotlin/WatchSyncTest.kt
099e849220282a8631f1dca8f4e0918951eb8877f6cd06e1191f93b876b44b35  android/feature/catalog/src/test/kotlin/CatalogCoreFixture.kt
861d8979ad3356aa0c4305332472e36dd60b5b06414bc2c4b287b4484431f6e8  android/feature/catalog/src/test/kotlin/CatalogViewModelTest.kt
0a195d42037652c0f0ef587a0980ef6d3badcc27d66a0a49ec3485e1755141e9  android/feature/catalog/src/test/kotlin/profile/ProfileViewModelTest.kt
82d15da1da1844dd6ed7dd7035a2159b083dcbe90b9b8dab0b92dd59a2454927  android/feature/player/src/test/kotlin/FakeWatchStateRepository.kt
a92c9e87de60f6a051e4ae2ad8925e8322e548c86fbe2c8ed04cd127fe2866e5  android/feature/setup/src/test/kotlin/SetupFixture.kt
539e13726d44ded5de7fc199e71a516ba4830416908fcb39b3754d1f5cc31459  android/feature/setup/src/test/kotlin/login/FakeCore.kt
6ba1293660edd509d4c621bf42d41dffda0000d4e0861771316195680be6fea6  android/feature/system/src/test/kotlin/FakeCore.kt
50db99fd268295aab832fc149d6d579b06e628e3e5bf4f018f7f93eb03fefddb  android/ui-mobile/src/test/kotlin/ui/MobileAppFixture.kt
```
