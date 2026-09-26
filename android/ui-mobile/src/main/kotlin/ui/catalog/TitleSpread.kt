package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import designsystem.Spacing
import java.io.File

private val HERO_HEIGHT = 260.dp
private val QUOTE_MAX_WIDTH = 220.dp

/**
 * A title page's opening spread — a Compose port of `title-spread.js`'s own
 * `titleSpread`/`describeHero`: the backdrop filling the block and fading
 * into the page behind the words, the tagline set large over the artwork as
 * a pull-quote, and the title, facts and overview below it. Shared by the
 * film and the series page, exactly as the web's own module is.
 *
 * The web sets the words down the left and the art filling the right, a
 * layout a magazine page has the width for. A phone does not: the words sit
 * below the art here instead, which is the same "read the words, see the
 * picture behind them" idea in the shape this screen actually has room for
 * — a deliberate difference from the web's own two-column spread, not a
 * partial port of it.
 */
@Composable
internal fun TitleSpread(
    backdropPath: String?,
    title: String,
    facts: String?,
    overview: String?,
    tagline: String?,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
            if (backdropPath != null) {
                AsyncImage(
                    model = File(backdropPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // Fades the artwork into the page along its foot, the same
                // job the web's own CSS fade does over its own axis.
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
                )
                if (!tagline.isNullOrBlank()) {
                    Text(
                        text = "“$tagline”",
                        style = MaterialTheme.typography.headlineSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .widthIn(max = QUOTE_MAX_WIDTH)
                                .padding(Spacing.large),
                    )
                }
            } else {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Column(modifier = Modifier.padding(PaddingValues(horizontal = Spacing.large, vertical = Spacing.medium))) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
            )
            facts?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.extraSmall),
                )
            }
            overview?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.medium),
                )
            }
        }
    }
}
