# Android profile and login ownership

Status: implemented and verified; ready for controller review.

## Changes

- ProfileViewModel catches initial reads and profile mutation failures/refusals, preserves known rows and any valid Stay action, and carries a sanitized optional Picking.error. ProfilePickerScreen displays the error with a retry action wired through ProfileGate. Cancellation is rethrown. If a profile choice commits but its snapshot read fails, Stay cannot falsely acknowledge the unread choice.
- WatchStateRepository now names its profile operations chooseProfile/createProfile. Implementations, callers, and authored test doubles were renamed mechanically; wire/native APIs and repository behavior remain unchanged. Prior controller documentation edits were preserved.
- LoginViewModel, LoginUiState, and focused login tests moved from feature/catalog into feature/setup under package setup.login. Phone/code/password cancellation is rethrown before failure conversion, preserving the current step/token. Other sign-in behavior is unchanged.
- Mobile login imports follow the move. Catalog coroutine tests use their own local core fixture instead of depending on the moved authentication fixtures. No module dependencies were added, and no feature imports another.

## Evidence

- Red baseline: `/tmp/android-profile-login-before.log`, 20 selected tests with seven expected failures (four profile storage/refusal cases and three login cancellation cases).
- Focused green: `/tmp/android-profile-login-after.log`, ProfileViewModelTest and setup.login.LoginViewModelTest passed; coverage also includes retry and snapshot-read failure after a committed choice.
- Broad gate: `/tmp/android-profile-login-verified.log`, `./gradlew :core:data:testDebugUnitTest :feature:catalog:testDebugUnitTest :feature:setup:testDebugUnitTest :feature:player:testDebugUnitTest :feature:system:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:compileDebugKotlin :app:compileDebugKotlin --no-daemon` passed. Test totals: 87 + 102 + 40 + 42 + 18 + 105 = 394; no failures/errors/skips. UI TV currently has no source.
- Hilt graph: `./gradlew :app:hiltJavaCompileDebug --no-daemon` passed (`/tmp/android-profile-login-hilt.log`).
- Searches found no old repository choose/create definitions/calls or old login package imports; scoped diff-check passed. UI error rendering was compiled; state transitions and retry behavior were tested through the production ViewModel.

No live Telegram, user databases, commits, scanner changes, or retained Gradle processes. Controller documentation may clarify setup ownership of the sign-in state machine. Existing unrelated workspace edits remain intact.
