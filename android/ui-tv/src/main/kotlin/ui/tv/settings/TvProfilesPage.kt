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
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ProfileViewModel
import catalog.profile.SWITCH_PROFILE
import catalog.profile.WHO_IS_WATCHING
import designsystem.Spacing
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
 * their subtitle language — then Manage profiles, then — while there is more
 * than one household — which one this device shows. A kid sees only You.
 *
 * Where the phone draws Manage inline — Who's managing?'s tiles where
 * Everyone here was — the television opens it as a panel of its own
 * ([TvProfilesPanel]) from the "Manage profiles" row — the tiles, then that
 * grown-up's view, or for the owner watching their view at once — and Switch
 * household (`TvJoinHousehold`) from the This device row: their PIN pads and
 * name questions take the whole screen here wherever they open, and one
 * screen at a time keeps nothing behind them competing for the remote.
 * [onOpen] opens the panel a row names. Seeing the page needs no PIN.
 *
 * [entryFocusRequester] is the page's first stop — who is watching, or with
 * nobody chosen yet Manage profiles — drawn whatever has loaded, so the page
 * always has somewhere to be entered. [returningFrom] lands on the row that
 * opened that panel instead — on Manage profiles where that row has gone
 * meanwhile.
 */
@Composable
internal fun TvProfilesPage(
    chosen: Profile?,
    focusInContent: Boolean,
    returningFrom: TvSettingsPanel?,
    entryFocusRequester: FocusRequester,
    onOpen: (TvSettingsPanel) -> Unit,
) {
    val rows = remember { listOf(TvSettingsPanel.Profiles, TvSettingsPanel.Join).associateWith { FocusRequester() } }
    // Keyed on focusInContent alone, as Telegram's is: the hub forgets the
    // panel it came back from right after, which must not land a second time.
    // Back on one of Household's rows is Household's to land: it knows which are drawn.
    LaunchedEffect(focusInContent) { if (returningFrom !in rows && focusInContent) entryFocusRequester.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        chosen?.let { You(it, entryFocusRequester) }
        // A kid manages nothing, so is shown nobody else either.
        if (chosen?.kids != true) Household(entryFocusRequester.takeIf { chosen == null }, rows, returningFrom, onOpen)
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
 * Settings' own Manage profiles, taking the whole screen as one visit
 * ([ProfilesVisit]): Who's managing? on the way in and again on the way back
 * from the background — the owner's view at once for the owner [chosen] —
 * the PIN dropped on the way out. Done, and Back from Who's managing? or from
 * the view opened at once, close the panel, and the panel closing is what
 * ends the visit.
 */
@Composable
internal fun TvProfilesPanel(
    chosen: Profile?,
    onDone: () -> Unit,
) {
    ProfilesVisit(open = true, chosen)
    TvManageScreen(hiltViewModel(), onClose = onDone)
}
