package playback

/**
 * How the picture sits in its container — ported from the web's
 * `framing.js`. [FIT] letterboxes, which is the honest default and the one
 * to come back to. The other three crop instead: [FILL] to whatever shape
 * this container happens to be, and the two named ratios to a fixed shape
 * regardless of it — for a film mastered with its bars already burnt into a
 * 16:9 frame, which is a correctly displayed picture of a letterbox and no
 * less black for it. Choosing "16:9" crops what a real 16:9 frame would not
 * have shown; that is a viewer's judgement, not a fact this file can read
 * off the video. A named ratio never distorts the picture — see [frame].
 *
 * [stored] is what a choice is remembered under (`PlayerPreferences`'
 * `"framing"` key) — the same strings the web writes, even though nothing
 * syncs a phone's choice to it; there is no reason for the two players to
 * spell the same four names differently.
 */
enum class Framing(val stored: String, val label: String, internal val forcedAspect: Float?) {
    FIT("fit", "Fit", null),
    FILL("fill", "Fill", null),
    RATIO_16_9("16:9", "16:9", 16f / 9f),
    RATIO_4_3("4:3", "4:3", 4f / 3f),
    ;

    companion object {
        val Default = FIT

        /** The stored framing, or [Default] for anything nobody wrote — nothing this app stored, or nothing at all. */
        fun orDefault(stored: String?): Framing = entries.firstOrNull { it.stored == stored } ?: Default
    }
}

/** A box in pixels. */
data class VideoBox(val width: Float, val height: Float)

/**
 * [box] is where the picture itself draws, at the video's own aspect,
 * never distorted; [window] is the region a viewer can actually see once
 * [box] is clipped to it — always this app's answer to what an overlay
 * drawn over the picture (a subtitle, the up-next card) must be measured
 * against, since [box] itself may run off [window] on the cropped axis and
 * take the overlay with it otherwise.
 */
data class Framed(val box: VideoBox, val window: VideoBox)

/**
 * Where the picture draws for [framing], given the video's own
 * [videoAspect] and a [containerWidth] x [containerHeight] container — a
 * pixel answer to what `object-fit` and `aspect-ratio` settle between them
 * on the web, which Compose has no single primitive for, and which (see the
 * phase this shipped in) the web itself got wrong for the two named ratios
 * on first attempt: `object-fit: cover` alone never changes the *shape* of
 * the content, so cropping to a shape needs a window of that shape to crop
 * *into*, not just a cover algorithm run with a borrowed aspect.
 *
 * [FIT]'s window is the container itself, and its box fits *within* that
 * window using the video's own shape — the same box `Modifier.aspectRatio`
 * already produced before framing existed, so [FIT] stays pixel-identical
 * to it. [FILL]'s window is also the container, with a box that fits
 * *over* it instead, using the video's own shape again — so [FILL] only
 * ever crops to whatever shape the container happens to be, never to a
 * shape the video was not.
 *
 * The two named ratios first fit a [window] of that *ratio* within the
 * container (a letterboxed window, the same algorithm [FIT] uses on the
 * video's own shape instead) and then fit the video's own shape *over*
 * that window — cropping the picture into a fixed-shape region that may
 * itself sit letterboxed inside the container, never stretching it to fill
 * a shape it was not.
 *
 * A non-finite or non-positive [videoAspect] or container answers the
 * container itself for both — nothing sensible to crop or letterbox before
 * the video has reported a size.
 */
fun frame(framing: Framing, videoAspect: Float, containerWidth: Float, containerHeight: Float): Framed {
    if (!videoAspect.isFinite() || videoAspect <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
        val whole = VideoBox(containerWidth, containerHeight)
        return Framed(whole, whole)
    }
    val window = framing.forcedAspect?.let { fitWithin(it, containerWidth, containerHeight) }
        ?: VideoBox(containerWidth, containerHeight)
    val box = if (framing == Framing.FIT) {
        fitWithin(videoAspect, window.width, window.height)
    } else {
        fitOver(videoAspect, window.width, window.height)
    }
    return Framed(box, window)
}

private fun fitWithin(aspect: Float, containerWidth: Float, containerHeight: Float): VideoBox =
    if (containerWidth / containerHeight > aspect) {
        VideoBox(containerHeight * aspect, containerHeight)
    } else {
        VideoBox(containerWidth, containerWidth / aspect)
    }

private fun fitOver(aspect: Float, containerWidth: Float, containerHeight: Float): VideoBox =
    if (containerWidth / containerHeight > aspect) {
        VideoBox(containerWidth, containerWidth / aspect)
    } else {
        VideoBox(containerHeight * aspect, containerHeight)
    }
