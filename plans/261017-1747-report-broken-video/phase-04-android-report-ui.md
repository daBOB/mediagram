# Phase 4 — Android player: phone and TV sheet, last error

**Context:** spec § Where the viewer finds it, § Collecting the technical detail. Consumes phase 1 (`ReportFacts`, `reportText`, `ReportReason`, `ReportWords`, `cleanReportNote`) and phase 3 (`ReportChannel`). Patterns: `feature/player/src/main/kotlin/PlayerHandleListener.kt:32` (error), `PlayerViewModel.kt:202` (`onError`), `PlayerViewModelTogether.kt:69` (`labelOf`), `feature/system/.../SystemViewModel.kt:64` (version name), `DailyLimitGate.kt` (`repository.chosenProfile.value`), `app/.../di/CoreModule.kt:41` (`Build.MANUFACTURER + Build.MODEL`). Dialog templates: phone `ui-mobile/.../player/AddToListDialog.kt`, TV `ui-tv/.../player/TvKidsChoiceDialog.kt` / `TvAddToListDialog.kt`. TV focus rules: memory `tv-compose-focus-gotchas` (one `onExit`, conditional `focusRequester` resets targets, `focusRestorer` hijacks).

**Status:** pending.

### Task 4.1: Keep the last error; report state in the view model

**Files:** Modify `PlayerHandleListener.kt` (+ the `PlayerHandle` listener interface it calls: carry `errorCodeName` alongside the words), `PlayerViewModel.kt`; Create `feature/player/src/main/kotlin/PlayerReport.kt`; Test `feature/player/src/test/kotlin/PlayerReportTest.kt`.

**Produces:**
```kotlin
enum class ReportStage { IDLE, SENDING, SENT, FAILED }
data class ReportUi(val stage: ReportStage = ReportStage.IDLE, val open: Boolean = false, val preset: ReportReason? = null)
// PlayerViewModel:
val report: StateFlow<ReportUi>
fun openReport(fromFailure: Boolean)   // preset WONT_PLAY only when fromFailure; ignored when SENDING or SENT
fun closeReport()
fun sendReport(reason: ReportReason, note: String)
```

`PlayerReport` (plain class, constructed by the view model with the channel, a clock-free fact source and a scope) holds `lastError: String?` and the stage:
- `titleOpened()` — `lastError = null`, stage `IDLE` (called where `_openSetId` changes)
- `failed(code: String)` — `lastError = code` (from `onPlayerError`: `error.errorCodeName`)
- `send(facts)` — only from `IDLE`/`FAILED`: `SENDING` → `channel.send(reportText(facts))` → `SENT` (sheet closes) or, on any exception (log it), `FAILED` (sheet stays, shows `ReportWords.FAILED`)
- Facts: `title = titleLine(set)`, `profile = repository.chosenProfile.value?.name`, `screen = labelOf(context)`, `device = "${Build.MANUFACTURER} ${Build.MODEL}"`, `app = "Android $versionName"`, `positionSeconds = handle.positionMs() / 1000.0`, `played = "direct"`, `held = held.value`, `error = lastError`, `setId`.

- [ ] **Step 1: Failing tests** (`kotlinx-coroutines-test`, fake `ReportChannel` recording texts / throwing on demand):
  - a send hands `reportText(facts)` to the channel and ends `SENT`
  - a second `send` while `SENDING` or after `SENT` sends nothing
  - a throwing channel ends `FAILED`; a retry from `FAILED` sends
  - `failed("ERROR_CODE_X")` then `titleOpened()` → the next report's `error` is `null`
- [ ] **Step 2: Run** `./gradlew :feature:player:testDebugUnitTest --tests '*PlayerReportTest'` → FAIL.
- [ ] **Step 3: Implement**; wire `ReportChannel` through the view model's Hilt constructor.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat(android): the player keeps its last error and can send a report`.

### Task 4.2: Phone / tablet

**Files:** Modify `ui-mobile/.../player/PlayerTopBar.kt` (an `onReport: (() -> Unit)?` and `reportSent: Boolean` → a `ReportButton` after `NotesButton`, text `ReportWords.BUTTON` / `ReportWords.SENT`, disabled when sent), `PlayerFailure.kt` (a second `CatalogueTextButton` *Report* beside Retry → `openReport(fromFailure = true)`), the player screen that hosts both; Create `ui-mobile/.../player/ReportDialog.kt` (an `AlertDialog` like `AddToListDialog`: `ReportWords.HEADING`, radio rows from `ReportReason.entries`, an `OutlinedTextField` capped at `REPORT_NOTE_MAX` with `ReportWords.NOTE` as label, confirm `ReportWords.SEND` enabled once a reason is picked and not `SENDING`, dismiss `ReportWords.CANCEL`, `ReportWords.FAILED` shown under the field in `FAILED`).

- [ ] **Step 1:** Implement. **Step 2:** a Compose UI/Robolectric test only if the module already has that harness for `AddToListDialog`; otherwise covered by the device walk. **Step 3:** `./gradlew :ui-mobile:compileDebugKotlin :ui-mobile:lintDebug` green. **Step 4: Commit** `feat(android): Report on the phone and tablet player`.

### Task 4.3: TV

**Files:** Modify `ui-tv/.../player/TvPlayerTopBar.kt` (a `TvCardButton` *Report* after Notes, `TvPlayerType.ghost`, `TvGhostPadding`; focus: Notes' `right` moves from `Cancel` to the Report requester, Report's `right = Cancel`, `down = focus.seekBar`, and `lastInBar` on `TvMarksRail` becomes false whenever Report is present — add `report: FocusRequester` to `TvPlayerFocus`), `TvPlayerFailure.kt` (a second `TvOverlayButton` *Report* in a row beside Retry; Retry keeps initial focus; left/right between them), the TV player screen; Create `ui-tv/.../player/TvReportDialog.kt` (modelled on `TvKidsChoiceDialog`: reason rows focusable, first row focused on open; the note as a `TvTextField`-style row that opens the keyboard only on OK; *Send* / *Cancel*; Back = Cancel; `ReportWords.FAILED` in `FAILED`).

- [ ] **Step 1: Failing test** in `ui-tv/src/test/kotlin/ui/tv/player/` following `TvLibraryChromeUpTest`'s Robolectric setup: from Notes, pressing Right focuses *Report*; from *Report*, Down reaches the seek bar.
- [ ] **Step 2:** Implement. **Step 3:** test PASS; `./gradlew :ui-tv:testDebugUnitTest :ui-tv:lintDebug` green. **Step 4: Commit** `feat(android): Report on the TV player`.

**Success criteria:** unit tests green; `scripts/check.sh` green; TV focus test green.
