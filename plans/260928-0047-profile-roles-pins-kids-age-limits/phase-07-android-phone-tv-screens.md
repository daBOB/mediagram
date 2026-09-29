# Phase 07 — Android: phone and TV screens

## Context links

- Contract: [shared-contract.md](shared-contract.md) §3 (refusals), §12 (picker start states)
- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md` §1 (not a login — the note), §2 (Managing, first admin, pre-upgrade grown-ups, wrong PINs), §4 (Kids control), §6 (Android)
- Web reference (Surface Parity — decide nothing twice): [phase-03](phase-03-web-browser-picker-manage-filter.md) line 76 (§12 on the picker: kid tiles stay in the first-profile state, Manage whenever a grown-up exists, after a first profile or claim the viewer enters by tile), `profile-manage.js` sections (`:1371-1409`), `pin-prompt.js` (`:975-1080`), `profile-picker.js` (`:1655-1760`), Kids `<select>` (`:537-546`)
- Everything drawn here comes from [phase-06](phase-06-android-data-viewmodels.md) § Interfaces → Produces
- Android visual system: root `DESIGN.md` (Components: Pill, Toggle/chip, "no component vocabulary for dialogs or lists" → Material defaults at 6dp; TV: `TvFocus`); memory: web tokens app-wide
- Bumping: [phase-08 § Bumping](phase-08-verify-docs-version.md)

## Overview

Priority P1. Status: pending (needs phase 06).
Draws what phase 06 decided, on the phone and the television: the picker's three start states
(first profile / who runs the household / tiles + Manage), the PIN — a dialog on the phone, a
D-pad-only pad on TV — Manage profiles, tiles that say "Kids · FSK 6/12", the honest note, and the
player's Kids choice. Retires `toggleKids`. Ends with a device check on the tablet that changes
nothing of the household's.

## Key insights (verified against the code, 2026-09-28; line numbers are pre-phase-06 unless noted)

- **The "not a login" note is duplicated** — `ui-mobile/.../profile/ProfilePickerScreen.kt:39-41`, `ui-tv/.../profile/TvProfilePicker.kt:34-36`. Phase 06 puts one `PICKER_NOTE` (the web's sentence, "Android" for "a browser") in `catalog.profile`; both pickers read it.
- **Tiles say "KIDS" today**: `ProfilePickerScreen.kt:151-157`, `TvProfileTiles.kt:81-90` → `Profile.kidsTag` ("Kids · FSK N", the web's tile text, phase-03 `:1745`).
- **Gates**: `ProfileGate.kt:24-38`, `TvProfileGate.kt:36-51` resolve `ProfileViewModel` with `hiltViewModel()`. Test fixtures hand VMs out from a map whose `getValue` throws for a missing class (`ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt:236-260`, `ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt:116-125`) — resolving `ManageProfilesViewModel` in the gate means both maps gain it.
- **TV composes flows in place, not over** (`TvProfilePicker.kt:90-104` comment): the add flow replaced the tile row so nothing behind competes for the remote. The PIN pad and Manage follow that; the phone uses dialogs over the screen (`ProfilePickerScreen.kt:122-133`).
- **TV dialog focus** must be requested inside the Dialog's own window — `TvDialog` does it (`ui-tv/.../setup/TvConfirmDialog.kt:86-99`); a phone `AlertDialog` needs the same for its field. `TvConfirmDialog` puts Cancel first for a costly press (`:51-75`). `TvChoiceRow` draws a radio row (`ui-tv/.../player/TvSettingsChoices.kt:104-130`); `TvSettingsHeading` a section title (`:88-95`); `TvTextRow` keeps focus while not enabled by swallowing the press (`ui-tv/.../TvTextRow.kt:24-60`).
- **Height budget on a 960×540 dp television**: the picker's width test already runs at `w960dp-h540dp` (`TvProfilePickerStateTest.kt:118-134`). A pad of four 72dp rows plus heading, dots, error and Cancel runs past 540dp; 56dp keys fit (4×56 + 3×8 = 248dp for the grid).
- **Phone controls**: `LinePill`, `QuietPill(small, contentDescription)`, `SettingsChip` (`ui-mobile/.../settings/SettingsControls.kt:53,76,106`), internal to ui-mobile. A shared callback bundle for both surfaces has precedent in ui-common (`ui-common/src/main/kotlin/ui/MenuActions.kt`).
- **Player Kids control**: phone `PlayerMarks.kt:43-50` (local dialog state precedent `:36`), wired at `PlayerScreen.kt:38,155`; TV `TvMarksRail.kt:150-157`, wired at `TvPlayerControlsBridge.kt:14,59-67`. A TV dialog must live at screen level so it outlasts the fading controls (`TvMarksRail.kt:164-168`); the list dialog's state and plumbing are `TvPlayerScreen.kt:108,110-111,134,185,195-204` and `TvAddToListOverPlayer` (`TvPlayerControlsBridge.kt:90-110`). Phone menus already use `DropdownMenu` (`ui-mobile/.../catalog/TitlePills.kt:69`). `toggleKids` callers left by phase 06: `PlayerViewModelDelegates.kt:48`, `PlayerActionFailureTest.kt:87`, `PlayerActionNoticeTest.kt:44,165`, `PlayerMarksTest.kt:90-108`.
- **TV player tests** use a relaxed mockk repository and `coVerify` on writes (`ui-tv/src/test/.../player/TvPlayerMarksTest.kt:48-57`).
- **App-level TV walks** press nodes by semantics (`TvHousekeepingTest.kt:214-217`) over `TvAppFixture`; its viewer is a hand fake (`TvAppFixture.kt:155-156`) with no rules. Manage walks need `FakeCore`'s rules, so the fixture takes an optional `FakeCore` for the viewer.
- **Android Settings shows no profile at all** (no "Who is watching" panel: `git grep` over `ui-mobile/.../settings`, `ui-tv/.../system`); the web's `settings-page.js:98-114` does, and phase 03 makes it `Name · Kids · FSK N`. The kid's limit on Android shows on the tiles, the Manage rows and the empty shelf (phase 06 Task 2). The missing panel is a pre-existing parity gap — listed in the report, not built here.
- **Device checks can change the whole household** (memory `profile-data-spreads-household-wide`): a PIN, a kid, a limit or an admin claim made on the tablet syncs everywhere, and a removal does not follow. The earliest admin claim wins for good.

## Requirements

Functional (phone and TV alike)
- Picker (§12): no grown-up → "Create the first profile — it runs this household" (name, then PIN twice) with "Look again", kid tiles still shown, no Manage; grown-ups, no admin → "Who runs this household?" (grown-ups only) above the tiles; a grown-up exists → "Manage profiles". Tiles "Kids · FSK N". `PICKER_NOTE`. "Stay as I am" as today.
- A kid's tile opens at once; a grown-up's opens the PIN (or a first PIN, twice); the prompt shows the web's refusal sentences, "Try again in N s" included.
- PIN entry: phone — number pad, masked, handed over at four digits; TV — a 4-digit pad driven by the D-pad alone, remote starts on "1", "Delete" takes one back, dots not digits.
- Manage: "Who are you?" (grown-ups) → PIN → "As <name>"; admin: Grown-ups (Reset PIN, Remove — asks, names that their kids go too), Add a grown-up (name → PIN twice); everyone: Kids (FSK 6 / 12, Remove), Add a kid (name + FSK, 6 first), Your PIN → Change your PIN; Done / Back closes and drops the PIN.
- Player: Kids control on an unrated title offers Not for kids / From 6 / From 12 (phone menu, TV dialog); rated shows its verdict, locked; hidden on a kids profile.
- `toggleKids` gone.

Non-functional
- New files < 200 lines; words only from `catalog.profile` / `player` (no second copy); no plan refs; each commit bumps.
- Nothing on the TV needs a touchscreen or a keyboard beyond the on-screen one for names.

## Architecture

```
ProfileGate (phone)                                   TvProfileGate (TV)
 ├ chosen → content                                    ├ chosen → content
 ├ Manage open → ManageProfilesScreen + PinDialog      ├ Manage open → TvPinPrompt (if asking) | TvManageProfiles
 └ else → ProfilePickerScreen + PinDialog              ├ picker asking → TvPinPrompt
                                                       └ else → TvProfilePicker (+ TvAddProfileFlow in place for a first profile)
Both: ProfileViewModel(pick, claim, createFirst, enterPin, cancelPin, pin) · ManageProfilesViewModel(… , state, pin) · ManageActions (ui-common)
Player: kidsLabel(marks) button → phone DropdownMenu / TV TvKidsChoiceDialog (screen level) → PlayerViewModel.setKidsMark(age)
```

## Interfaces

**Consumes (phase 06):** `ProfileUiState.Picking{needsFirstProfile, needsAdmin, grownUps}`; `ProfileViewModel.{pin, pick, claim, createFirst, enterPin, cancelPin, stay, retry, reopen}`; `ManageUiState`; `ManageProfilesViewModel.{state, pin, open, actAs, addKid, setKidsAge, remove, addGrownUp, changePin, enterPin, cancelPin, close}`; `PinPrompt{heading, error, busy}`, `PIN_LENGTH`; words `PICKER_NOTE, FIRST_PROFILE, WHO_RUNS_THIS, MANAGE_PROFILES, LOOK_AGAIN, WHO_ARE_YOU, GROWN_UPS, KIDS_SECTION, YOUR_PIN, ADD_A_KID, ADD_A_GROWN_UP, CHANGE_YOUR_PIN, RESET_PIN, REMOVE, DONE, managingAs(), removeQuestion(), Profile.kidsTag`; `KIDS_LIMITS`; `PlayerMarksState.kidsMark`, `kidsLabel`, `KIDS_CHOICES`, `PlayerViewModel.setKidsMark`.

**Produces:** `ui.profile.ManageActions` + `ManageProfilesViewModel.actions()` (ui-common); test tags `PinFieldTag`, `tvPinKeyTag(key)`, `TvPinDotsTag`, `TvFirstProfileTag`, `TvManageProfilesTag`, `tvClaimTag(id)`, `tvKidsLimitTag(age)`; `TvAppFixture(…, viewerCore: FakeCore? = null)`.

## Related code files

Create
- `android/ui-common/src/main/kotlin/ui/profile/ManageActions.kt`
- `android/ui-mobile/src/main/kotlin/ui/profile/{PinDialog,ManageProfilesScreen,AddProfileDialogs}.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/profile/{TvPinPad,TvPinPrompt,TvManageProfiles,TvManageDialogs,TvPickerQuestions}.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/player/TvKidsChoiceDialog.kt`
- Tests: `android/ui-mobile/src/test/kotlin/ui/profile/{PinDialogTest,ManageProfilesScreenTest,ProfilePickerScreenTest}.kt`, `android/ui-mobile/src/test/kotlin/ui/player/PlayerMarksKidsChoiceTest.kt`, `android/ui-tv/src/test/kotlin/ui/tv/profile/{TvPinPadStateTest,TvManageProfilesStateTest}.kt`, `android/ui-tv/src/test/kotlin/ui/tv/TvManageProfilesFlowTest.kt`, `android/ui-tv/src/androidTest/kotlin/ui/tv/profile/TvPinPadTest.kt`

Modify
- Phone: `ui-mobile/src/main/kotlin/ui/profile/{ProfileGate,ProfilePickerScreen,RemoveProfileDialog}.kt`, `ui-mobile/src/main/kotlin/ui/player/{PlayerMarks,PlayerScreen}.kt`; test `ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`
- TV: `ui-tv/src/main/kotlin/ui/tv/profile/{TvProfileGate,TvProfilePicker,TvProfileTiles,TvAddProfileFlow,TvRemoveProfileDialog}.kt`, `ui-tv/src/main/kotlin/ui/tv/player/{TvMarksRail,TvPlayerControlsBridge,TvPlayerScreen}.kt`; tests `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt`, `ui-tv/src/test/kotlin/ui/tv/profile/TvProfilePickerStateTest.kt`, `ui-tv/src/test/kotlin/ui/tv/player/TvPlayerMarksTest.kt`, `ui-tv/src/androidTest/kotlin/ui/tv/profile/TvProfilePickerTest.kt`
- **Sequential edits in phase 06's files** (retiring `toggleKids`): `feature/player/src/main/kotlin/{PlayerMarksController,PlayerViewModelDelegates}.kt`; tests `feature/player/src/test/kotlin/{PlayerMarksTest,PlayerActionFailureTest,PlayerActionNoticeTest}.kt`

Delete: none (`RemoveProfileDialog.kt`, `TvAddProfileFlow.kt`, `TvRemoveProfileDialog.kt` are reworked in place; `TvAddTile` and `TvProfilePickerAddTileTag` removed from their files).

## Implementation steps

Commands from `/home/andre/Workspace/mediagram/android`. Robolectric tests use the house harness (`createEmptyComposeRule()` + `Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()`, closed in `@After`), as `ui-mobile/src/test/kotlin/ui/catalog/OptionalMetadataTest.kt` and `TvProfilePickerStateTest.kt` do; it is written out once in Task 1 and reused.

### Task 1: The phone's PIN dialog

- [ ] **Step 1: failing test** — `ui-mobile/src/test/kotlin/ui/profile/PinDialogTest.kt`

```kotlin
package ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import catalog.profile.PinPrompt
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinDialogTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(prompt: PinPrompt, onPin: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { PinDialog(prompt, onPin, onDismiss) } }
        }
        compose.waitForIdle()
    }

    @Test
    fun fourDigitsAreHandedOverAndTheFieldStartsAgain() {
        val entered = mutableListOf<String>()
        show(PinPrompt("andre’s PIN", newPin = false), onPin = { entered += it })
        compose.onNodeWithText("andre’s PIN").assertExists()
        compose.onNodeWithTag(PinFieldTag).performTextInput("12a3")
        assertEquals(emptyList(), entered)
        compose.onNodeWithTag(PinFieldTag).performTextInput("4")
        compose.onNodeWithTag(PinFieldTag).performTextInput("5678")
        assertEquals(listOf("1234", "5678"), entered)
    }

    @Test
    fun aNewPinsSecondEntryAndAWaitAreSaid() {
        show(PinPrompt("Choose a PIN for test", newPin = true, confirming = true, error = "Too many wrong PINs. Try again in 42 s."))
        compose.onNodeWithText("The same PIN again").assertExists()
        compose.onNodeWithText("Too many wrong PINs. Try again in 42 s.").assertExists()
    }

    @Test
    fun cancelLeavesWithoutAPin() {
        var dismissed = false
        show(PinPrompt("andre’s PIN", newPin = false), onDismiss = { dismissed = true })
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }
}
```

- [ ] **Step 2: run, expect failure** — `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.profile.PinDialogTest'` → `Unresolved reference 'PinDialog'`, `'PinFieldTag'`.

- [ ] **Step 3: implement** `ui-mobile/src/main/kotlin/ui/profile/PinDialog.kt`

```kotlin
package ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import catalog.profile.PIN_LENGTH
import catalog.profile.PinPrompt
import designsystem.Spacing

/** The PIN field's tag, for tests. */
internal const val PinFieldTag = "pin-field"

/**
 * The one PIN dialog on the phone, for the picker and Manage alike: four
 * digits on the number pad, masked, nothing learnt by the keyboard. The
 * digits go the moment there are four and the field empties, so a new PIN's
 * second entry and a retry after a refusal both start from nothing — as the
 * web's `pin-prompt.js` clears its fields. [PinPrompt.heading] says what is
 * wanted; [PinPrompt.error] why the last try failed, a wait included.
 */
@Composable
internal fun PinDialog(
    prompt: PinPrompt,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.heading) },
        text = {
            // Inside the dialog's own content: a dialog is a second window,
            // and the field has to exist there before it can take focus.
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Column {
                OutlinedTextField(
                    value = digits,
                    onValueChange = { typed ->
                        val clean = typed.filter(Char::isDigit).take(PIN_LENGTH)
                        digits = if (clean.length == PIN_LENGTH) "" else clean
                        if (clean.length == PIN_LENGTH) onPin(clean)
                    },
                    enabled = !prompt.busy,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
                    modifier = Modifier.focusRequester(focus).testTag(PinFieldTag),
                )
                prompt.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.small),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 4: run, expect pass** — command from Step 2.
- [ ] **Step 5: commit** — bump (phase-08 § Bumping), then `git add android/ui-mobile <manifests> docs/project-changelog.md && git commit -m "feat(android): a PIN dialog on the phone, four digits and gone; release <next>"`.

### Task 2: Manage profiles on the phone

- [ ] **Step 1: failing test** — `ui-mobile/src/test/kotlin/ui/profile/ManageProfilesScreenTest.kt` (harness as Task 1, `@Config(sdk = [35], qualifiers = "w400dp-h2400dp")` so every section is in the window; `show { ManageProfilesScreen(state, actions) }` inside `MaterialTheme`):

```kotlin
    private val andre = Profile("a", "andre", admin = true, hasPin = true)
    private val bea = Profile("b", "Bea", hasPin = true)
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")
    private val calls = mutableListOf<String>()
    private val actions =
        ManageActions(
            onActAs = { calls += "actAs $it" },
            onAddKid = { name, age -> calls += "addKid $name $age" },
            onSetKidsAge = { id, age -> calls += "age $id $age" },
            onRemove = { calls += "remove $it" },
            onAddGrownUp = { calls += "addGrownUp $it" },
            onChangePin = { calls += "pin $it" },
            onClose = { calls += "close" },
        )

    @Test
    fun whoAreYouOffersTheGrownUpsGiven() {
        show(ManageUiState.ChoosingActor(listOf(andre, bea)))
        compose.onNodeWithText("Who are you?").assertExists()
        compose.onNodeWithText("Bea").performClick()
        assertEquals(listOf("actAs b"), calls)
    }

    @Test
    fun aParentSeesItsKidsWithTheirLimitAndNoGrownUps() {
        show(ManageUiState.Managing(bea, grownUps = emptyList(), kids = listOf(tom), canAddGrownUp = false))
        compose.onNodeWithText("As Bea").assertExists()
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        compose.onNodeWithText("Add a grown-up").assertDoesNotExist()
        compose.onNodeWithText("FSK 6").performClick()
        assertEquals(listOf("age t 6"), calls)
    }

    @Test
    fun theAdminResetsAndRemovesGrownUpsAndIsAskedFirst() {
        show(ManageUiState.Managing(andre, grownUps = listOf(bea), kids = emptyList(), canAddGrownUp = true))
        compose.onNodeWithText("Reset PIN").performClick()
        compose.onNodeWithContentDescription("Remove Bea").performClick()
        compose.onNodeWithText("Remove Bea, their kids, and everything they have watched?").assertExists()
        compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("pin b", "remove b"), calls)
    }

    @Test
    fun aNewKidNeedsANameAndStartsAtSix() {
        show(ManageUiState.Managing(bea, emptyList(), emptyList(), canAddGrownUp = false))
        compose.onNodeWithText("Add a kid").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Lina")
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf("addKid Lina 6"), calls)
    }

    @Test
    fun whyTheLastChangeFailedIsSaid() {
        show(ManageUiState.Managing(bea, emptyList(), emptyList(), canAddGrownUp = false, notice = "Your profile cannot do that."))
        compose.onNodeWithText("Your profile cannot do that.").assertExists()
        compose.onNodeWithText("Change your PIN").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("pin b", "close"), calls)
    }
```

- [ ] **Step 2: run, expect failure** — `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.profile.ManageProfilesScreenTest'` → `Unresolved reference 'ManageActions'`, `'ManageProfilesScreen'`.

- [ ] **Step 3: implement.** `ui-common/src/main/kotlin/ui/profile/ManageActions.kt`:

```kotlin
package ui.profile

import catalog.profile.ManageProfilesViewModel

/**
 * What Manage profiles can ask for — the phone's screen and the television's
 * list take this one bundle, so the two offer the same actions under the same
 * names, the way `MenuActions` keeps their menus alike.
 */
data class ManageActions(
    val onActAs: (id: String) -> Unit,
    val onAddKid: (name: String, age: Int) -> Unit,
    val onSetKidsAge: (id: String, age: Int) -> Unit,
    val onRemove: (id: String) -> Unit,
    val onAddGrownUp: (name: String) -> Unit,
    val onChangePin: (id: String) -> Unit,
    val onClose: () -> Unit,
)

/** [ManageActions] answered by the view model that holds the panel's PIN. */
fun ManageProfilesViewModel.actions(): ManageActions =
    ManageActions(::actAs, ::addKid, ::setKidsAge, ::remove, ::addGrownUp, ::changePin, ::close)
```

`ui-mobile/src/main/kotlin/ui/profile/ManageProfilesScreen.kt`:

```kotlin
package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.DONE
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageUiState
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import catalog.profile.WHO_ARE_YOU
import catalog.profile.YOUR_PIN
import catalog.profile.managingAs
import designsystem.Spacing
import model.KIDS_LIMITS
import model.Profile
import ui.settings.LinePill
import ui.settings.QuietPill
import ui.settings.SettingsChip

/**
 * Manage profiles on the phone — the web's `profile-manage.js`, section for
 * section: who you are; then, for the admin, the other grown-ups and a way to
 * add one; for every grown-up, its own kids at FSK 6 or 12 and a way to add
 * one; and its own PIN. The PIN itself is asked by [PinDialog] over this, from
 * the view model's prompt; a removal asks first ([RemoveProfileDialog]).
 */
@Composable
internal fun ManageProfilesScreen(
    state: ManageUiState,
    actions: ManageActions,
) {
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> WhoAreYou(state.grownUps, actions)
        is ManageUiState.Managing -> Managing(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoAreYou(
    grownUps: List<Profile>,
    actions: ManageActions,
) {
    Page {
        Text(WHO_ARE_YOU, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(top = Spacing.small)) {
            grownUps.forEach { profile -> LinePill(profile.name, onClick = { actions.onActAs(profile.id) }) }
        }
        QuietPill(DONE, onClick = actions.onClose, modifier = Modifier.padding(top = Spacing.large))
    }
}

@Composable
private fun Managing(
    state: ManageUiState.Managing,
    actions: ManageActions,
) {
    // Which add form is open: a kid, a grown-up, or none.
    var addingKid by remember { mutableStateOf<Boolean?>(null) }
    var removing by remember { mutableStateOf<Profile?>(null) }
    Page {
        Text(managingAs(state.actor.name), style = MaterialTheme.typography.bodyMedium)
        state.notice?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.small))
        }
        if (state.canAddGrownUp) {
            Section(GROWN_UPS)
            state.grownUps.forEach { grownUp ->
                ProfileRow(grownUp.name) {
                    QuietPill(RESET_PIN, onClick = { actions.onChangePin(grownUp.id) }, small = true)
                    QuietPill(REMOVE, onClick = { removing = grownUp }, small = true, contentDescription = "$REMOVE ${grownUp.name}")
                }
            }
            LinePill(ADD_A_GROWN_UP, onClick = { addingKid = false }, modifier = Modifier.padding(top = Spacing.small))
        }
        Section(KIDS_SECTION)
        state.kids.forEach { kid ->
            ProfileRow(kid.name) {
                KIDS_LIMITS.forEach { age -> SettingsChip("FSK $age", selected = kid.kidsLimit == age, onClick = { actions.onSetKidsAge(kid.id, age) }) }
                QuietPill(REMOVE, onClick = { removing = kid }, small = true, contentDescription = "$REMOVE ${kid.name}")
            }
        }
        LinePill(ADD_A_KID, onClick = { addingKid = true }, modifier = Modifier.padding(top = Spacing.small))
        Section(YOUR_PIN)
        QuietPill(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(state.actor.id) })
        LinePill(DONE, onClick = actions.onClose, modifier = Modifier.padding(top = Spacing.large))
    }
    when (addingKid) {
        true -> AddKidDialog(onAdd = { name, age -> addingKid = null; actions.onAddKid(name, age) }, onDismiss = { addingKid = null })
        false -> AddGrownUpDialog(onAdd = { name -> addingKid = null; actions.onAddGrownUp(name) }, onDismiss = { addingKid = null })
        null -> Unit
    }
    removing?.let { target -> RemoveProfileDialog(target, onRemove = { actions.onRemove(target.id) }, onDismiss = { removing = null }) }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.large)) {
        Text(MANAGE_PROFILES, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.large, bottom = Spacing.small))
}

@Composable
private fun ProfileRow(
    name: String,
    controls: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        controls()
    }
}
```

`ui-mobile/src/main/kotlin/ui/profile/AddProfileDialogs.kt`:

```kotlin
package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import designsystem.Spacing
import model.KIDS_LIMITS
import ui.settings.SettingsChip

/** A kid: a name and its limit — FSK 6 unless its parent picks 12, the web's own first choice. */
@Composable
internal fun AddKidDialog(
    onAdd: (name: String, age: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableIntStateOf(KIDS_LIMITS.min()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ADD_A_KID) },
        text = {
            Column {
                NameField(name) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(top = Spacing.medium)) {
                    KIDS_LIMITS.forEach { limit -> SettingsChip("FSK $limit", selected = age == limit, onClick = { age = limit }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name.trim(), age) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A grown-up: a name here; its first PIN is asked next, twice, by [PinDialog]. */
@Composable
internal fun AddGrownUpDialog(
    onAdd: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ADD_A_GROWN_UP) },
        text = { NameField(name) { name = it } },
        confirmButton = { TextButton(onClick = { onAdd(name.trim()) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun NameField(
    name: String,
    onName: (String) -> Unit,
) {
    OutlinedTextField(value = name, onValueChange = onName, singleLine = true, label = { Text("Name") })
}
```

`ui-mobile/src/main/kotlin/ui/profile/RemoveProfileDialog.kt` — reworked whole:

```kotlin
package ui.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import catalog.profile.REMOVE
import catalog.profile.removeQuestion
import model.Profile

/** Asked before a removal, in the web's words; a grown-up's kids go with it, and the question says so. */
@Composable
internal fun RemoveProfileDialog(
    profile: Profile,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(removeQuestion(profile)) },
        confirmButton = {
            TextButton(onClick = {
                onRemove()
                onDismiss()
            }) { Text(REMOVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 4: run, expect pass** — command from Step 2.
- [ ] **Step 5: commit** — bump, then `git add android/ui-common android/ui-mobile <manifests> docs/project-changelog.md && git commit -m "feat(android): Manage profiles on the phone, by role; release <next>"`.

### Task 3: The phone's picker — first profile, who runs the household, the PIN, Manage

- [ ] **Step 1: failing test** — `ui-mobile/src/test/kotlin/ui/profile/ProfilePickerScreenTest.kt` (harness as Task 1; `show(state)` renders `ProfilePickerScreen(state, actions)` with `actions` recording like Task 2's):

```kotlin
    private val calls = mutableListOf<String>()
    private val actions =
        PickerActions(
            onPick = { calls += "pick $it" },
            onClaim = { calls += "claim $it" },
            onCreateFirst = { calls += "first $it" },
            onManage = { calls += "manage" },
            onStay = { calls += "stay" },
            onRetry = { calls += "retry" },
        )

    @Test
    fun aKidsTileNamesItsOwnLimitAndOpensOnATap() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true), Profile("m", "Mia", kids = true, kidsAge = 6)), canStay = false))
        compose.onNodeWithText("Kids · FSK 6").assertExists()
        compose.onNodeWithText("Mia").performClick()
        assertEquals(listOf("pick m"), calls)
    }

    @Test
    fun aDeviceWithNoGrownUpMakesTheFirstAndStillOpensItsKids() {
        show(ProfileUiState.Picking(listOf(Profile("k", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Create the first profile — it runs this household").assertExists()
        compose.onNodeWithText("TV kids").assertExists()
        compose.onNodeWithText("Manage profiles").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Ann")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Look again").performClick()
        assertEquals(listOf("first Ann", "retry"), calls)
    }

    @Test
    fun grownUpsWithNoAdminAreAskedWhoRunsTheHousehold() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre"), Profile("m", "Mia", kids = true)), canStay = false))
        compose.onNodeWithText("Who runs this household?").assertExists()
        compose.onNodeWithContentDescription("Mia runs this household").assertDoesNotExist()
        compose.onNodeWithContentDescription("andre runs this household").performClick()
        assertEquals(listOf("claim a"), calls)
    }

    @Test
    fun anAdminHouseholdOffersManageAndSaysThePinIsNotALogin() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true)), canStay = true))
        compose.onNodeWithText("Who runs this household?").assertDoesNotExist()
        compose.onNodeWithText("it is not a login", substring = true).assertExists()
        compose.onNodeWithText("Manage profiles").performClick()
        compose.onNodeWithText("Stay as I am").performClick()
        assertEquals(listOf("manage", "stay"), calls)
    }
```

- [ ] **Step 2: run, expect failure** — `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.profile.ProfilePickerScreenTest'` → `Unresolved reference 'PickerActions'`, `No value passed for parameter 'onChoose'`.

- [ ] **Step 3: implement.** `ui-mobile/src/main/kotlin/ui/profile/ProfilePickerScreen.kt` — whole file (the tile's `Initial`/`initialOf` keep their bodies from `:175-190`):

```kotlin
package ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.profile.FIRST_PROFILE
import catalog.profile.LOOK_AGAIN
import catalog.profile.MANAGE_PROFILES
import catalog.profile.PICKER_NOTE
import catalog.profile.ProfileUiState
import catalog.profile.WHO_RUNS_THIS
import catalog.profile.kidsTag
import designsystem.Spacing
import model.Profile
import ui.settings.LinePill
import ui.settings.QuietPill

private const val HEADING = "Who's watching?"

/** What the picker can ask for, as one bundle. */
internal data class PickerActions(
    val onPick: (String) -> Unit,
    val onClaim: (String) -> Unit,
    val onCreateFirst: (String) -> Unit,
    val onManage: () -> Unit,
    val onStay: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The web's picker (`profile-picker.js`) and its three start states: no
 * grown-up yet — make the first, who runs the household; grown-ups but no
 * admin — "Who runs this household?" above the tiles; otherwise the tiles
 * and Manage profiles. A kid's tile opens at once, a grown-up's asks its PIN
 * ([ProfileGate] draws [PinDialog]). Renders nothing for [ProfileUiState.Chosen].
 */
@Composable
internal fun ProfilePickerScreen(
    state: ProfileUiState,
    actions: PickerActions,
) {
    when (state) {
        ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ProfileUiState.Picking -> PickerBody(state, actions)
        is ProfileUiState.Chosen -> Unit
    }
}

@Composable
private fun PickerBody(
    state: ProfileUiState.Picking,
    actions: PickerActions,
) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
        Text(HEADING, style = MaterialTheme.typography.headlineSmall)
        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small))
            TextButton(onClick = actions.onRetry) { Text("Try again") }
        }
        // A load that just failed is answered by trying again, not by starting a household.
        when {
            state.error != null -> Unit
            state.needsFirstProfile -> FirstProfile(onCreate = actions.onCreateFirst, onLookAgain = actions.onRetry)
            state.needsAdmin -> AdminQuestion(state.grownUps, actions.onClaim)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize().weight(1f).padding(top = Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(items = state.profiles, key = Profile::id) { profile -> ProfileTile(profile, onClick = { actions.onPick(profile.id) }) }
        }
        Text(PICKER_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.grownUps.isNotEmpty()) {
            TextButton(onClick = actions.onManage, modifier = Modifier.padding(top = Spacing.small)) { Text(MANAGE_PROFILES) }
        }
        if (state.canStay) {
            TextButton(onClick = actions.onStay, modifier = Modifier.padding(top = Spacing.small)) { Text("Stay as I am") }
        }
    }
}

/**
 * The first grown-up on a device with none. "Look again" is for a device
 * whose first sync has not come in yet: a household that already has
 * profiles brings them on the next round, and making a first one meanwhile
 * would only lose the admin role to the household's older claim.
 */
@Composable
private fun FirstProfile(
    onCreate: (String) -> Unit,
    onLookAgain: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    Text(FIRST_PROFILE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.small))
    // The field above its buttons, not beside them: a text field's own
    // minimum width leaves no room for a pill on a 400dp phone.
    NameField(name) { name = it }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(top = Spacing.small)) {
        LinePill("Continue", onClick = { onCreate(name) }, enabled = name.isNotBlank())
        QuietPill(LOOK_AGAIN, onClick = onLookAgain)
    }
}

/** Asked while nobody runs the household; only grown-ups are offered, each a PIN away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminQuestion(
    grownUps: List<Profile>,
    onClaim: (String) -> Unit,
) {
    Text(WHO_RUNS_THIS, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.medium))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(top = Spacing.small)) {
        grownUps.forEach { profile ->
            QuietPill(profile.name, onClick = { onClaim(profile.id) }, contentDescription = "${profile.name} runs this household")
        }
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Initial(profile.name)
        Text(profile.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.small))
        profile.kidsTag?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

/** The letter on a tile. */
@Composable
private fun Initial(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(initialOf(text), style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun initialOf(name: String): String = name.trim().take(1).uppercase().ifEmpty { "?" }
```

`ui-mobile/src/main/kotlin/ui/profile/ProfileGate.kt` — whole file:

```kotlin
package ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel
import ui.ProfileBarState

/**
 * Gates [content] on a chosen profile: a viewer, not the setup step the app
 * already answers, decides whose shelves these are. Shows the picker while
 * there is nobody chosen, and hands [content] the bar state once there is —
 * the name [ui.LibraryScaffold] shows, and what tapping it reopens.
 *
 * Manage profiles, opened from the picker, takes the picker's place until
 * Done; once a profile is chosen it cannot be reached, so a phone left on a
 * grown-up does not hand a child the controls.
 */
@Composable
internal fun ProfileGate(content: @Composable (ProfileBarState) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val managing by manage.state.collectAsStateWithLifecycle()
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val chosen = state as? ProfileUiState.Chosen

    when {
        chosen != null -> content(ProfileBarState(name = chosen.profile.name, onChoose = viewModel::reopen))
        managing != ManageUiState.Closed -> ManageProfilesRoute(manage, managing)
        else -> {
            ProfilePickerScreen(
                state = state,
                actions =
                    PickerActions(
                        onPick = viewModel::pick,
                        onClaim = viewModel::claim,
                        onCreateFirst = viewModel::createFirst,
                        onManage = manage::open,
                        onStay = viewModel::stay,
                        onRetry = viewModel::retry,
                    ),
            )
            pin?.let { PinDialog(it, onPin = viewModel::enterPin, onDismiss = viewModel::cancelPin) }
        }
    }
}

/** Manage over its view model: the screen, the PIN over it, and Back as Done. */
@Composable
private fun ManageProfilesRoute(
    viewModel: ManageProfilesViewModel,
    state: ManageUiState,
) {
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { viewModel.actions() }
    BackHandler(onBack = actions.onClose)
    ManageProfilesScreen(state, actions)
    pin?.let { PinDialog(it, onPin = viewModel::enterPin, onDismiss = viewModel::cancelPin) }
}
```

`ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt:118` — add beside `ProfileViewModel`: `ManageProfilesViewModel::class.java to ManageProfilesViewModel(stored, mockk<WatchSync>(relaxed = true)),` (the gate resolves it every time now).

- [ ] **Step 4: run, expect pass** — `./gradlew :ui-mobile:testDebugUnitTest` (the whole module: `LibraryFlowTest` goes through the gate).
- [ ] **Step 5: commit** — bump, then `git add android/ui-mobile <manifests> docs/project-changelog.md && git commit -m "feat(android): the phone's picker asks a grown-up's PIN, makes the first profile and opens Manage; release <next>"`.

### Task 4: A PIN pad for the remote

- [ ] **Step 1: failing tests.** Robolectric `ui-tv/src/test/kotlin/ui/tv/profile/TvPinPadStateTest.kt` (harness as `TvProfilePickerStateTest.kt:39-53,149-160`, `TvTheme { … }`, `@Config(sdk = [35], qualifiers = "w960dp-h540dp")`):

```kotlin
    private fun press(key: String) {
        compose.onNodeWithTag(tvPinKeyTag(key)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun fourKeysHandThePinOverAndThePadEmpties() {
        val entered = mutableListOf<String>()
        show { TvPinPrompt(PinPrompt("andre’s PIN", newPin = false), onPin = { entered += it }, onCancel = {}) }
        listOf("1", "2", "3").forEach(::press)
        compose.onNodeWithTag(TvPinDotsTag).assertTextEquals("●●●○")
        press("4")
        assertEquals(listOf("1234"), entered)
        compose.onNodeWithTag(TvPinDotsTag).assertTextEquals("○○○○")
    }

    @Test
    fun deleteTakesTheLastDigitBack() {
        val entered = mutableListOf<String>()
        show { TvPinPrompt(PinPrompt("andre’s PIN", newPin = false), onPin = { entered += it }, onCancel = {}) }
        listOf("1", "2", "Delete", "3", "4", "5").forEach(::press)
        assertEquals(listOf("1345"), entered)
    }

    @Test
    fun thePromptSaysWhatItWantsAndWhyTheLastTryFailed() {
        var cancelled = false
        show { TvPinPrompt(PinPrompt("andre’s PIN", newPin = false, error = "Wrong PIN."), onPin = {}, onCancel = { cancelled = true }) }
        compose.onNodeWithText("andre’s PIN").assertExists()
        compose.onNodeWithText("Wrong PIN.").assertExists()
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(cancelled)
    }

    /** Heading, error, dots, four rows of keys and Cancel on the 540dp-tall box this is seen on. */
    @Test
    fun thePadAndCancelFitA540dpTelevision() {
        show { TvPinPrompt(PinPrompt("Choose a PIN for test", newPin = true, error = "The two PINs are not the same."), onPin = {}, onCancel = {}) }
        val screen = compose.onRoot().getBoundsInRoot()
        val cancel = compose.onNodeWithText("Cancel").getBoundsInRoot()
        assertTrue(cancel.bottom <= screen.bottom - Overscan.vertical + 1.dp, "Cancel ends at ${cancel.bottom}")
    }
```

androidTest `ui-tv/src/androidTest/kotlin/ui/tv/profile/TvPinPadTest.kt` — the D-pad alone, on a real window manager:

```kotlin
package ui.tv.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme

/** Initial focus and D-pad travel are real window-manager behaviour; [TvPinPadStateTest] covers the rest without one. */
@RunWith(AndroidJUnit4::class)
class TvPinPadTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(onPin: (String) -> Unit = {}) {
        compose.setContent { TvTheme { TvPinPad(enabled = true, onPin = onPin) } }
    }

    private fun press(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }

    @Test
    fun theRemoteStartsOnOneAndWalksTheGrid() {
        show()
        compose.onNodeWithTag(tvPinKeyTag("1")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvPinKeyTag("2")).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(tvPinKeyTag("5")).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(tvPinKeyTag("0")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvPinKeyTag("Delete")).assertIsFocused()
    }

    @Test
    fun centreTypesAndTheFourthDigitHandsThePinOver() {
        var pin: String? = null
        show { pin = it }
        repeat(2) { press(Key.DirectionCenter) }
        press(Key.DirectionRight)
        repeat(2) { press(Key.DirectionCenter) }
        assertEquals("1122", pin)
    }
}
```

- [ ] **Step 2: run, expect failure** — `./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.profile.TvPinPadStateTest'` → `Unresolved reference 'TvPinPrompt'`, `'tvPinKeyTag'`; `./gradlew :ui-tv:compileDebugAndroidTestKotlin` → `Unresolved reference 'TvPinPad'`.

- [ ] **Step 3: implement** `ui-tv/src/main/kotlin/ui/tv/profile/TvPinPad.kt`

```kotlin
package ui.tv.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import catalog.profile.PIN_LENGTH
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus

private const val DELETE = "Delete"
private val Keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(null, "0", DELETE))

// Four rows of these, with the heading, dots, an error line and Cancel, fit
// a 540dp-tall television inside its overscan; 72dp keys did not.
private val KeySize = 56.dp

/** A key's tag — `tv-pin-key-0` … `tv-pin-key-9`, `tv-pin-key-Delete` — for tests. */
internal fun tvPinKeyTag(key: String) = "tv-pin-key-$key"

/** The dots that say how many digits are in. */
internal const val TvPinDotsTag = "tv-pin-dots"

/**
 * Four digits from a remote with nothing but a D-pad: a telephone grid the
 * remote walks in two directions, Centre typing the key it rests on and
 * "Delete" taking the last digit back. The remote starts on "1". The digits
 * go the moment there are four and the pad empties, so a new PIN's second
 * entry and a retry both start from nothing. Dots, never digits, show how far
 * the entry is — the whole room can see a television.
 *
 * While not [enabled] (a PIN is being checked) the keys still hold the
 * remote, as `TvTextRow` does, but type nothing.
 */
@Composable
internal fun TvPinPad(
    enabled: Boolean,
    onPin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var digits by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
            text = "●".repeat(digits.length) + "○".repeat(PIN_LENGTH - digits.length),
            style = TvTypeScale.title,
            modifier = Modifier.testTag(TvPinDotsTag).padding(bottom = Spacing.small),
        )
        Keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                row.forEach { key ->
                    if (key == null) {
                        Spacer(Modifier.size(KeySize))
                    } else {
                        TvPinKey(key, first.takeIf { key == "1" }) {
                            if (!enabled) return@TvPinKey
                            val next = if (key == DELETE) digits.dropLast(1) else digits + key
                            digits = if (next.length == PIN_LENGTH) "" else next
                            if (next.length == PIN_LENGTH) onPin(next)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPinKey(
    label: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .widthIn(min = KeySize)
                .height(KeySize)
                .testTag(tvPinKeyTag(label))
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        shape = TvFocus.surfaceShape(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(),
        glow = TvFocus.surfaceGlow(),
    ) {
        Text(
            text = label,
            style = if (label == DELETE) TvTypeScale.body else TvTypeScale.title,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.medium),
        )
    }
}
```

`ui-tv/src/main/kotlin/ui/tv/profile/TvPinPrompt.kt`

```kotlin
package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.PinPrompt
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvTextRow

/**
 * The PIN question as a whole screen, for the picker and Manage alike —
 * composed in place of what asked, as the add flow always was, so nothing
 * behind it competes for the remote. Back cancels.
 */
@Composable
internal fun TvPinPrompt(
    prompt: PinPrompt,
    onPin: (String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(prompt.heading, style = TvTypeScale.title)
        prompt.error?.let {
            Text(it, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small))
        }
        TvPinPad(enabled = !prompt.busy, onPin = onPin, modifier = Modifier.padding(top = Spacing.medium))
        TvTextRow(text = "Cancel", onClick = onCancel, modifier = Modifier.padding(top = Spacing.medium))
    }
}
```

- [ ] **Step 4: run, expect pass** — `./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.profile.TvPinPadStateTest' :ui-tv:compileDebugAndroidTestKotlin`. If `thePadAndCancelFitA540dpTelevision` fails, shrink `KeySize` to 52dp, never below 48dp (the remote's target on a 10-foot screen), and say so in the commit body.
- [ ] **Step 5: commit** — bump, then `git add android/ui-tv <manifests> docs/project-changelog.md && git commit -m "feat(android): a four-digit PIN pad the remote walks; release <next>"`.

### Task 5: Manage profiles on television

- [ ] **Step 1: failing test** — `ui-tv/src/test/kotlin/ui/tv/profile/TvManageProfilesStateTest.kt` (harness as Task 4, `w960dp-h540dp`; `actions` recording as Task 2; `press(node)` = `performSemanticsAction(OnClick)` + `waitForIdle()`):

```kotlin
    private val andre = Profile("a", "andre", admin = true, hasPin = true)
    private val bea = Profile("b", "Bea", hasPin = true)
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")

    @Test
    fun whoAreYouOffersTheGrownUps() {
        show { TvManageProfiles(ManageUiState.ChoosingActor(listOf(andre, bea)), actions) }
        press(compose.onNodeWithText("Bea"))
        assertEquals(listOf("actAs b"), calls)
    }

    @Test
    fun aParentsKidsShowTheirLimitAndAKidsRowOffersTheOther() {
        show { TvManageProfiles(ManageUiState.Managing(bea, emptyList(), listOf(tom), canAddGrownUp = false), actions) }
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        press(compose.onNodeWithText("Tom · FSK 12"))
        press(compose.onNode(hasText("FSK 6") and hasAnyAncestor(isDialog())))
        assertEquals(listOf("age t 6"), calls)
    }

    @Test
    fun theAdminRemovesAGrownUpAfterBeingToldItsKidsGoToo() {
        show { TvManageProfiles(ManageUiState.Managing(andre, listOf(bea), emptyList(), canAddGrownUp = true), actions) }
        press(compose.onNodeWithText("Bea"))
        press(compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())))
        compose.onNodeWithText("Remove Bea, their kids, and everything they have watched?").assertExists()
        press(compose.onNodeWithTag(TvConfirmDialogConfirmTag))
        assertEquals(listOf("remove b"), calls)
    }

    @Test
    fun addingAKidAsksItsNameThenItsLimit() {
        show { TvManageProfiles(ManageUiState.Managing(bea, emptyList(), emptyList(), canAddGrownUp = false), actions) }
        press(compose.onNodeWithText("Add a kid"))
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Lina")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        press(compose.onNodeWithTag(tvKidsLimitTag(6)))
        assertEquals(listOf("addKid Lina 6"), calls)
    }

    @Test
    fun addingAGrownUpHandsTheNameOnForItsPin() {
        show { TvManageProfiles(ManageUiState.Managing(andre, emptyList(), emptyList(), canAddGrownUp = true), actions) }
        press(compose.onNodeWithText("Add a grown-up"))
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Cleo")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        assertEquals(listOf("addGrownUp Cleo"), calls)
    }
```

- [ ] **Step 2: run, expect failure** — `./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.profile.TvManageProfilesStateTest'` → `Unresolved reference 'TvManageProfiles'`, `'tvKidsLimitTag'`.

- [ ] **Step 3: implement.** `ui-tv/src/main/kotlin/ui/tv/profile/TvManageProfiles.kt`:

```kotlin
package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.DONE
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageUiState
import catalog.profile.WHO_ARE_YOU
import catalog.profile.YOUR_PIN
import catalog.profile.managingAs
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Profile
import ui.profile.ManageActions
import ui.tv.TvTextRow
import ui.tv.player.TvSettingsHeading

/** What covers or replaces the list while it is open. */
private sealed interface TvManageStep {
    data object List : TvManageStep

    data class Adding(val kid: Boolean) : TvManageStep

    data class KidActions(val kid: Profile) : TvManageStep

    data class GrownUpActions(val grownUp: Profile) : TvManageStep

    data class Removing(val profile: Profile) : TvManageStep
}

/**
 * Manage profiles on television — the phone's screen as a column the remote
 * walks: who you are; then the admin's grown-ups, every grown-up's own kids
 * ("Name · FSK N"), its PIN, Done. A row opens its few choices in a dialog
 * rather than laying buttons along it, so Down always means the next person.
 * Adding composes the name question in place, as the picker's add flow did.
 * Back is Done.
 */
@Composable
internal fun TvManageProfiles(
    state: ManageUiState,
    actions: ManageActions,
) {
    BackHandler(onBack = actions.onClose)
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> TvWhoAreYou(state.grownUps, actions)
        is ManageUiState.Managing -> TvManaging(state, actions)
    }
}

@Composable
private fun TvWhoAreYou(
    grownUps: List<Profile>,
    actions: ManageActions,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    TvManagePage(WHO_ARE_YOU) {
        grownUps.forEachIndexed { index, profile ->
            TvTextRow(profile.name, onClick = { actions.onActAs(profile.id) }, focusRequester = first.takeIf { index == 0 })
        }
        TvTextRow(DONE, onClick = actions.onClose, focusRequester = first.takeIf { grownUps.isEmpty() }, modifier = Modifier.padding(top = Spacing.medium))
    }
}

@Composable
private fun TvManaging(
    state: ManageUiState.Managing,
    actions: ManageActions,
) {
    var step by remember { mutableStateOf<TvManageStep>(TvManageStep.List) }
    (step as? TvManageStep.Adding)?.let { adding ->
        TvAddProfileFlow(
            heading = if (adding.kid) ADD_A_KID else ADD_A_GROWN_UP,
            askLimit = adding.kid,
            onAdd = { name, age ->
                step = TvManageStep.List
                if (age != null) actions.onAddKid(name, age) else actions.onAddGrownUp(name)
            },
            onCancel = { step = TvManageStep.List },
        )
        return
    }
    val first = remember { FocusRequester() }
    // Again whenever someone leaves the list: the row the remote was on may be the one removed.
    LaunchedEffect(state.grownUps.size, state.kids.size) { first.requestFocus() }
    val admin = state.canAddGrownUp
    TvManagePage(managingAs(state.actor.name)) {
        state.notice?.let { Text(it, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error) }
        if (admin) {
            TvSettingsHeading(GROWN_UPS)
            state.grownUps.forEachIndexed { index, grownUp ->
                TvTextRow(grownUp.name, onClick = { step = TvManageStep.GrownUpActions(grownUp) }, focusRequester = first.takeIf { index == 0 })
            }
            TvTextRow(ADD_A_GROWN_UP, onClick = { step = TvManageStep.Adding(kid = false) }, focusRequester = first.takeIf { state.grownUps.isEmpty() })
        }
        TvSettingsHeading(KIDS_SECTION)
        state.kids.forEachIndexed { index, kid ->
            TvTextRow("${kid.name} · FSK ${kid.kidsLimit}", onClick = { step = TvManageStep.KidActions(kid) }, focusRequester = first.takeIf { !admin && index == 0 })
        }
        TvTextRow(ADD_A_KID, onClick = { step = TvManageStep.Adding(kid = true) }, focusRequester = first.takeIf { !admin && state.kids.isEmpty() })
        TvSettingsHeading(YOUR_PIN)
        TvTextRow(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(state.actor.id) })
        TvTextRow(DONE, onClick = actions.onClose, modifier = Modifier.padding(top = Spacing.medium))
    }
    when (val current = step) {
        is TvManageStep.KidActions ->
            TvKidActions(
                current.kid,
                onAge = { actions.onSetKidsAge(current.kid.id, it) },
                onRemove = { step = TvManageStep.Removing(current.kid) },
                onDismiss = { step = TvManageStep.List },
            )
        is TvManageStep.GrownUpActions ->
            TvGrownUpActions(
                current.grownUp,
                onResetPin = {
                    step = TvManageStep.List
                    actions.onChangePin(current.grownUp.id)
                },
                onRemove = { step = TvManageStep.Removing(current.grownUp) },
                onDismiss = { step = TvManageStep.List },
            )
        is TvManageStep.Removing ->
            TvRemoveProfileDialog(current.profile, onRemove = { actions.onRemove(current.profile.id) }, onDismiss = { step = TvManageStep.List })
        else -> Unit
    }
}

@Composable
private fun TvManagePage(
    subheading: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Text(MANAGE_PROFILES, style = TvTypeScale.title)
        Text(subheading, style = TvTypeScale.body, modifier = Modifier.padding(bottom = Spacing.small))
        content()
    }
}
```

`ui-tv/src/main/kotlin/ui/tv/profile/TvManageDialogs.kt`:

```kotlin
package ui.tv.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import designsystem.Spacing
import designsystem.TvTypeScale
import model.KIDS_LIMITS
import model.Profile
import ui.tv.TvTextRow
import ui.tv.player.TvChoiceRow
import ui.tv.setup.TvDialog

/** A kid's choices for its parent: its limit — the current one marked, the remote starting on it — or removal, which asks again. */
@Composable
internal fun TvKidActions(
    kid: Profile,
    onAge: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = remember { FocusRequester() }
    TvDialog(
        title = kid.name,
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = current,
        content = {
            Column(Modifier.padding(top = Spacing.medium)) {
                KIDS_LIMITS.forEach { age ->
                    TvChoiceRow(
                        label = "FSK $age",
                        selected = kid.kidsLimit == age,
                        onClick = {
                            onAge(age)
                            onDismiss()
                        },
                        focusRequester = current.takeIf { kid.kidsLimit == age },
                    )
                }
                TvTextRow(REMOVE, onClick = onRemove, modifier = Modifier.padding(top = Spacing.small))
            }
        },
    ) { Button(onClick = onDismiss) { Text("Cancel", style = TvTypeScale.body) } }
}

/** Another grown-up, for the admin: a new PIN for them, or removal with their kids. */
@Composable
internal fun TvGrownUpActions(
    grownUp: Profile,
    onResetPin: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    TvDialog(
        title = grownUp.name,
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = first,
        content = {
            Column(Modifier.padding(top = Spacing.medium)) {
                TvTextRow(RESET_PIN, onClick = onResetPin, focusRequester = first)
                TvTextRow(REMOVE, onClick = onRemove, modifier = Modifier.padding(top = Spacing.small))
            }
        },
    ) { Button(onClick = onDismiss) { Text("Cancel", style = TvTypeScale.body) } }
}
```

`ui-tv/src/main/kotlin/ui/tv/profile/TvRemoveProfileDialog.kt` — reworked whole:

```kotlin
package ui.tv.profile

import androidx.compose.runtime.Composable
import catalog.profile.REMOVE
import catalog.profile.removeQuestion
import model.Profile
import ui.tv.setup.TvConfirmDialog

/**
 * Asked before a removal, in the web's words — a grown-up's kids go with it,
 * and the question says so. Cancel takes the remote first and Back cancels:
 * this is the press that costs something.
 */
@Composable
internal fun TvRemoveProfileDialog(
    profile: Profile,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvConfirmDialog(
        title = removeQuestion(profile),
        body = "",
        confirmLabel = REMOVE,
        confirm = {
            onRemove()
            onDismiss()
        },
        cancel = onDismiss,
    )
}
```

`ui-tv/src/main/kotlin/ui/tv/profile/TvAddProfileFlow.kt` — reworked whole:

```kotlin
package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.KIDS_LIMITS
import ui.tv.TvTextRow
import ui.tv.setup.TvTextQuestion

/** A limit row's tag, for tests. */
internal fun tvKidsLimitTag(age: Int) = "tv-kids-limit-$age"

private enum class AddStep { NAME, LIMIT }

/**
 * Adding a profile on television: the name first, on a screen of its own
 * (a remote has nowhere to land on a second field once the first holds it),
 * then, for a kid, its limit — FSK 6 or FSK 12, the remote starting on 6, the
 * web's own first choice. A grown-up goes on after its name: its first PIN is
 * asked next, twice, on the pad — in Manage, and for the first profile on the
 * picker. Back from the limit returns to the name with what was typed; Back
 * from the name leaves.
 */
@Composable
internal fun TvAddProfileFlow(
    heading: String,
    askLimit: Boolean,
    onAdd: (name: String, age: Int?) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf(AddStep.NAME) }
    var name by remember { mutableStateOf("") }
    when (step) {
        AddStep.NAME -> {
            BackHandler(onBack = onCancel)
            TvTextQuestion(
                heading = heading,
                explanation = null,
                label = "Name",
                value = name,
                onValue = { name = it },
                onSubmit = {
                    if (name.isBlank()) return@TvTextQuestion
                    if (askLimit) step = AddStep.LIMIT else onAdd(name.trim(), null)
                },
            )
        }
        AddStep.LIMIT -> {
            BackHandler { step = AddStep.NAME }
            TvKidsLimitChoice(name.trim(), onChoose = { age -> onAdd(name.trim(), age) })
        }
    }
}

@Composable
private fun TvKidsLimitChoice(
    name: String,
    onChoose: (Int) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        Text(name, style = TvTypeScale.title)
        KIDS_LIMITS.forEachIndexed { index, age ->
            TvTextRow(
                text = "FSK $age",
                onClick = { onChoose(age) },
                focusRequester = first.takeIf { index == 0 },
                modifier = Modifier.testTag(tvKidsLimitTag(age)).padding(top = Spacing.medium),
            )
        }
    }
}
```

- [ ] **Step 4: run, expect pass** — command from Step 2.
- [ ] **Step 5: commit** — bump, then `git add android/ui-tv <manifests> docs/project-changelog.md && git commit -m "feat(android): Manage profiles on the television, a row per person; release <next>"`.

### Task 6: The television's picker, gate and whole-app walks

- [ ] **Step 1: failing tests.** `TvProfilePickerStateTest.kt` — replace `aKidsProfilesTileCarriesTheKidsLabelAndAPlainOneDoesNot` (`:75-84`) and add (show gains the new callbacks):

```kotlin
    @Test
    fun aKidsTileNamesItsOwnLimit() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true), Profile("m", "Mia", kids = true, kidsAge = 6), Profile("o", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Kids · FSK 6").assertExists()
        compose.onNodeWithText("Kids · FSK 12").assertExists()
    }

    @Test
    fun aDeviceWithNoGrownUpOffersTheFirstProfileAndNoManage() {
        show(ProfileUiState.Picking(listOf(Profile("o", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithTag(TvFirstProfileTag).assertExists()
        compose.onNodeWithText("Look again").assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertDoesNotExist()
    }

    @Test
    fun grownUpsWithNoAdminAreAskedWhoRunsTheHousehold() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre"), Profile("o", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Who runs this household?").assertExists()
        compose.onNodeWithTag(tvClaimTag("a")).assertExists()
        compose.onNodeWithTag(tvClaimTag("o")).assertDoesNotExist()
        compose.onNodeWithTag(TvManageProfilesTag).assertExists()
    }

    @Test
    fun theNoteSaysAPinIsNotALogin() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true)), canStay = false))
        compose.onNodeWithText("it is not a login", substring = true).assertExists()
        compose.onNodeWithText("Who runs this household?").assertDoesNotExist()
    }
```

androidTest `TvProfilePickerTest.kt` — add:

```kotlin
    @Test
    fun theFirstProfileRowTakesTheRemoteOnADeviceWithNoGrownUp() {
        compose.setContent {
            TvTheme {
                TvProfilePicker(
                    state = ProfileUiState.Picking(profiles = listOf(Profile(id = "k", name = "TV kids", kids = true)), canStay = false),
                    onChoose = {},
                    onStay = {},
                    onRetry = {},
                )
            }
        }
        compose.onNodeWithTag(TvFirstProfileTag).assertIsFocused()
    }
```

Whole-app walks, `ui-tv/src/test/kotlin/ui/tv/TvManageProfilesFlowTest.kt` (harness, Hilt stub, `press` and `launch` as `TvHousekeepingTest.kt:43-67,182-196,214-217`, with `launch(viewer: FakeCore)` building `TvAppFixture(TvSetupStage.READY, viewerCore = viewer)` and waiting for "Who's watching?"):

```kotlin
    private fun household(admin: Boolean = true) =
        FakeCore().apply {
            profiles = listOf(CoreProfile("a", "andre", admin = admin), CoreProfile("b", "Bo"), CoreProfile("c", "Cy", kids = true, kidsAge = 6u, parentId = "b"))
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
        }

    private fun typePin(pin: String) = pin.forEach { press(compose.onNodeWithTag(tvPinKeyTag(it.toString()))) }

    @Test
    fun aGrownUpsTileOpensOnlyOnItsOwnPin() {
        launch(household())
        press(compose.onNodeWithTag(TvProfilePickerFirstTileTag))
        typePin("0000")
        compose.onNodeWithText("Wrong PIN.").assertExists()
        typePin("1234")
        compose.onAllNodesWithText("Who's watching?").assertCountEquals(0)
    }

    @Test
    fun theAdminRemovesAGrownUpAndItsKidThroughManage() {
        launch(household())
        press(compose.onNodeWithTag(TvManageProfilesTag))
        press(compose.onNodeWithText("andre"))
        typePin("1234")
        compose.onNodeWithText("As andre").assertExists()
        press(compose.onNodeWithText("Bo"))
        press(compose.onNode(hasText("Remove") and hasClickAction() and hasAnyAncestor(isDialog())))
        compose.onNodeWithText("Remove Bo, their kids, and everything they have watched?").assertExists()
        press(compose.onNodeWithTag(TvConfirmDialogConfirmTag))
        compose.onAllNodesWithText("Bo").assertCountEquals(0)
        press(compose.onNodeWithText("Done"))
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onAllNodesWithText("Cy").assertCountEquals(0)
    }

    @Test
    fun aHouseholdWithNoAdminIsAskedWhoRunsItOnce() {
        launch(household(admin = false))
        press(compose.onNodeWithTag(tvClaimTag("a")))
        typePin("1234")
        compose.onAllNodesWithText("Who runs this household?").assertCountEquals(0)
    }

    @Test
    fun aDeviceWithNoGrownUpMakesTheFirstProfileWithAPinTypedTwice() {
        launch(FakeCore().apply { profiles = listOf(CoreProfile("k", "TV kids", kids = true)) })
        press(compose.onNodeWithTag(TvFirstProfileTag))
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Ann")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        typePin("2468")
        compose.onNodeWithText("The same PIN again").assertExists()
        typePin("2468")
        compose.onNode(hasText("Ann") and hasClickAction()).assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertExists()
    }
```

- [ ] **Step 2: run, expect failure** — `./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.profile.TvProfilePickerStateTest' --tests 'ui.tv.TvManageProfilesFlowTest'` → `Unresolved reference 'TvFirstProfileTag'`, `'tvClaimTag'`, `No parameter with name 'viewerCore'`.

- [ ] **Step 3: implement.** `ui-tv/src/main/kotlin/ui/tv/profile/TvPickerQuestions.kt`:

```kotlin
package ui.tv.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import catalog.profile.FIRST_PROFILE
import catalog.profile.LOOK_AGAIN
import catalog.profile.WHO_RUNS_THIS
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Profile
import ui.tv.TvTextRow

/** The first-profile row's tag. */
internal const val TvFirstProfileTag = "tv-profile-picker-first-profile"

/** A "who runs this household" answer's tag. */
internal fun tvClaimTag(id: String) = "tv-profile-picker-claim-$id"

/**
 * The first grown-up on a device with none, and "Look again" for a device
 * whose first sync has not come in: a household that already has profiles
 * brings them on the next round, and a first one made meanwhile would only
 * lose the admin role to that household's older claim.
 */
@Composable
internal fun TvFirstProfileRows(
    onStart: () -> Unit,
    onLookAgain: () -> Unit,
    focusRequester: FocusRequester?,
) {
    TvTextRow(
        text = FIRST_PROFILE,
        onClick = onStart,
        focusRequester = focusRequester,
        modifier = Modifier.testTag(TvFirstProfileTag).padding(horizontal = Overscan.horizontal).padding(top = Spacing.medium),
    )
    TvTextRow(text = LOOK_AGAIN, onClick = onLookAgain, modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.small))
}

/** Asked above the tiles while nobody runs the household; only grown-ups are offered, each a PIN away. */
@Composable
internal fun TvAdminQuestion(
    grownUps: List<Profile>,
    onClaim: (String) -> Unit,
) {
    Text(WHO_RUNS_THIS, style = TvTypeScale.body, modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.medium))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.large), modifier = Modifier.padding(top = Spacing.small)) {
        grownUps.forEach { profile -> TvTextRow(profile.name, onClick = { onClaim(profile.id) }, modifier = Modifier.testTag(tvClaimTag(profile.id))) }
    }
}
```

`TvProfilePicker.kt` (after phase 06's cut):
- signature → `fun TvProfilePicker(state: ProfileUiState, onChoose: (String) -> Unit, onStay: () -> Unit, onRetry: () -> Unit, onClaim: (String) -> Unit = {}, onCreateFirst: (String) -> Unit = {}, onManage: () -> Unit = {})`, passed through to `TvPickerBody`;
- `NOTE` (`:34-36`) → `PICKER_NOTE`; add `internal const val TvManageProfilesTag = "tv-profile-picker-manage"`;
- the body opens with the first-profile name question composed in place, as the add flow was:

```kotlin
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TvAddProfileFlow(
            heading = FIRST_PROFILE,
            askLimit = false,
            onAdd = { name, _ ->
                naming = false
                onCreateFirst(name)
            },
            onCancel = { naming = false },
        )
        return
    }
```
- what takes the remote first:

```kotlin
    val error = state.error
    // A failed load is answered by Try again; a device with no grown-up by the
    // first profile; otherwise the first tile. Exactly one holds the requester.
    val tryAgainFirst = error != null && state.profiles.isEmpty()
    val firstProfileFirst = error == null && state.needsFirstProfile
    LaunchedEffect(tryAgainFirst, firstProfileFirst) { firstFocusRequester.requestFocus() }
```
  the Try again row takes `firstFocusRequester.takeIf { tryAgainFirst }`; the tile at index 0 takes it when `!tryAgainFirst && !firstProfileFirst`;
- after the error block: `if (firstProfileFirst) TvFirstProfileRows(onStart = { naming = true }, onLookAgain = onRetry, focusRequester = firstFocusRequester)` and `if (error == null && state.needsAdmin) TvAdminQuestion(state.grownUps, onClaim)`;
- after the note: `if (state.grownUps.isNotEmpty()) TvTextRow(MANAGE_PROFILES, onClick = onManage, modifier = Modifier.testTag(TvManageProfilesTag).padding(horizontal = Overscan.horizontal).padding(top = Spacing.small))`.

`TvProfileTiles.kt` — `:81-90` becomes `profile.kidsTag?.let { Text(it, style = TvTypeScale.body, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.extraSmall)) }`; delete `NEW_PROFILE` (`:34`) and `TvAddTile` (`:94-108`); the fixed-height comment (`:59-62`) says "a kids tile carries one more line (its limit)".

`TvProfileGate.kt` — whole body:

```kotlin
/**
 * The television counterpart to `ui.profile.ProfileGate`: the same view
 * models decide the same things. One screen at a time — whichever PIN is
 * asked replaces what asked for it, and Manage replaces the picker — so
 * nothing behind competes for the remote. Manage cannot be reached once a
 * profile is chosen.
 */
@Composable
internal fun TvProfileGate(content: @Composable (TvChosenProfile) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val managing by manage.state.collectAsStateWithLifecycle()
    val pickPin by viewModel.pin.collectAsStateWithLifecycle()
    val managePin by manage.pin.collectAsStateWithLifecycle()
    val chosen = state as? ProfileUiState.Chosen
    val inManage = managing != ManageUiState.Closed
    val asking = if (inManage) managePin else pickPin

    when {
        chosen != null -> content(TvChosenProfile(name = chosen.profile.name, onChoose = viewModel::reopen))
        inManage && asking != null -> TvPinPrompt(asking, onPin = manage::enterPin, onCancel = manage::cancelPin)
        inManage -> TvManageProfiles(managing, remember(manage) { manage.actions() })
        asking != null -> TvPinPrompt(asking, onPin = viewModel::enterPin, onCancel = viewModel::cancelPin)
        else ->
            TvProfilePicker(
                state = state,
                onChoose = viewModel::pick,
                onStay = viewModel::stay,
                onRetry = viewModel::retry,
                onClaim = viewModel::claim,
                onCreateFirst = viewModel::createFirst,
                onManage = manage::open,
            )
    }
}
```
(imports `androidx.compose.runtime.remember`, `catalog.profile.ManageProfilesViewModel`, `catalog.profile.ManageUiState`, `ui.profile.actions`.)

`TvAppFixture.kt` — constructor gains `viewerCore: FakeCore? = null` (KDoc: "a `FakeCore` behind the viewer instead of the hand fake, for walks that need the core's profile rules — PINs, roles, the wait"); `:155-156` becomes

```kotlin
        val viewer =
            viewerCore?.let { DefaultWatchStateRepository(FakeCoreProvider(it), dispatcher) }
                ?: FakeWatchStateRepository(profiles, chosenProfileId, watch)
        profile = ProfileViewModel(viewer, NoopWatchSync)
```
and the model map (`:236-260`) gains `ManageProfilesViewModel::class.java to ManageProfilesViewModel(viewer, NoopWatchSync),`.

- [ ] **Step 4: run, expect pass** — `./gradlew :ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin` (whole module: `TvAppTest`, `TvHousekeepingTest` still pass).
- [ ] **Step 5: commit** — bump, then `git add android/ui-tv <manifests> docs/project-changelog.md && git commit -m "feat(android): the television's picker asks a PIN on the pad, makes the first profile and opens Manage; release <next>"`.

### Task 7: The Kids choice in the player, and `toggleKids` retired

- [ ] **Step 1: failing tests.** Phone, `ui-mobile/src/test/kotlin/ui/player/PlayerMarksKidsChoiceTest.kt` (harness as Task 1):

```kotlin
    private fun marks(verdict: KidsVerdict = KidsVerdict.UNRATED, kidsMark: Int? = null) =
        PlayerMarksState(watchlisted = false, kidsMark = kidsMark, lists = emptyList(), memberOf = emptySet(), kidsVerdict = verdict, ageLabel = if (verdict == KidsVerdict.UNRATED) null else "FSK 16")

    private fun actions(onKidsMark: (Int?) -> Unit) = PlayerMarksActions(onToggleWatchlist = {}, onKidsMark = onKidsMark, onSetInList = { _, _ -> }, onCreateList = {})

    @Test
    fun anUnratedTitleOffersTheThreeChoices() {
        var chosen: Int? = -1
        show { PlayerMarks(marks(), actions { chosen = it }) }
        compose.onNodeWithText("Not for kids").performClick()
        compose.onNodeWithText("From 12").assertExists()
        compose.onNodeWithText("From 6").performClick()
        assertEquals(6, chosen)
    }

    @Test
    fun aRatedTitleShowsItsVerdictAndOffersNothing() {
        show { PlayerMarks(marks(KidsVerdict.UNSAFE), actions {}) }
        compose.onNodeWithText("FSK 16 · not for kids").assertIsNotEnabled()
    }
```

TV, `TvPlayerMarksTest.kt` — add:

```kotlin
    @Test
    fun theKidsMarkOpensTheChoiceAndFromSixMarksFromSix() {
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithText("Not for kids").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(hasText("Not for kids") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        coVerify { fixture.repository.setKids("set-one", 6) }
    }
```

Feature: `PlayerMarksTest.kt` — delete `toggleKidsMarksAndUnmarksTheOpenTitle` (`:90-108`; `theKidsChoiceMarks…` from phase 06 covers it) and switch `vm.toggleKids()` (`:126,166`) to `vm.setKidsMark(12)`; `PlayerActionFailureTest.kt:87` and `PlayerActionNoticeTest.kt:44,165` → `vm.setKidsMark(12)`.

- [ ] **Step 2: run, expect failure** — `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.player.PlayerMarksKidsChoiceTest' :ui-tv:testDebugUnitTest --tests 'ui.tv.player.TvPlayerMarksTest'` → `No parameter with name 'onKidsMark'`; the TV case fails at the dialog (no dialog opens: the button still toggles).

- [ ] **Step 3: implement.** Phone `ui-mobile/src/main/kotlin/ui/player/PlayerMarks.kt` — `PlayerMarksActions.onToggleKids: () -> Unit` (`:69`) → `onKidsMark: (age: Int?) -> Unit`; the Kids button (`:43-50`) → `if (marks.canMarkKids) KidsMark(marks, onChoose = actions.onKidsMark)`; add:

```kotlin
/**
 * The Kids control: on an unrated title the web's select — "Not for kids",
 * "From 6", "From 12" — as a menu under the button; on a rated title its
 * verdict, dimmed, with nothing to press.
 */
@Composable
private fun KidsMark(
    marks: PlayerMarksState,
    onChoose: (Int?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        MarkButton(label = kidsLabel(marks), onClick = { open = true }, enabled = marks.kidsVerdict == KidsVerdict.UNRATED)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            KIDS_CHOICES.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        open = false
                        onChoose(choice.age)
                    },
                )
            }
        }
    }
}
```
`ui-mobile/.../player/PlayerScreen.kt` — `:38` `import player.toggleKids` → `import player.setKidsMark`; `:155` `onToggleKids = viewModel::toggleKids` → `onKidsMark = viewModel::setKidsMark`.

TV `ui-tv/src/main/kotlin/ui/tv/player/TvKidsChoiceDialog.kt`:

```kotlin
package ui.tv.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale
import player.KIDS_CHOICES
import ui.tv.setup.TvDialog

/**
 * The Kids choice over the film — the phone's menu and the web's select as a
 * television dialog: the current answer marked and holding the remote.
 * Choosing closes it; Back leaves the mark as it was.
 */
@Composable
internal fun TvKidsChoiceDialog(
    current: Int?,
    onChoose: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember { FocusRequester() }
    TvDialog(
        title = "For kids",
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = chosen,
        content = {
            Column(Modifier.padding(top = Spacing.medium)) {
                KIDS_CHOICES.forEach { choice ->
                    TvChoiceRow(
                        label = choice.label,
                        selected = choice.age == current,
                        onClick = {
                            onChoose(choice.age)
                            onDismiss()
                        },
                        focusRequester = chosen.takeIf { choice.age == current },
                    )
                }
            }
        },
    ) { Button(onClick = onDismiss) { Text("Cancel", style = TvTypeScale.body) } }
}
```

`TvMarksRail.kt` — `TvMarksActions.onToggleKids` (`:170-173`) → `onKids: () -> Unit` ("opens the choice; the dialog lives with the screen, as Add to list's does"); the Kids `MarkButton` (`:151-156`) calls `actions.onKids`. `TvPlayerControlsBridge.kt` — drop `import player.toggleKids` (`:14`); `TvControlsActions` gains `val onKids: () -> Unit`; `markActions` passes `onKids = actions.onKids`; below `TvAddToListOverPlayer` add:

```kotlin
/** The Kids choice over the film while a title is open; its window's keys go to the player first, as the list dialog's do. */
@Composable
internal fun TvKidsChoiceOverPlayer(
    marks: PlayerMarksState?,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    keys: (KeyEvent) -> Boolean,
) {
    val open = marks ?: return
    CompositionLocalProvider(LocalTvDialogKeys provides keys) {
        TvKidsChoiceDialog(current = open.kidsMark, onChoose = viewModel::setKidsMark, onDismiss = onDismiss)
    }
}
```
`TvPlayerScreen.kt` — beside `choosingList` (`:108`): `var choosingKids by rememberSaveable { mutableStateOf(false) }`; include it wherever `choosingList` holds the controls or closes on a new title (`:110` `closeList = { choosingList = false; choosingKids = false }`, `:111` `held = choosingList || choosingKids || …`, `:134` `busy = { choosingList || choosingKids || onSeekBar }`); `TvControlsActions(…, onKids = { choosingKids = true }, …)` at `:183-191`; hoist the list dialog's `keys` lambda (`:202-203`) into `val dialogKeys: (KeyEvent) -> Boolean = { event -> remote.onKey(event, player, controlsShowing = true, onSeekBar = false, canControl = controlsMayShow(state), panelOpen = true) }` and pass it to both; after the list block:

```kotlin
            if (choosingKids) {
                TvKidsChoiceOverPlayer(marks = marks, viewModel = viewModel, onDismiss = { choosingKids = false }, keys = dialogKeys)
            }
```

Feature (retiring the toggle): delete `toggleKids` from `PlayerMarksController.kt` and `PlayerViewModelDelegates.kt:48`.

- [ ] **Step 4: run, expect pass** — `./gradlew :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest` and `rg -n "toggleKids" android --glob '!**/build/**'` → no hits.
- [ ] **Step 5: commit** — bump, then `git add android/feature/player android/ui-mobile android/ui-tv <manifests> docs/project-changelog.md && git commit -m "feat(android): the player's Kids control chooses not for kids, from 6 or from 12; release <next>"`.

### Task 8: Checks, then the tablet by eye

- [ ] **Step 1:** `./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin` → green.
- [ ] **Step 2: D-pad tests on a real window manager, on the tablet** (composables on plain state, no household data): `ANDROID_SERIAL=caad49da ./gradlew :ui-tv:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=ui.tv.profile` → `TvPinPadTest`, `TvProfilePickerTest` pass.
- [ ] **Step 3: install on the tablet only.** Rebuild the native core first if the Rust changed since the last `.so` (phase 05's step — a stale `.so` builds and dies at launch). `ANDROID_SERIAL=caad49da ./gradlew :app:installDebug` — never a bare install (a TV emulator and the box 192.168.0.35:5555 may be attached).
- [ ] **Step 4: read-only walk** (screenshots `adb -s caad49da exec-out screencap -p > <scratchpad>/profiles-<step>.png`, none showing a PIN typed in clear or the Telegram account):
  1. Reopen the picker from the name in the bar. Tiles say "Kids · FSK 12" for TV kids; the note says "it is not a login"; the start state matches the household (after andre's claim on the web: tiles + Manage profiles; before: "Who runs this household?" — **do not answer it here**; the earliest claim wins for good).
  2. On a grown-up **that already has a PIN**, type one wrong PIN: "Wrong PIN." — then Cancel. (The count is this device's alone and resets on restart.) On a grown-up with no PIN, the dialog asks to choose one — **Cancel**: a PIN set here syncs to every device.
  3. Open Manage profiles → "Who are you?" → Done.
  4. On the **test** profile, open an unrated title's player; the Kids control shows "Not for kids"; open its menu, see the three choices, **dismiss without choosing** (a mark is the household's and syncs).
- [ ] **Step 5: anything that changes the household — only with the user's go-ahead**, and then on test profiles only: set a PIN on `test`, add a kid under it at FSK 6, change it to 12, remove it (another device that synced it meanwhile brings it back — spec §8). The admin claim is never made from here: andre claims on the web (phase 08).
- [ ] **Step 6: the television — only if the user agrees**, on the TV emulator or the box: `ANDROID_SERIAL=<emulator or 192.168.0.35:5555> ./gradlew :app:installDebug` (the box takes the benchmark build — memory `tv-box-gets-the-benchmark-build`), then Step 4's walk with the remote: the pad reached and typed by D-pad alone, Manage rows walked, the Kids dialog opened and dismissed.
- [ ] **Step 7:** note what was seen, and anything off-token, in `plans/reports/` for phase 08's closing report.

## Todo list

- [ ] Task 1 — phone PIN dialog (commit)
- [ ] Task 2 — phone Manage screen, dialogs, `ManageActions` (commit)
- [ ] Task 3 — phone picker + gate (commit)
- [ ] Task 4 — TV PIN pad + prompt (commit)
- [ ] Task 5 — TV Manage (commit)
- [ ] Task 6 — TV picker + gate + app walks (commit)
- [ ] Task 7 — Kids choice on both; `toggleKids` gone (commit)
- [ ] Task 8 — checks; tablet read-only walk; household-changing steps and TV only with the user's agreement

## Success criteria

- Every unit-test module and lint green; `TvPinPadTest` and `TvProfilePickerTest` pass on the tablet.
- `TvManageProfilesFlowTest` walks the whole app: a wrong then right PIN opens a grown-up; the admin removes a grown-up and its kid; the no-admin question goes after a claim; a first profile is made with a PIN typed twice.
- The pad works by D-pad alone and fits 960×540 dp with Cancel on screen.
- No hard-coded "12", "KIDS" or picker note left in ui-mobile/ui-tv (`rg -n '"KIDS"|FSK 12 and under|They are not a login' android/ui-*` → none).
- Tablet walk seen and recorded without changing household data.

## Risk assessment

| Risk | L×I | Mitigation |
|---|---|---|
| A tester answers "Who runs this household?" or sets a PIN on the real household | M×H | Task 8 is read-only by default, household-changing steps need the user's go-ahead, admin claim never from a device |
| The pad does not fit a 540dp television | M×M | 56dp keys + the bounds test; 52dp floor noted |
| Focus lost after a removal on TV (removed row held it) | M×M | `LaunchedEffect(sizes)` re-requests the first row; flow test removes a row and presses on |
| Fixtures miss `ManageProfilesViewModel` → whole-app tests throw | H×L | Task 3/6 add it to `LibraryFlowFixture` and `TvAppFixture` in the same commit as the gates |
| TV Kids dialog closes with the fading controls | L×M | Lives at screen level like Add to list; `choosingKids` holds the controls |
| Parity drift in words | L×M | Every string comes from `catalog.profile`/`player`, pinned by `ProfileWordsTest`/`PlayerKidsLabelTest` to the web's |

Rollback: each task is its own commit; revert newest-first. Tasks 1, 2, 4, 5 add unused components and revert freely; 3 and 6 carry the gates and fixtures together.

## Security considerations

- The PIN field is masked and `NumberPassword` (no suggestions learnt); the TV shows dots, never digits; both empty themselves after four digits.
- Manage is reachable only from the picker; the held PIN is dropped on Done/Back (`close`).
- The note is honest (spec §1): the PIN keeps a child out, it is not a login.
- Screenshots never show a PIN being typed or the Telegram account.

## Next steps

Phase 08: whole-project checks, both surfaces by eye (andre claims admin on the web first), docs. Follow-ups listed in the report: an Android "Who is watching" settings panel (web has one), remote digit keys on the pad.
