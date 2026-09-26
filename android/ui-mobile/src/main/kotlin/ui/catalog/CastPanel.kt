package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import catalog.initialsOf
import coil3.compose.AsyncImage
import designsystem.Spacing
import model.Credit
import model.TitleCredits
import java.io.File

private val FACE_SIZE = 64.dp
private val PERSON_WIDTH = 88.dp

/**
 * The Cast tab: who directed or created it, then who is in it — a Compose
 * port of `cast.js`'s `castPanel`/`personCard`. Only ever built once a
 * title's [TitleCredits] name somebody; the caller gates the tab itself on
 * `credits.cast.isNotEmpty()`, the same rule `offerCast` follows on the web.
 */
@Composable
internal fun CastPanel(
    credits: TitleCredits,
    onOpenPerson: (Long) -> Unit,
    fetchPortrait: suspend (Long) -> String?,
    shouldRequestPortrait: (Long) -> Boolean,
) {
    Column {
        crewLine(credits.crew)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.medium),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), contentPadding = PaddingValues(vertical = Spacing.small)) {
            items(items = credits.cast, key = Credit::personId) { person ->
                PersonCard(person, onClick = { onOpenPerson(person.personId) }, fetchPortrait, shouldRequestPortrait)
            }
        }
    }
}

/**
 * "Directed by A, B" or "Created by A, B" — a show's own crew calls itself
 * a creator rather than a director, and only the first credited name says
 * which this is, exactly as `cast.js`'s own crew line reads it.
 */
private fun crewLine(crew: List<Credit>): String? {
    if (crew.isEmpty()) return null
    val verb = if (crew.first().role == "Creator") "Created by" else "Directed by"
    return "$verb " + crew.joinToString(", ") { it.name }
}

@Composable
private fun PersonCard(
    person: Credit,
    onClick: () -> Unit,
    fetchPortrait: suspend (Long) -> String?,
    shouldRequestPortrait: (Long) -> Boolean,
) {
    val portrait = rememberPortrait(person.personId, person.portraitPath, shouldRequestPortrait, fetchPortrait)
    Column(
        modifier = Modifier.width(PERSON_WIDTH).clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.size(FACE_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                AsyncImage(
                    model = File(portrait),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(
                    text = initialsOf(person.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.extraSmall),
        )
        person.role?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
