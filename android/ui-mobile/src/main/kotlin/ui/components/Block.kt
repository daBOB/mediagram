package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A heading and its label/value rows. A row whose value is null is left out entirely. */
@Composable
internal fun Block(
    heading: String,
    rows: List<Pair<String, String?>>,
) {
    Column {
        Text(text = heading, style = MaterialTheme.typography.titleMedium)
        for ((label, value) in rows) {
            if (value.isNullOrEmpty()) continue
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
