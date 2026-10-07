package ui.common.catalog

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import designsystem.Backdrop
import designsystem.LocalBackdrop
import java.io.File

/** Every [HeroArtwork] a test composes carries this tag — a screen shows at most one hero at a time, so it is enough to ask "is a picture showing at all". */
const val HERO_ARTWORK_TEST_TAG = "hero-artwork"

/** How far past its own bounds a blurred hero's picture is scaled — the web's own `scale(1.12)`, which hides the blur's own softened edge. */
private const val BLURRED_SCALE = 1.12f

/** The web's own `saturate(1.25)` on a blurred hero. */
private const val BLURRED_SATURATION = 1.25f

private val BLURRED_RADIUS = 28.dp

/** Decoded size for a blurred hero below API 31 — small enough that upscaling it is itself most of the softening. */
private const val TINY_DECODE_PX = 48

/**
 * A hero's own picture — cover, title spread, department — drawn the way
 * the current [designsystem.Backdrop] asks for it, the Compose counterpart
 * of `styles/appearance.css`'s `[data-backdrop]` rules. Every hero site
 * reaches for this instead of `AsyncImage` directly, so the four modes stay
 * one implementation instead of four call sites each guessing at blur.
 *
 * Default, Artwork and Solid draw the picture plainly here — Solid's "no
 * picture at all" is each caller's own branch, the same one already guarding
 * a missing [path], not this component's job. Only Blurred changes what is
 * drawn.
 */
@Composable
fun HeroArtwork(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val taggedModifier = modifier.testTag(HERO_ARTWORK_TEST_TAG)
    if (LocalBackdrop.current == Backdrop.BLURRED) {
        BlurredHeroArtwork(path, taggedModifier, contentScale)
    } else {
        AsyncImage(model = File(path), contentDescription = null, contentScale = contentScale, modifier = taggedModifier)
    }
}

/**
 * Blurred: `blur(28px) saturate(1.25) scale(1.12)` on the web. `Modifier.blur`
 * only reaches the screen from API 31 (`RenderEffect`); below it there is no
 * blur to ask for, so this decodes the same picture tiny instead and lets
 * [contentScale] upscale it — softer than a full-resolution crop for free,
 * and a smaller decode than every other mode already pays, on every API this
 * app supports.
 */
@Composable
private fun BlurredHeroArtwork(
    path: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val context = LocalContext.current
    val model =
        remember(path) {
            ImageRequest.Builder(context)
                .data(File(path))
                .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) size(TINY_DECODE_PX) }
                .build()
        }
    val saturated = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(BLURRED_SATURATION) }) }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = contentScale,
        colorFilter = saturated,
        modifier =
            modifier
                .clipToBounds()
                .graphicsLayer { scaleX = BLURRED_SCALE; scaleY = BLURRED_SCALE }
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(BLURRED_RADIUS, BlurredEdgeTreatment.Rectangle)
                    } else {
                        Modifier
                    },
                ),
    )
}
