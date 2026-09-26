package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.ratingLabel
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import player.technicalLine
import androidx.tv.material3.Text
import ui.tv.TvTextRow
import uniffi.mediagram_core.TitleInfo

/**
 * Details: the file as it sits on disk, the provider's rating and status,
 * and the editor's-choice toggle — the phone's own "Make editor's
 * choice"/"Remove as editor's choice" button, ported to television. `null`
 * [onToggleEditorsChoice] hides the row entirely, same as the phone's
 * kids-profile gate.
 */
@Composable
internal fun TvTitleDetails(
    set: MediaSet,
    info: TitleInfo?,
    editorsChoice: String?,
    onToggleEditorsChoice: (() -> Unit)?,
) {
    Column {
        technicalLine(set).takeIf(String::isNotEmpty)?.let { Text(text = it, style = TvTypeScale.body) }
        ratingLabel(info?.rating)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        info?.network?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        info?.status?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        if (onToggleEditorsChoice != null) {
            val pinned = editorsChoice == set.setId
            TvTextRow(
                text = if (pinned) "Remove as editor's choice" else "Make editor's choice",
                onClick = onToggleEditorsChoice,
                modifier = Modifier.padding(top = Spacing.medium),
            )
        }
    }
}
