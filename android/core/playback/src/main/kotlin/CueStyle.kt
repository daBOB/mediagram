package playback

/**
 * The subtitle sheet's size and backing rows, and the rendering values they
 * produce — a port of the web's `SIZES`/`BACKINGS`/`cueStyle`
 * (`subtitle-panel.js`, `subtitle-style.js`). Kept as plain value/label
 * lists rather than an enum, the same shape the web itself uses, so the
 * sheet can render a row per entry without a second table to keep in step.
 */

/** One size the sheet offers, as a percent of the base text size. */
data class CueSizeOption(val percent: Int, val label: String)

val CUE_SIZES: List<CueSizeOption> = listOf(
    CueSizeOption(80, "Small"),
    CueSizeOption(100, "Normal"),
    CueSizeOption(115, "Large"),
    CueSizeOption(135, "Larger"),
)

const val DEFAULT_CUE_SIZE_PERCENT: Int = 100

/** One backing the sheet offers, as the value it is remembered under. */
data class CueBackingOption(val stored: String, val label: String)

val CUE_BACKINGS: List<CueBackingOption> = listOf(
    CueBackingOption("shadow", "Shadow"),
    CueBackingOption("box", "Box"),
    CueBackingOption("none", "None"),
)

const val DEFAULT_CUE_BACKING: String = "shadow"

/** The stored percent, or the default when it names none of [CUE_SIZES] — a corrupt or foreign value is not trusted. */
fun cueSizePercentOrDefault(stored: String?): Int =
    CUE_SIZES.firstOrNull { it.percent.toString() == stored }?.percent ?: DEFAULT_CUE_SIZE_PERCENT

/** As [cueSizePercentOrDefault], for [CUE_BACKINGS]. */
fun cueBackingOrDefault(stored: String?): String =
    CUE_BACKINGS.firstOrNull { it.stored == stored }?.stored ?: DEFAULT_CUE_BACKING

/**
 * How far a size may be scaled, as a percentage of the base size — ported
 * from the web's `SMALLEST`/`LARGEST`. The sheet only ever offers
 * [CUE_SIZES], so this is a second, independent guard on the value that
 * actually reaches rendering rather than the one a corrupt preference could
 * ever trip.
 */
private const val SMALLEST_PERCENT = 50
private const val LARGEST_PERCENT = 200

/** What a size and a backing render as. Compose's own equivalent of the web's `video::cue` rule. */
data class CueAppearance(val fontScale: Float, val backgroundAlpha: Float, val hasShadow: Boolean)

/**
 * The rendering values [sizePercent] and [backing] produce — a port of the
 * web's `cueStyle`. A `null`/out-of-range size is the browser's own 100%; a
 * backing of "box" draws a solid scrim behind the text, and anything else
 * (including an unrecognised value) draws none. Only a "shadow" backing
 * carries a shadow — a box already gives the text its own contrast, and
 * drawing both would double it.
 */
fun cueAppearance(sizePercent: Int?, backing: String?): CueAppearance {
    val percent = (sizePercent ?: DEFAULT_CUE_SIZE_PERCENT).coerceIn(SMALLEST_PERCENT, LARGEST_PERCENT)
    return CueAppearance(
        fontScale = percent / 100f,
        backgroundAlpha = if (backing == "box") 0.75f else 0f,
        hasShadow = backing == "shadow",
    )
}
