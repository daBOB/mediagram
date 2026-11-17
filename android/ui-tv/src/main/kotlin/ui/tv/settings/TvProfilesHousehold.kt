package ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.EVERYONE_HERE
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageProfilesViewModel
import catalog.profile.everyoneHere
import designsystem.Spacing
import model.HouseholdWords
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

/**
 * Everyone here at a glance, each name beside its role — one stop, for a
 * household long enough to scroll — then the row that opens Manage, then,
 * while there is another household to switch to, the one this device shows,
 * then the Owner row: anyone else is told households are the owner's, and may
 * say they are the owner to open the owner's view. For the owner ([asOwner])
 * Manage profiles is that view, which lists everyone in every household, so
 * neither everyone here nor the Owner row is drawn.
 */
@Composable
internal fun Household(
    list: FocusRequester,
    asOwner: Boolean,
    rows: Map<TvSettingsPanel, FocusRequester>,
    returningFrom: TvSettingsPanel?,
    onOpen: (TvSettingsPanel) -> Unit,
) {
    val profiles by hiltViewModel<ManageProfilesViewModel>().profiles.collectAsStateWithLifecycle()
    val households: JoinHouseholdViewModel = hiltViewModel()
    val join by households.state.collectAsStateWithLifecycle()
    val part by hiltViewModel<HouseholdsOwnerViewModel>().state.collectAsStateWithLifecycle()
    // Read again as the page shows: a household made or renamed on another device since.
    LaunchedEffect(households) { households.reread() }
    val ownerRow = !asOwner && (part is HouseholdsOwnerUiState.Open || (part as? HouseholdsOwnerUiState.Closed)?.canOpen == true)
    val shown = setOfNotNull(TvSettingsPanel.Join.takeIf { join.canSwitch }, TvSettingsPanel.Households.takeIf { ownerRow })
    // Once, as the page shows: This device, say, is gone once one household is left, so Manage profiles holds the remote instead.
    LaunchedEffect(Unit) { if (returningFrom in rows) rows.getValue(returningFrom.takeIf { it in shown } ?: TvSettingsPanel.Profiles).requestFocus() }
    // Which of the rows that can go holds the remote. Its going hands the
    // remote to Manage profiles rather than nothing; a row only stops holding
    // it by focus moving on while it is still shown.
    val showing by rememberUpdatedState(shown)
    var holding by remember { mutableStateOf<TvSettingsPanel?>(null) }
    fun held(row: TvSettingsPanel) = Modifier.onFocusChanged { if (it.isFocused) holding = row else if (holding == row && row in showing) holding = null }
    // Drawn a step behind what is shown, so the remote is handed on while the
    // row going still holds it: removed holding it — the last row, as This
    // device is the owner's — it would leave the page for another section.
    var drawn by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (holding?.let { it !in shown } == true) {
            holding = null
            rows.getValue(TvSettingsPanel.Profiles).requestFocus()
        }
        drawn = shown
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        if (!asOwner) {
            TvInfoBlock(
                heading = EVERYONE_HERE,
                rows = everyoneHere(profiles).map { (profile, line) -> profile.name to line },
                modifier = Modifier.focusRequester(list),
                focusable = true,
            )
        }
        TvTextRow(MANAGE_PROFILES, onClick = { onOpen(TvSettingsPanel.Profiles) }, focusRequester = rows.getValue(TvSettingsPanel.Profiles))
        if (TvSettingsPanel.Join in drawn) {
            val here = listOfNotNull(HouseholdWords.THIS_DEVICE, join.here?.name).joinToString(" · ")
            TvTextRow(here, onClick = { onOpen(TvSettingsPanel.Join) }, held(TvSettingsPanel.Join), rows.getValue(TvSettingsPanel.Join))
        }
        if (!asOwner) TvQuietLine(HouseholdWords.OWNER_NOTE)
        if (TvSettingsPanel.Households in drawn) {
            TvTextRow(HouseholdWords.OWNER, onClick = { onOpen(TvSettingsPanel.Households) }, held(TvSettingsPanel.Households), rows.getValue(TvSettingsPanel.Households))
        }
    }
}
