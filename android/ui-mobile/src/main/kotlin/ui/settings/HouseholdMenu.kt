package ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import model.HouseholdWords
import ui.chrome.CatalogueMenu
import ui.chrome.CatalogueMenuItem
import ui.chrome.CatalogueOutlinedButton
import ui.profile.COLLAPSED
import ui.profile.EXPANDED

/**
 * A household's ⋯ on the owner's view — the web's `household-menu.js`: one
 * round button, named for [household], that opens a list of what may be done
 * to it, each [actions] word with what choosing it does. Choosing one closes
 * it, as do Back and a tap anywhere else. Not pressed while a change is out.
 */
@Composable
internal fun HouseholdMenu(
    household: String,
    actions: List<Pair<String, () -> Unit>>,
    enabled: Boolean,
) {
    if (actions.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        CatalogueOutlinedButton(
            onClick = { expanded = !expanded },
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            contentPadding = PaddingValues(0.dp),
            modifier =
                Modifier.size(48.dp).semantics {
                    contentDescription = "${HouseholdWords.ACTIONS_FOR} $household"
                    stateDescription = if (expanded) EXPANDED else COLLAPSED
                },
        ) { Text("⋯", style = MaterialTheme.typography.titleMedium) }
        CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { (word, run) ->
                CatalogueMenuItem(
                    text = { Text(word) },
                    onClick = {
                        expanded = false
                        run()
                    },
                )
            }
        }
    }
}
