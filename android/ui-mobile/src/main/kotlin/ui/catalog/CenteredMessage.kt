package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import designsystem.CountdownLeader
import designsystem.Spacing
import ui.catalog.home.LEADER_GAP

/**
 * What a screen says when it has nothing to show.
 *
 * Set in the catalogue's own reading face rather than left at the default,
 * because a first run, an empty library and a failed load are the three
 * moments a viewer reads a whole sentence here, and they are exactly the
 * moments the app would otherwise stop sounding like itself.
 */
@Composable
internal fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        MessageText(message)
    }
}

/**
 * A wait inside a page: the film leader counting down (`index.html:100-116`)
 * above [message], in [CenteredMessage]'s words. On the page's own ground,
 * so the leader takes the page's inks, not its fixed ones for the dark.
 *
 * The web's in-page waits are words alone (`settings-telegram-forms.js:15`);
 * the leader beside them is deliberate, so every wait in this app is the same one.
 */
@Composable
internal fun CenteredLoading(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(LEADER_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CountdownLeader(ink = MaterialTheme.colorScheme.onSurfaceVariant, numeralInk = MaterialTheme.colorScheme.onSurface)
        MessageText(message)
    }
}

@Composable
private fun MessageText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
