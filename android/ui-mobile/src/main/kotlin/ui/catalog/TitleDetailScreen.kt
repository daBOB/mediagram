package ui.catalog

import kotlinx.coroutines.CancellationException
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import designsystem.Spacing
import model.MediaSet
import model.ageLabel
import uniffi.mediagram_core.TitleInfo
import java.io.File

/** Wide enough to recognise a poster by, narrow enough to leave the facts a column. */
private val POSTER_WIDTH = 120.dp

/**
 * What a title is, before playing it.
 *
 * A card used to play on tap, which left the synopsis the catalog already
 * downloads with nowhere to go: a film has no collection screen to hold it.
 * So a card opens this, and this plays.
 *
 * [info] being null is ordinary rather than a failure — a course has no
 * provider entry, and a library assembled without a TMDB key has no rows at
 * all. Every block it would fill is left out instead of being shown empty,
 * the same rule the System screen follows.
 *
 * Scrolling rather than fitting: an overview runs to a paragraph, and on a
 * short screen in landscape the Play button would otherwise be off the
 * bottom with no way to reach it.
 *
 * [editorsChoice] is the household's current pin, if any — [onToggleEditorsChoice]
 * is `null` on a kids profile, which is what hides the action: a household
 * mark is not a kids profile's to make, the same restriction [onOpenGenre]'s
 * neighbours already carry for Kids marks elsewhere.
 */
@Composable
fun TitleDetailScreen(
    set: MediaSet,
    info: TitleInfo?,
    onPlay: () -> Unit,
    onOpenGenre: (String) -> Unit,
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        val backdropPath = set.backdropPath
        if (backdropPath != null) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = File(backdropPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
                )
            }
        }

        Column(
            modifier = Modifier.padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            TitleHeader(
                posterPath = set.posterPath,
                title = set.title,
                facts = factsLine(set.year, set.durationSecs, set.ageLabel()),
                info = info,
                genres = set.genres,
                onOpenGenre = onOpenGenre,
            )

            // As stored, not shouted: the web player prints the container and
            // codecs in the case the index recorded, and a viewer reading both
            // surfaces should not be told the same file two ways.
            technicalLine(set).takeIf(String::isNotEmpty)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The one prominent control on the screen: this stands between a
            // card and playback now, so it should not have to be looked for.
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("▶ Play") }

            if (onToggleEditorsChoice != null) {
                val pinned = editorsChoice == set.setId
                OutlinedButton(onClick = onToggleEditorsChoice, modifier = Modifier.fillMaxWidth()) {
                    Text(if (pinned) "Remove as editor's choice" else "Make editor's choice")
                }
            }
        }
    }
}

/**
 * The block that describes something: its artwork beside its facts, then
 * what a provider said about it.
 *
 * Shared by the title detail screen and a collection's own screen, because
 * a show and an episode of it are described the same way and only differ in
 * what they can say — [facts] is a file's year and runtime, and a whole
 * show has neither, so it is null there.
 */
@Composable
internal fun TitleHeader(
    posterPath: String?,
    title: String,
    facts: String?,
    info: TitleInfo?,
    modifier: Modifier = Modifier,
    genres: List<String> = emptyList(),
    onOpenGenre: (String) -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            PosterArt(posterPath = posterPath, title = title, modifier = Modifier.width(POSTER_WIDTH))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                facts?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                ratingLabel(info?.rating)?.let { rating ->
                    Text(text = rating, style = MaterialTheme.typography.bodyMedium)
                }
                // Links to their shelves, not the plain sentence a provider's
                // genre string used to print — the catalog's own genres, the
                // same field a genre page is matched against, so tapping one
                // always lands where it says it will.
                GenreLinks(genres, onOpenGenre)
            }
        }

        // The tagline is quoted and the overview is not, because one is a
        // line of marketing and the other a paragraph of description, and a
        // viewer who cannot tell them apart has been handed a wall of text.
        info?.tagline?.takeIf(String::isNotBlank)?.let { tagline ->
            Text(
                text = "“$tagline”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        info?.overview?.takeIf(String::isNotBlank)?.let { overview ->
            Text(text = overview, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * What the index records about the title a poster key names, looked up once
 * per key.
 *
 * Null both while the answer is on its way and when there is no answer, and
 * the screen renders the same either way: a title with no provider entry is
 * the ordinary case, so a spinner over the blocks it would fill would
 * promise something that is never coming.
 */
@Composable
internal fun rememberTitleInfo(posterKey: String?, lookup: suspend (String) -> TitleInfo?): TitleInfo? {
    var info by remember(posterKey) { mutableStateOf<TitleInfo?>(null) }
    LaunchedEffect(posterKey) {
        try {
            info = posterKey?.let { lookup(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load title details", e)
        }
    }
    return info
}
