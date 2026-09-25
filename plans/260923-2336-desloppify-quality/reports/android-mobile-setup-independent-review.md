# Mobile setup integration independent review

Status: DONE — PASS

## Conclusion

No actionable correctness or compatibility finding in the reviewed delta. The
repair clears a completed Activity-retained login before observing another
completion, and the integration tests exercise the production setup routing
through the same-core sign-out and foreground authorization-loss boundaries.

## Source review

- `MobileApp.kt:126–137`: one `LaunchedEffect(loginViewModel)` runs the
  synchronous entry reset before `state.first { Authorized }`. A retained
  completion cannot trigger the callback first. The stable ViewModel key avoids
  restarting this reset on ordinary recomposition; `rememberUpdatedState`
  supplies the current callback. The wait is owned by the sign-in composition
  and is cancelled when that composition leaves. There is no unbounded
  collection or repeated callback after the first new completion.
- `LoginViewModel.kt:74–78`: entry clears both the completed state and its token.
  `submitCode` still refuses to call the core without a token. Pending code,
  password, rejection and cancelled-retry states are unchanged by entry, and
  existing core-replacement handling remains intact. Existing public method
  signatures, state variants and native interfaces are unchanged.
- `MobileApp.kt:54–57,88–100`: foreground recheck, authentication completion,
  library selection and Settings sign-out route through the actual
  `SetupViewModel`. Its outstanding-step check remains the authorization
  authority. `Libraries.install` persists the choice only after installation.

## Test quality and evidence

The new Activity renders the shipped `MobileApp`. Its real `ViewModelStore`
holds real Setup, Login and Settings ViewModels, `StoredCoreProvider` and
`Libraries`; the Hilt interception changes only generated factory lookup.
Native IO, in-memory settings and unrelated catalog/media facts are controlled
test boundaries. The shared `LibraryFlowFixture` keeps its prior default
behavior and optionally accepts the real Settings ViewModel.

The four new Compose tests cover actual application/phone/code and two-factor
callbacks, a deferred library installation, Activity stop/resume with the same
core, and the real Settings sign-out confirmation/completion path. The resume
case checks exactly one additional authorization read before a fresh login,
and both recovery cases redeem a newly issued token. Login unit coverage checks
old-token rejection and pending/rejected/cancelled credential retry preservation.

Read existing evidence; no tests were rerun for this review:

- `/tmp/android-mobile-setup-before.log`: four Compose tests ran; the two
  sign-out/resume regressions failed at the missing phone field before repair.
- `/tmp/android-mobile-setup-green.log`: focused build succeeded. Final XML
  confirms 4 MobileApp, 6 LibraryFlow and 11 Login tests passing.
- `/tmp/android-mobile-setup-mutation-{completion,library,resume,entry,token}.log`
  and their saved XML: all five mutations failed behavioral assertions. Removing
  handoff hid the picker, removing selection left no installation, removing
  resume/entry recovery hid the phone field, and retaining the token allowed an
  old code to return to Authorized.
- `/tmp/android-mobile-setup-verified.log`: final restored broad build succeeded.
  Current test XML totals are **54 setup + 122 mobile = 176 tests**, with zero
  failures, errors or skips. The worker report records byte restoration/hash
  verification, explicit-file formatting and scoped whitespace checks.

These are Robolectric Compose and JVM results, not device, native-network or
live Telegram validation. Effect disposal behavior was assessed from ownership
in source; no additional runtime cancellation scenario is claimed.

## Boundaries

Read-only production review; this report is the sole written artifact. No
Gradle, scanner, git mutation, source/test modification or background process
was started. Concerns: none within the requested scope.
