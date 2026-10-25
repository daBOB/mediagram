package ui.common.ambient

import android.graphics.BitmapFactory
import android.graphics.RuntimeShader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ambient's colour of the moment — on television the focused poster's, on a
 * tablet the page's own or the row scrolled to — which the glow behind the
 * page ([AmbientBackdrop]) eases to. Held by the library, so it carries from
 * a wall to the title it opened and back.
 */
@Stable
class AmbientTint {
    var target by mutableStateOf<Color?>(null)

    /** The backdrops on screen, in window coordinates, that the glow keeps off ([keepsGlowAway]). */
    internal val arts = mutableStateMapOf<Any, Rect>()
}

/** Provided only while Ambient is on; a card with none to tell does nothing more. */
val LocalAmbientTint = staticCompositionLocalOf<AmbientTint?> { null }

/**
 * The glow of a poster at [file]: its strongest colour ([strongestColourOf]),
 * worked out once from a decode a few dozen pixels across and kept, lifted so
 * a dark poster still lights the page ([glowOf]). `null` until it is known, or
 * with no file.
 */
@Composable
fun rememberPosterColor(file: File?): Color? =
    // Keyed on the file, state and all: a page that swaps its picture in place (one title opening
    // another) would otherwise keep the first picture's colour, the state outliving its producer.
    key(file) {
        produceState(initialValue = file?.path?.let(PosterGlows::get)?.let(::Color)) {
            if (file == null || value != null) return@produceState
            value = withContext(Dispatchers.IO) { glowOfPoster(file) }?.let(::Color)
        }.value
    }

private object PosterGlows : LruCache<String, Int>(512)

private fun glowOfPoster(file: File): Int? {
    PosterGlows.get(file.path)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= SampleWidth) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return glowOf(strongestColourOf(pixels)).also { PosterGlows.put(file.path, it) }
    } finally {
        bitmap.recycle()
    }
}

/**
 * A poster's strongest colour: its pixels sorted into twelve families of hue,
 * each weighed by how vivid it is (saturated, neither near black nor white),
 * and the heaviest family's own average. A plain average of a poster in red
 * and blue is a grey, and grey glows as a pale patch; a poster with no colour
 * to speak of — black and white — keeps its plain average, a grey it earns.
 */
fun strongestColourOf(pixels: IntArray): Int {
    val weight = DoubleArray(Hues)
    val red = DoubleArray(Hues)
    val green = DoubleArray(Hues)
    val blue = DoubleArray(Hues)
    var r = 0L
    var g = 0L
    var b = 0L
    val hsl = FloatArray(3)
    for (pixel in pixels) {
        val pr = (pixel shr 16) and 0xFF
        val pg = (pixel shr 8) and 0xFF
        val pb = pixel and 0xFF
        r += pr
        g += pg
        b += pb
        ColorUtils.colorToHSL(pixel, hsl)
        val vivid = hsl[1] * (1f - kotlin.math.abs(2f * hsl[2] - 1f))
        val family = (hsl[0] / (360f / Hues)).toInt().coerceIn(0, Hues - 1)
        weight[family] += vivid
        red[family] += pr * vivid
        green[family] += pg * vivid
        blue[family] += pb * vivid
    }
    if (pixels.isEmpty()) return android.graphics.Color.GRAY
    val best = weight.indices.maxBy { weight[it] }
    if (weight[best] / pixels.size < ColourlessBelow) {
        return android.graphics.Color.rgb((r / pixels.size).toInt(), (g / pixels.size).toInt(), (b / pixels.size).toInt())
    }
    return android.graphics.Color.rgb((red[best] / weight[best]).toInt(), (green[best] / weight[best]).toInt(), (blue[best] / weight[best]).toInt())
}

private const val Hues = 12

/** Less vivid weight than this per pixel, and a poster has no colour of its own to glow in. */
private const val ColourlessBelow = 0.02

/** A poster's colour made into a glow: saturated a little more, and kept in a band of lightness that reads on the dark page. */
fun glowOf(argb: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = (hsl[1] * 1.3f + 0.08f).coerceAtMost(0.85f)
    hsl[2] = hsl[2].coerceIn(0.42f, 0.6f)
    return ColorUtils.HSLToColor(hsl)
}

/** About this many pixels across is enough to know a poster's colour. */
private const val SampleWidth = 32

/**
 * Behind the Ambient page: soft blobs drifting like a lava lamp's, merging as
 * they pass, in [AmbientTint]'s colour, easing to each new one. A fragment
 * shader on Android 13 and later (the box runs 14); before that one still
 * glow. Its own layer, so its frames redraw it alone, never the page over it.
 */
@Composable
fun AmbientBackdrop(
    tint: AmbientTint,
    modifier: Modifier = Modifier,
) {
    val colour by animateColorAsState(tint.target ?: Color.Transparent, tween(durationMillis = 900), label = "ambientTint")
    // Where this layer sits in the window, to place the backdrops it keeps clear of.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val glowLayer =
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            // Offscreen, so cutting around a backdrop takes the glow alone, never the page beneath it.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val reach = GlowAwayReach.toPx()
                tint.arts.values.forEach { cutGlowAround(it.translate(-origin), reach) }
            }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val seconds = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { seconds.floatValue = (it - start) / 1e9f }
        }
        Box(glowLayer.lavaLamp({ colour }, { seconds.floatValue }))
    } else {
        Box(
            glowLayer.drawWithCache {
                onDrawBehind { drawRect(Brush.radialGradient(listOf(colour.copy(alpha = 0.3f), Color.Transparent), center = Offset(size.width * 0.7f, size.height * 0.4f), radius = size.minDimension)) }
            },
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.lavaLamp(
    colour: () -> Color,
    seconds: () -> Float,
): Modifier =
    drawWithCache {
        val shader = RuntimeShader(LavaLamp)
        val brush = ShaderBrush(shader)
        onDrawBehind {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", seconds())
            shader.setColorUniform("tint", colour().toArgb())
            drawRect(brush)
        }
    }

/**
 * Four blobs on slow, unrelated orbits; their fields summed, as metaballs are,
 * so two drawing near swell into one and part again. Each is small beside the
 * screen, with a wide soft edge — diffuse, as the user asked — and the whole
 * stays a faint tint under the page.
 */
private const val LavaLamp = """
uniform float2 size;
uniform float time;
layout(color) uniform half4 tint;

half4 main(float2 p) {
    float aspect = size.x / size.y;
    float2 uv = p / size.y;
    float field = 0.0;
    for (int i = 0; i < 4; i++) {
        float f = float(i);
        float2 c = float2(
            aspect * (0.5 + 0.40 * sin(time * (0.17 + 0.031 * f) + f * 1.9)),
            0.5 + 0.36 * cos(time * (0.13 + 0.027 * f) + f * 2.6));
        float r = 0.16 + 0.03 * sin(time * 0.21 + f * 1.3);
        float2 d = uv - c;
        field += r * r / max(dot(d, d), 0.0001);
    }
    float a = smoothstep(0.12, 2.2, field) * 0.34 * tint.a;
    return half4(tint.rgb * a, a);
}
"""
