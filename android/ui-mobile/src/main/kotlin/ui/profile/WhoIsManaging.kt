package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import catalog.profile.ManageUiState
import designsystem.Spacing
import model.HouseholdWords
import ui.settings.Notice

/**
 * Who's managing? — the web's `profile-manage-who.js`: Manage profiles' one
 * question before anyone manages, drawn with Who's watching?'s own tiles
 * ([ProfileTile]). The grown-ups here, the owner first and labelled Owner; on
 * a device showing another household the owner's tile ends the row
 * ([ManageUiState.ChoosingActor.managers]). A tile asks that person's PIN —
 * the owner's for the owner — through [onChoose]; a wrong one is said in the
 * prompt, and nothing opens. [headStyle] is the picker's heading over the
 * picker, a section's in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WhoIsManaging(
    state: ManageUiState.ChoosingActor,
    onChoose: (String) -> Unit,
    headStyle: TextStyle,
) {
    Column {
        Text(HouseholdWords.WHOS_MANAGING, style = headStyle, modifier = Modifier.semantics { heading() })
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            modifier = Modifier.padding(top = Spacing.large),
        ) {
            state.managers.forEach { one ->
                val line = HouseholdWords.OWNER.takeIf { one.id == state.owner?.id }
                ProfileTile(one, onClick = { onChoose(one.id) }, modifier = Modifier.width(TileWidth), line = line)
            }
        }
        state.notice?.let { Notice(it, Modifier.padding(top = Spacing.medium)) }
    }
}

/** The picker's grid cell at its narrowest, so a tile here is the size it is there. */
private val TileWidth = 96.dp
