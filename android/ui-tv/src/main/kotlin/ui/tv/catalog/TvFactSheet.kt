package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.FactRow
import catalog.FactValue
import designsystem.LocalCatalogueTones
import designsystem.TvTypeScale
import ui.tv.TvTextRow

/**
 * Label–value rows, as a magazine prints the facts box beside a feature —
 * the television's `factSheet` (`title-spread.js`, `.fact` in
 * `title-page.css`): the label in spaced quiet caps, the value beside it,
 * a soft rule under each row.
 *
 * Words are only read; genres and a franchise are stops the remote can
 * press, the same links the phone draws. [restoreKey] names what was just
 * left — a genre's page, or a franchise's ([franchiseRestoreKey]) — and
 * that link takes the remote back.
 */
@Composable
internal fun TvFactSheet(
    rows: List<FactRow>,
    modifier: Modifier = Modifier,
    onOpenGenre: (String) -> Unit = {},
    onOpenFranchise: (Long) -> Unit = {},
    restoreKey: String? = null,
) {
    val tones = LocalCatalogueTones.current
    val partOf = remember { FocusRequester() }
    Column(modifier = modifier.widthIn(max = FactSheetMaxWidth)) {
        for ((label, value) in rows) {
            Row(modifier = Modifier.padding(vertical = RowPadding)) {
                Text(
                    text = label.uppercase(),
                    style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, letterSpacing = 0.24.em),
                    color = tones.quiet,
                    modifier = Modifier.width(LabelWidth).padding(top = 3.dp),
                )
                when (value) {
                    is FactValue.Words -> Text(text = value.text, style = TvTypeScale.body, color = MaterialTheme.colorScheme.onSurface)
                    is FactValue.Genres -> TvGenreLinks(value.names, onOpenGenre, restoreKey)
                    is FactValue.PartOf -> {
                        val back = franchiseRestoreKey(value.franchise.id) == restoreKey
                        TvTextRow(text = value.franchise.name, onClick = { onOpenFranchise(value.franchise.id) }, focusRequester = partOf.takeIf { back })
                        LaunchedEffect(back) { if (back) partOf.requestFocus() }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(tones.ruleSoft))
        }
    }
}

/**
 * The restore key a title page saves for its "Part of" link — prefixed, so
 * a franchise's id is never read as a person's on the Cast tab, nor a
 * genre's name, which share the same key on the way back.
 */
internal fun franchiseRestoreKey(id: Long): String = "franchise:$id"

/** `.fact-sheet{max-width:40rem}`. */
private val FactSheetMaxWidth = 640.dp

/** `.fact{grid-template-columns:10rem …}`, widened for labels set at the couch's 16sp rather than the web's 11px — "AUDIO LANGUAGES" still on one line. */
private val LabelWidth = 220.dp

/** `.fact{padding:14px 0}`, tighter: all nine Details rows have to fit under the tab row on a 540dp screen. */
private val RowPadding = 8.dp
