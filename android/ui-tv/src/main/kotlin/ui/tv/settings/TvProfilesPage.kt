package ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.EVERYONE_HERE
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ProfileViewModel
import catalog.profile.SWITCH_PROFILE
import catalog.profile.WHO_IS_WATCHING
import catalog.profile.everyoneHere
import designsystem.Spacing
import model.HouseholdWords
import model.Profile
import setup.ProfileSettingsViewModel
import setup.ProfileSubtitleChoices
import setup.profileStatus
import ui.common.settings.ProfilesVisit
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine
import ui.tv.profile.TvManageScreen

/**
 * The one Profiles page, television-side — the web's (`settings-profiles.js`)
 * and the phone's `ProfilesPage`: You — who is watching, the way to switch,
 * their subtitle language — then everyone in the household, then Manage
 * profiles, then — while there is more than one household — which one this
 * device shows, then the owner's Households. A kid sees only You; with nobody [chosen], which is how the
 * picker's Manage profiles opens it, there is no You. Seeing the list needs no
 * PIN; changing anything asks who you are and that grown-up's PIN.
 *
 * Where the phone draws Manage inline under the list, the television opens it
 * as a panel of its own ([TvProfilesPanel]) from the "Manage profiles" row,
 * Switch household (`TvJoinHousehold`) from the This device row, and the
 * owner's Households (`TvHouseholdsPanel`) from the Owner row: their
 * PIN pads and name questions take the whole screen here wherever they open,
 * and one screen at a time keeps nothing behind them competing for the
 * remote. [onOpen] opens the panel a row names.
 *
 * [entryFocusRequester] is the page's first stop — who is watching, or with
 * nobody chosen the list — drawn whatever has loaded, so the page always has
 * somewhere to be entered. [returningFrom] lands on the row that opened that
 * panel instead: back from it, or over the picker, whose own Manage profiles
 * button opened the page.
 */
@Composable
internal fun TvProfilesPage(
    chosen: Profile?,
    focusInContent: Boolean,
    returningFrom: TvSettingsPanel?,
    entryFocusRequester: FocusRequester,
    onOpen: (TvSettingsPanel) -> Unit,
) {
    val rows = remember { listOf(TvSettingsPanel.Profiles, TvSettingsPanel.Join, TvSettingsPanel.Households).associateWith { FocusRequester() } }
    val list = remember { FocusRequester() }
    // Keyed on focusInContent alone, as Telegram's is: the hub forgets the
    // panel it came back from right after, which must not land a second time.
    LaunchedEffect(focusInContent) {
        val row = rows[returningFrom]
        if (row != null) row.requestFocus() else if (focusInContent) entryFocusRequester.requestFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        chosen?.let { You(it, entryFocusRequester) }
        // A kid manages nothing, so is shown nobody else either.
        if (chosen?.kids != true) Household(if (chosen == null) entryFocusRequester else list, chosen?.owner == true, rows, onOpen)
    }
}

/** Who is watching and the way to switch, then their default subtitle language as one radio row per choice. */
@Composable
private fun You(
    profile: Profile,
    entry: FocusRequester,
) {
    val settings: ProfileSettingsViewModel = hiltViewModel()
    val subtitle by settings.subtitle.collectAsStateWithLifecycle()
    // The gate's own picker and Manage: the activity holds one of each, so this reopens what the masthead's name does.
    val picker: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(
            heading = WHO_IS_WATCHING,
            rows = listOf("Watching as" to profileStatus(profile)),
            modifier = Modifier.focusRequester(entry),
            focusable = true,
        )
        // Manage closed first: the gate shows it in the picker's place while it is open.
        TvTextRow(
            SWITCH_PROFILE,
            onClick = {
                manage.close()
                picker.reopen()
            },
        )
        TvQuietLine("Subtitles, for every title this profile opens:")
        for ((value, label) in ProfileSubtitleChoices) {
            val chosen = value == subtitle
            TvTextRow(
                text = "${if (chosen) CHOSEN else NOT_CHOSEN}  $label",
                onClick = { settings.chooseSubtitle(value) },
                modifier =
                    Modifier.semantics {
                        selected = chosen
                        role = Role.RadioButton
                    },
            )
        }
        TvQuietLine("Forced subtitles still appear when a film switches language.")
    }
}

/**
 * Everyone here at a glance, each name beside its role — one stop, for a
 * household long enough to scroll — then the row that opens Manage, then,
 * while there is another household to switch to, the one this device shows,
 * then the owner's Households: [asOwner]'s to open, and anyone else is told
 * they are the owner's and may say they are the owner.
 */
@Composable
private fun Household(
    list: FocusRequester,
    asOwner: Boolean,
    rows: Map<TvSettingsPanel, FocusRequester>,
    onOpen: (TvSettingsPanel) -> Unit,
) {
    val profiles by hiltViewModel<ManageProfilesViewModel>().profiles.collectAsStateWithLifecycle()
    val households: JoinHouseholdViewModel = hiltViewModel()
    val join by households.state.collectAsStateWithLifecycle()
    val part by hiltViewModel<HouseholdsOwnerViewModel>().state.collectAsStateWithLifecycle()
    // Read again as the page shows: a household made or renamed on another device since.
    LaunchedEffect(households) { households.reread() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(
            heading = EVERYONE_HERE,
            rows = everyoneHere(profiles).map { (profile, line) -> profile.name to line },
            modifier = Modifier.focusRequester(list),
            focusable = true,
        )
        TvTextRow(MANAGE_PROFILES, onClick = { onOpen(TvSettingsPanel.Profiles) }, focusRequester = rows.getValue(TvSettingsPanel.Profiles))
        if (join.canSwitch) {
            val here = listOfNotNull(HouseholdWords.THIS_DEVICE, join.here?.name).joinToString(" · ")
            TvTextRow(here, onClick = { onOpen(TvSettingsPanel.Join) }, focusRequester = rows.getValue(TvSettingsPanel.Join))
        }
        if (!asOwner) TvQuietLine(HouseholdWords.OWNER_NOTE)
        if (asOwner || part is HouseholdsOwnerUiState.Open || (part as? HouseholdsOwnerUiState.Closed)?.canOpen == true) {
            val words = if (asOwner) HouseholdWords.HOUSEHOLDS else HouseholdWords.OWNER
            TvTextRow(words, onClick = { onOpen(TvSettingsPanel.Households) }, focusRequester = rows.getValue(TvSettingsPanel.Households))
        }
    }
}

/**
 * Settings' own Manage profiles, taking the whole screen as one visit
 * ([ProfilesVisit]): "Who are you?" on the way in and again on the way back
 * from the background, the PIN dropped on the way out. Done and Back close
 * the panel, and the panel closing is what ends the visit.
 */
@Composable
internal fun TvProfilesPanel(onDone: () -> Unit) {
    ProfilesVisit(open = true)
    TvManageScreen(hiltViewModel(), onClose = onDone)
}
