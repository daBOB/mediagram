package ui.tv.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.ListOfSets
import ui.tv.TvTextRow
import ui.tv.catalog.TvListNameQuestion
import ui.tv.setup.TvDialog

/**
 * Filing the open title into one or more lists, from the player — the
 * phone's `AddToListDialog`, which offers a tick per list and a way to start
 * a new one without leaving the film, rather than the web's numbered prompt
 * that leans on a Collections shelf on the same page.
 *
 * Each list is a row the remote ticks or unticks with Centre; "＋ New list"
 * asks for a name the way the catalogue's own Lists tab does
 * ([TvListNameQuestion]) and puts the title straight on the list it makes,
 * then comes back here with it ticked. The remote lands on the first list,
 * or on "＋ New list" when there are none. Done and Back both close it.
 *
 * [notice] is a write that could not be confirmed, shown here as on the
 * phone, since the dialog is where the viewer is looking when it fails.
 */
@Composable
internal fun TvAddToListDialog(
    lists: List<ListOfSets>,
    memberOf: Set<String>,
    onToggle: (id: String, included: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
    notice: String? = null,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        NewListQuestion(
            onConfirm = { name ->
                naming = false
                onCreate(name.trim())
            },
            onDismiss = { naming = false },
        )
        return
    }

    val first = remember { FocusRequester() }
    TvDialog(
        title = "Add to list",
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = first,
        content = {
            notice?.let {
                Text(it, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.medium))
            }
            if (lists.isEmpty()) {
                Text("No lists yet.", style = TvTypeScale.body, color = Palette.Figures, modifier = Modifier.padding(top = Spacing.medium))
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = ListsHeight).padding(top = Spacing.medium)) {
                itemsIndexed(items = lists, key = { _, list -> list.id }) { index, list ->
                    val included = list.id in memberOf
                    TvTextRow(
                        text = "${if (included) TICKED else UNTICKED} ${list.name}",
                        onClick = { onToggle(list.id, !included) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.extraSmall),
                        focusRequester = first.takeIf { index == 0 },
                    )
                }
            }
            TvTextRow(
                text = "＋ New list",
                onClick = { naming = true },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
                focusRequester = first.takeIf { lists.isEmpty() },
            )
        },
    ) {
        Button(onClick = onDismiss) { Text("Done", style = TvTypeScale.body) }
    }
}

/**
 * The catalogue's own name question, over the film in a window of its own:
 * the whole screen, as it is in the Lists tab, because the keyboard it
 * brings up takes half the screen anyway.
 */
@Composable
private fun NewListQuestion(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors =
                SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            TvListNameQuestion(onConfirm = onConfirm, onDismiss = onDismiss)
        }
    }
}

private const val TICKED = "☑"
private const val UNTICKED = "☐"

/** About five lists before the rest scroll, so the dialog never outgrows a television's height. */
private val ListsHeight = 240.dp
