# Android part 1: kids daily limit, data path + Manage (2026-10-17)

## Native core / bindings
`scripts/generate-android-bindings.sh` run (builds all 4 ABIs from current `crates/`, regenerates `mediagram_core.kt`). .so are gitignored; bindings diff is in `android/core/rust/.../mediagram_core.kt`.
Note: uniffi puts `dailyLimit` BEFORE `parentId` in `uniffi.mediagram_core.Profile` (positional ctor callers must use names; `WatchStateFixture` fixed).

## Kotlin API for the player agent
- `model.Profile.dailyLimit: Int?` (minutes; null = none; always null on grown-ups). Added last in the ctor.
- `model.DailyLimit.kt`: `dailyLimitState(limitMinutes: Int?, watchedSeconds: Double): DailyLimitStatus(state: DailyLimitState {FREE, OK, WARN, OUT}, leftSeconds: Double?)`; `DAILY_WARN_SECONDS = 300.0`; `DailyLimitWords.LABEL / WARNING / OUT`; `DAILY_LIMIT_CHOICES: List<DailyLimitChoice(minutes: Int?, label)>`; `dailyLimitChoiceFor(minutes)` (off-menu value shows as "N min", like the web).
- `ProfileRequest.SetDailyLimit(actorId, pin, id, minutes: Int?)`; `ProfileRequest.CreateKid(..., age, dailyLimit: Int? = null)`. Values outside u16 -> `Invalid` without calling core; 1..1440 checked in core.
- `WatchStateRepository.watchedToday(profileId: String): Double` (default 0.0; real impl = `core.watchedToday(profileId, today())`, `today` = `LocalDate.now()` as stats). Suspends on `awaitCore()`, runs on the repo dispatcher.
- `ManageProfilesViewModel.setDailyLimit(kidId, minutes)`, `addKid(name, age, dailyLimit = null)`; `ManageActions` gained `onSetDailyLimit` and `onAddKid(name, age, dailyLimit)`.
- Testing: `FakeCore.setDailyLimit/createKid(5th arg)/watchedToday`, `FakeCore.watchedTodayByProfile` (seed seconds), `watchedTodayAsked`; `FakeProfiles.setDailyLimit` (same RoleAction.SET_KIDS_AGE check, 1..1440 Invalid, grown-up shown with null).

## UI
- Phone/tablet (`ManageProfilesScreen`, `ProfileForms`): each kid row gets a second line of chips (`DailyLimitChips`, fixture menu order, a11y label "Daily limit for <name>"); add-kid form gets the same chips ("Daily limit for the new kid", default No limit). Chips not dropdown: matches the existing age-limit chip idiom.
- TV: kid row reads "Name · FSK N · <limit label>"; kid dialog gets a "Daily limit · <label>" row that opens its own choice dialog (7 rows would not fit under ages + Remove); add-kid flow gains a third step (name -> FSK -> Daily limit, starting on No limit, Back walks back). Keyed rows, focusRequester only on the stable current row, as the age dialog.
- Refusals go through the same `report()`/notice path as the age limit.

## Tests (all green)
`cd android && ./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin` -> exit 0.
New: `DailyLimitFixtureTest` (all 8 fixture cases, words, menu, threshold; fixture wired into `model/build.gradle.kts` inputs), `CoreContract` daily-limit case (runs against FakeCore; real core on device only), VM set/clear/create/refusal, repository mapping + `watchedToday` date, Robolectric mobile (menu click/no-op on current/off-menu/new kid) and TV (dialog focus, no-op, set, 3-step add). Existing TV/mobile tests updated for the new row text and extra step.

## Open questions / concerns
- TV kid row always shows the limit label ("· No limit"); web select always shows it too. Say if you want it only when set.
- Real-core `CoreContract` not run here (no device); FakeCore rules mirrored from core report.
- Not done (yours/others): version bump, docs, commit. `plans/reports/*.png` untracked files are not mine.

**Status:** DONE
**Summary:** Bindings/.so regenerated, data path, shared rule + fixture test, and Manage limit on phone/tablet/TV built; full check set green.
**Concerns:** none blocking; device walk (TV focus on the new dialog/step) not done.
