# Rust and Android checkpoint commits

Status: DONE — eight focused commits created; publication remains on the controller's explicit hold.

Branch: `desloppify/quality-20260923`. Starting HEAD `e57625a182e188b5c79e4d5810b7cd4b25a2a275`; final HEAD `7b6ee4698259be8b17a7bdd62da4c7012d9aee60`. No source edits or version bumps were made during this git task.

## Commit groups

| Commit | Subject | Files |
| --- | --- | --- |
| `b952a0b7` | fix(core-session): keep key removal under the session guard | 2 |
| `a82a6374` | fix(core-error): retain nested diagnostic causes | 2 |
| `049cb1f6` | fix(media): bound MP4 boxes before advancing offsets | 2 |
| `a7fd9765` | test(core-install): synchronize competing staging acquisition | 1 |
| `28bbda5a` | docs(core-state): clarify snapshot timestamp coverage | 1 |
| `33bd1193` | test(android-playback): verify repeated reads use cached bytes | 1 |
| `a8373141` | fix(android-system): retain diagnostics and allow snapshot retries | 5 |
| `7b6ee469` | fix(account): retire local state before resetting profile ownership | 27 |

The final 27-file commit keeps the native terminal state retirement, additive API, regenerated Kotlin binding, Android close/reset/provider contract, profile publication ownership and every affected fixture adapter together. Its full-file `CoreClient.kt` change includes the approved lifecycle and fallback documentation; no partial staging was needed. The seven earlier groups are independently scoped and do not add a consumer before its required contract. Intermediate commits were not separately rebuilt.

## Exact manifest comparison

- Authorized scope: exactly the **41 keys** of `.desloppify/rust-android-sixth-tested-sha256.json`.
- Manifest SHA-256: `6500f95d6c429314c0cf310b245b065b98e2eeff39f926c003a63c75ed65c5b6`; unchanged before and after committing.
- All 41 working files matched before staging. Every staged blob and its resulting commit blob matched its expected SHA-256. All 41 final HEAD blobs and working files still match.
- The aggregate diff from starting HEAD to final HEAD contains exactly those 41 paths, with no additional paths. Index is empty.
- Strong credential-pattern checks found no real credential. Broader matches were documentation, a fixture test name, existing auth-boundary calls and explicit test data. Credential-like long literals were inspected without printing values; the only candidate was a Kotlin lint suppression name. Each staged diff passed whitespace validation.
- Web changes, root docs, `.gitignore`, plans, skills files, manifests and scanner state were excluded from staging and left to their owners. The report itself is an untracked handoff artifact, outside these commits.

| Commit | Authorized path | Expected / committed / working SHA-256 |
| --- | --- | --- |
| `b952a0b7` | `crates/mediagram-core/src/api/account/profile.rs` | `8600eeae04e7a78416cf04d7f7bf5aa5cb18e2717234acc7eae8712fbe782ed1` |
| `b952a0b7` | `crates/mediagram-core/src/api/account/profile_tests.rs` | `346825ddc94f8accc7bf2bc320ac1f693339946034698ada84c4481c18464478` |
| `a82a6374` | `crates/mediagram-core/src/error.rs` | `3d6a921613e52dde27cdc149e8612f2ef9e886f04c274d369ad89413fab414a1` |
| `a82a6374` | `crates/mediagram-core/src/error_tests.rs` | `b841a9335404d642cb20dbf657c7a093ea1522d067a3084760e5e8cf7d814475` |
| `049cb1f6` | `crates/mediagram/src/media/mp4_atoms.rs` | `ca71429eace471a66be2c329a867f874c220d7a12d66daa705fbac5864a8307e` |
| `049cb1f6` | `crates/mediagram/tests/media_mp4_atoms.rs` | `a71cfce5e3834fabb9fc4f684e7428f9d2436c9212608ec8169b8cb4409ac163` |
| `a7fd9765` | `crates/mediagram-core/src/versions/install.rs` | `56944b69def85f54aa5b7d4d488d5e0e22713d5a749dee54ea51d405904d152d` |
| `28bbda5a` | `crates/mediagram-core/src/state/exchange.rs` | `bbdb9d9a83b66a307684a30a2ff3a1f1cc9ce74cf3601a252dfe657eae6f9a45` |
| `33bd1193` | `android/core/playback/src/test/kotlin/PlayerFactoryTest.kt` | `7d24835801c859c636aa84ee5c572bde800ca0bbf66ee3c6bf29be5a22cbf555` |
| `a8373141` | `android/feature/system/src/main/kotlin/SystemViewModel.kt` | `43b8bf942413499de68c4dfebcee5575673e233c03ac15daf727e5ef6673faff` |
| `a8373141` | `android/ui-mobile/src/main/kotlin/ui/system/SystemScreen.kt` | `6b6715f1d78c09ed7a1e7b42446b8899e5c7a794124657b968c96acfd52da6d4` |
| `a8373141` | `android/ui-mobile/src/test/kotlin/ui/system/SystemViewModelTest.kt` | `cb650ad94389b734dade4eb68b0f5428154d2ef763526a22e5a9eed83f9e1240` |
| `a8373141` | `android/ui-mobile/src/test/kotlin/ui/system/SystemScreenTest.kt` | `385b80ca3f25c6fcf36100f45133e555e0aa9df774eb48f2abb07ca3edc456fb` |
| `a8373141` | `android/ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt` | `c2958fbf6d40dcbd9baa9a2b0a178566d7900d7be46ea05bdae29f9e4966f127` |
| `7b6ee469` | `android/core/data/src/main/kotlin/CoreClient.kt` | `773d171eaccf382fc634a65debe4338618152225742868ea7994c725bbe8d73e` |
| `7b6ee469` | `android/core/data/src/main/kotlin/CoreProvider.kt` | `c4ed17f2054a78066a3fd7909e5de2558a4b656c11f387d351bdf91304f84c35` |
| `7b6ee469` | `android/core/data/src/main/kotlin/DefaultCoreClient.kt` | `edffd93dfb483b0c370b2fb2bdb7e301d7f2208d3629dfcd8982e6927fc0a723` |
| `7b6ee469` | `android/core/data/src/main/kotlin/WatchStateRepository.kt` | `4e7ea3c2d38b7bd1a4e8070d411a7530e4933668167a1463c1f43a9083b76ae8` |
| `7b6ee469` | `android/core/data/src/test/kotlin/CoreAccountResetTest.kt` | `b11c3b16e2b9bed4a82a8b8e70db4f5cd3cd2e990bbe43840caa059df5d00e5f` |
| `7b6ee469` | `android/core/data/src/test/kotlin/DefaultCoreClientCloseTest.kt` | `0eb19ee9a4e3366ee352412c7ed745a0dc672c9db3fc572999b9bf2fcc0eae76` |
| `7b6ee469` | `android/core/data/src/test/kotlin/FakeCore.kt` | `37caa97545d64d8b22bafcc31ddb21f4efdb1f85340e1b41c7b646f8c720b514` |
| `7b6ee469` | `android/core/data/src/test/kotlin/WatchStateOwnershipTest.kt` | `41b5f6fa85bac807b970190eaa64585cfbf7c8e5f659c8e2d0b92eb908445f15` |
| `7b6ee469` | `android/core/data/src/test/kotlin/WatchSyncTest.kt` | `6645444ed4c34210aacec73c2413ffb38dc7131ceef312b4749edbc19796a804` |
| `7b6ee469` | `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` | `993f559f1212eecc92c7efc33f560de283ae117f4ad6f308d776148b622a5d8c` |
| `7b6ee469` | `android/feature/catalog/src/main/kotlin/profile/ProfileViewModel.kt` | `ade74231ba9d52f19978efb8bcf66dd532155953191c63b41957fcf18697fa80` |
| `7b6ee469` | `android/feature/catalog/src/test/kotlin/CatalogCoreFixture.kt` | `099e849220282a8631f1dca8f4e0918951eb8877f6cd06e1191f93b876b44b35` |
| `7b6ee469` | `android/feature/catalog/src/test/kotlin/CatalogViewModelTest.kt` | `861d8979ad3356aa0c4305332472e36dd60b5b06414bc2c4b287b4484431f6e8` |
| `7b6ee469` | `android/feature/catalog/src/test/kotlin/profile/ProfileOwnershipTest.kt` | `565f02739ece713c23ed8cbd176716fe3514dac4ace3a5ddf9c848235ebc94ec` |
| `7b6ee469` | `android/feature/catalog/src/test/kotlin/profile/ProfileViewModelTest.kt` | `0a195d42037652c0f0ef587a0980ef6d3badcc27d66a0a49ec3485e1755141e9` |
| `7b6ee469` | `android/feature/player/src/test/kotlin/FakeWatchStateRepository.kt` | `82d15da1da1844dd6ed7dd7035a2159b083dcbe90b9b8dab0b92dd59a2454927` |
| `7b6ee469` | `android/feature/setup/src/main/kotlin/SettingsViewModel.kt` | `1b9e743df53d20ae1b006dc6dc5016cbcfaca65483843dc44e273fa9ca96c9b8` |
| `7b6ee469` | `android/feature/setup/src/main/kotlin/SetupViewModel.kt` | `379f76fb6430724568e7d76c8c878e0f5b5eb1fb89ad1cf77f1914f3e0cc7158` |
| `7b6ee469` | `android/feature/setup/src/test/kotlin/ProfileResetTest.kt` | `94df3f6c8e0b21f6680bae98dce892e88ded93c01aa761bb0602ce7c7cbc9b0c` |
| `7b6ee469` | `android/feature/setup/src/test/kotlin/SetupFixture.kt` | `a92c9e87de60f6a051e4ae2ad8925e8322e548c86fbe2c8ed04cd127fe2866e5` |
| `7b6ee469` | `android/feature/setup/src/test/kotlin/login/FakeCore.kt` | `539e13726d44ded5de7fc199e71a516ba4830416908fcb39b3754d1f5cc31459` |
| `7b6ee469` | `android/feature/system/src/test/kotlin/FakeCore.kt` | `6ba1293660edd509d4c621bf42d41dffda0000d4e0861771316195680be6fea6` |
| `7b6ee469` | `android/ui-mobile/src/test/kotlin/ui/MobileAppFixture.kt` | `50db99fd268295aab832fc149d6d579b06e628e3e5bf4f018f7f93eb03fefddb` |
| `7b6ee469` | `crates/mediagram-core/src/api/state.rs` | `ef5baf1aeef81e5d4e05eabeb6450bb737e7a1185d98e259441022fba09fee53` |
| `7b6ee469` | `crates/mediagram-core/src/state/mod.rs` | `12149c11e5c0e6213436e176b68ff6a7e7b56eca9dfb5f2b79ae17ff7cc7ce0d` |
| `7b6ee469` | `crates/mediagram-core/src/state/retirement_tests.rs` | `81a775734215014827f0a4a1e5b06ddb806ad1a474e329b9179ac74dd0fb38e9` |
| `7b6ee469` | `crates/mediagram-core/tests/state_retirement.rs` | `aadbc0d5aab8a2259e9b2b8d6b4b3767d38f5dd1015385486533f9192bf5085c` |

## Validation reused

- `/tmp/rust-retirement-final-tests.log`: summed result lines confirm **1,025 passed, zero failed, four intentional ignores**.
- `/tmp/rust-retirement-final-format.log`, `-clippy.log` and `-doc.log`: supplied formatting, all-target/all-feature strict Clippy and rustdoc gate passed; completions inspected.
- `/tmp/android-fourth-full-gate.log`: **BUILD SUCCESSFUL**, 500 tasks; whole Android unit tests, debug lint, app Kotlin compilation and data instrumentation-test compilation.
- `/tmp/android-profile-consumers-green.log`: **364 passed**, no failures/skips; app Kotlin/Hilt/KSP compilation passed.
- Read the supplied sign-out/error, MP4/staging/snapshot, native retirement, Android ownership, independent ownership review and System recovery reports. Root and independent reviews found no unresolved scoped defect.
- No test/build/server process was started and no shared daemon was stopped. No new failure or source concern required repeating the supplied gates.

## Publication and handoff

No push was attempted, as explicitly instructed. The controller is waiting for the stable whole-web gate before authorizing publication. Existing failed pre-push history does not change that hold; this task did not invoke or bypass hooks, force-push, merge, create a PR, scan, resolve findings or update scanner commit tracking.

Concerns/Blockers: none for this commit checkpoint. Publication remains deliberately pending controller authorization after the whole-web gate. Unresolved questions: none.
