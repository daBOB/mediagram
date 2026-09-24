# Android follow-up focused commits

Status: DONE

Committed the exact reviewed and tested 11-file Android checkpoint in three
coherent groups on `desloppify/quality-20260923`. No source edits, version bump,
test rerun or push were performed during this task.

## Commits and exact paths

### `3012012636ce0e68b6d0eb38c4e464e55d829bb0`

`docs(android-core): clarify partial storage reset recovery`

Documentation-only correction describing existing sequential deletion, partial
failure and caller recovery obligations; independently valid before either fix.

- `android/core/data/src/main/kotlin/CoreStorage.kt`

### `f1dbbc102c2693c86426abf270cae9073947f173`

`fix(android-settings): report identity replacement failures truthfully`

Neutral identity-replacement failure text and original-cause retention travel
with the four real-provider failure-boundary cases and corrected existing test.

- `android/feature/setup/src/main/kotlin/SettingsViewModel.kt`
- `android/feature/setup/src/test/kotlin/SettingsIdentityFailureTest.kt`
- `android/feature/setup/src/test/kotlin/SettingsViewModelTest.kt`

### `2c9e0e99e3b443192be640140cb2e1f104d3e55c`

`fix(android-login): reset retained authorization on sign-in entry`

The ViewModel entry reset, MobileApp effect ordering and production-composition
regressions form one integration boundary. Keeping them together avoids a
missing-method import or a newly committed regression suite without its repair.

- `android/feature/setup/src/main/kotlin/login/LoginViewModel.kt`
- `android/feature/setup/src/test/kotlin/login/LoginViewModelTest.kt`
- `android/ui-mobile/src/main/kotlin/ui/MobileApp.kt`
- `android/ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`
- `android/ui-mobile/src/test/kotlin/ui/MobileAppFixture.kt`
- `android/ui-mobile/src/test/kotlin/ui/MobileAppTest.kt`
- `android/ui-mobile/src/test/kotlin/ui/MobileAppTestActivity.kt`

## Validation and safety

- Root's final full Android gate passed: `testDebugUnitTest lintDebug
  :app:compileDebugKotlin :core:data:compileDebugAndroidTestKotlin`;
  `/tmp/android-third-final-gate.log` records **BUILD SUCCESSFUL**, 500 tasks.
- Root's explicit 11-file formatting gate passed;
  `/tmp/android-third-final-format.log` has no diagnostics.
- Mobile setup's restored module gate has **176 passing tests**, and five
  behavioral mutations failed as expected. Identity replacement has five
  failing-before message cases and **18 passing Settings cases** after repair.
  The two independent review reports are PASS:
  [storage/identity](android-storage-identity-review.md) and
  [MobileApp setup](android-mobile-setup-independent-review.md).
- Before staging, recorded each of the 11 source/test files' SHA-256 in
  `/tmp/android-followup-commit-checkpoint.json`. After all commits, every
  worktree and final-HEAD file matched its recorded hash exactly.
- Each staged boundary contained only its explicit path list and passed
  `git diff --cached --check`. Security-candidate review found code identifiers,
  comments and deterministic test-only credentials/tokens; no real secrets,
  dotenv files, private keys or user data were staged.
- Used normal `git commit` with configured hooks intact. The configured hooks
  directory contains only `pre-push`; no hooks were disabled or bypassed.
- A GitHub issue lookup for Android returned no matching issues.
- Root Cargo, web package and Android app versions remain synchronized at
  **0.40.2**. No manifest was included in these commits.

## Final state

Final HEAD: `2c9e0e99e3b443192be640140cb2e1f104d3e55c`.
Index empty; all Android paths clean. Root documentation/configuration/plans and
concurrent web tooling changes remain outside these commits. Finding tracking
and any later publication remain root-owned. Concerns: none.
