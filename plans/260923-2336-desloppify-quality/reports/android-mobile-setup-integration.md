# Mobile setup handoff integration

Status: DONE

## Verified cause and repair

The new real-composition tests confirmed a production defect after both
foreground authorization loss and explicit Settings sign-out. Setup correctly
returned to `NeedsSignIn`, but the same Activity-owned `LoginViewModel` still
held `Authorized`. `LoginScreen` consequently rendered no credential field and
the previous completion requested another setup check.

`LoginViewModel.enterSignIn()` now consumes Setup's already established
unauthorized state: it clears only a completed login and its old token. A
pending or rejected code/password remains available for retry. There is no
extra asynchronous authorization query or reset racing those attempts.

MobileApp's sign-in-entry effect performs this reset before awaiting the next
`Authorized` state. A normal recomposition does not restart that effect, and
leaving the sign-in composition cancels its wait. The current completion callback
is read through `rememberUpdatedState`. Existing public signatures and native
session behavior are unchanged.

Covered findings:

- `review::.::holistic::test_strategy::mobile_setup_handoff_unverified`
- `review::.::holistic::mid_level_elegance::retained_login_misses_same_core_signout`

## Production composition coverage

Added `MobileAppTest`, `MobileAppTestActivity` and `MobileAppFixture`. Tests render
the shipped `MobileApp`, using real Setup, Login and Settings view models, real
`StoredCoreProvider` and `Libraries`, in-memory settings, and the existing real
catalog/profile/player fixture. The only Hilt interception replaces the generated
Activity factory lookup; view models are retained under real provider keys in a
real `ViewModelStore`. Native IO and unrelated catalog/media facts are controlled.
No test duplicates the setup state machine.

The four Compose tests cover:

1. Actual application identity, phone, code and library row callbacks. The
   installer is held at a deferred IO boundary: no chosen library or Ready UI
   appears until installation finishes.
2. The two-factor password callback and completion handoff into library selection.
3. Real Activity stop/resume after authorization changes on the same core. The
   phone form returns, exactly one authorization recheck occurs, and another
   phone/code submission succeeds using a newly issued token.
4. The real Settings sign-out confirmation and retained completion handler. The
   phone form returns without replacing the core, storage/library selection are
   cleared, and a new login returns to the library picker.

Two added login unit tests verify completed-token invalidation and preservation
of a pending code. Existing wrong-code/password and cancellation tests now also
exercise re-entry before retry. The shared LibraryFlow fixture gained only an
optional real SettingsViewModel parameter; its default behavior is unchanged.

## Red, green and mutation evidence

- Corrected pre-fix composition run: **2 pass, 2 fail**, both failures at the
  missing phone field after sign-out/resume. Log:
  `/tmp/android-mobile-setup-before.log`. An initial test attempt needed its
  Settings confirmation selector scoped to the dialog; that fixture issue was
  fixed before the final red run.
- First focused green: **21 tests** — 4 MobileApp, 6 LibraryFlow and 11 Login —
  passed. Log: `/tmp/android-mobile-setup-green.log`.
- Five bounded source mutations were executed sequentially under the exclusive
  Gradle slot. Every mutation failed an assertion, not compilation:

| Mutation | Observed failure |
| --- | --- |
| Remove login completion handoff | Library picker never appears |
| Remove library row callback | No installation was requested |
| Remove foreground recheck | Phone field never returns |
| Remove sign-in entry reset | Both sign-out and resume leave no phone field |
| Keep the completed token | Submitting an old code wrongly returns Authorized |

Logs are `/tmp/android-mobile-setup-mutation-{completion,library,resume,entry,token}.log`;
matching XML result copies end in `-results`. Each mutation restored the exact
original source bytes in `finally` and verified its SHA-256 before continuing.
No mutation remains in the working tree.

Final restored command, from `android/`:

```sh
./gradlew :feature:setup:testDebugUnitTest :ui-mobile:testDebugUnitTest
```

**176 tests passed: setup 54, mobile 122; zero failures, errors or skips.**
Build succeeded in 14 seconds; `/tmp/android-mobile-setup-verified.log`.
Explicit-file ktlint and scoped `git diff --check` passed.

## Boundaries and handoff

Production changes are limited to MobileApp and LoginViewModel. No native calls,
live Telegram, user storage, manifests, scanner state or commits were involved.
Activities and fixture scopes are closed after each test. All owned Gradle
wrapper processes exited; the pre-existing shared daemon was reused with the
existing JDK and locale options. The Gradle slot was returned to root.

Root owns independent review and queue resolution. Existing Compose stability
configuration warnings remain unrelated to this change; both required modules
compiled and their complete unit suites passed.
