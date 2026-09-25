package playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ported from the web's `framing.test.ts`, adapted to what this app actually
 * needs from framing: not a CSS style but a box in pixels for a parent to
 * centre and clip to. A 21:9-ish container (2000x900) is used throughout so
 * a forced ratio never coincides with the container's own shape by accident
 * the way a 16:9 container would for [Framing.RATIO_16_9].
 */
class FramingTest {

    private val wideFilm = 2.39f // the phase's own 2.39:1 film example
    private val lesson = 4f / 3f

    private val containerWidth = 2000f
    private val containerHeight = 900f

    private fun framed(framing: Framing, videoAspect: Float) = frame(framing, videoAspect, containerWidth, containerHeight)

    /** No mode ever stretches the picture — the box always keeps the video's own shape, whatever it is cropped into. */
    @Test
    fun everyModeKeepsTheVideosOwnShape() {
        for (framing in Framing.entries) {
            for (aspect in listOf(wideFilm, lesson)) {
                val box = framed(framing, aspect).box
                assertEquals(aspect, box.width / box.height, absoluteTolerance = 0.001f)
            }
        }
    }

    // -- fit: a window the size of the container, letterboxed within it using the video's own shape --

    @Test
    fun fitLetterboxesAWideFilm() {
        val (box, window) = framed(Framing.FIT, wideFilm)
        assertEquals(VideoBox(2000f, 2000f / wideFilm), box)
        assertEquals(VideoBox(containerWidth, containerHeight), window)
        assertTrue(box.height <= containerHeight)
    }

    @Test
    fun fitLetterboxesA4by3Lesson() {
        val (box, window) = framed(Framing.FIT, lesson)
        assertEquals(VideoBox(900f * lesson, 900f), box)
        assertEquals(VideoBox(containerWidth, containerHeight), window)
        assertTrue(box.width <= containerWidth)
    }

    // -- fill: a window the size of the container, cropped over it using the video's own shape --

    @Test
    fun fillCropsAWideFilmToTheContainer() {
        val (box, window) = framed(Framing.FILL, wideFilm)
        assertEquals(VideoBox(900f * wideFilm, 900f), box)
        assertEquals(VideoBox(containerWidth, containerHeight), window)
        assertTrue(box.width >= containerWidth) // crops the sides
    }

    @Test
    fun fillCropsA4by3LessonToTheContainer() {
        val (box, window) = framed(Framing.FILL, lesson)
        assertEquals(VideoBox(2000f, 2000f / lesson), box)
        assertEquals(VideoBox(containerWidth, containerHeight), window)
        assertTrue(box.height >= containerHeight) // crops top and bottom
    }

    // -- named ratios: a smaller, fixed-shape window letterboxed within the container, cropped over using the video's own shape --

    @Test
    fun ratio16by9CropsAWideFilmIntoA16by9Window() {
        val (box, window) = framed(Framing.RATIO_16_9, wideFilm)
        val expectedWindow = VideoBox(900f * (16f / 9f), 900f)
        assertEquals(expectedWindow, window)
        // The window is narrower than the container — pillarboxed either side.
        assertTrue(window.width < containerWidth)
        // The film is wider than the window: covering it matches height and
        // crops the sides, at the film's own shape — never top and bottom.
        assertEquals(VideoBox(expectedWindow.height * wideFilm, expectedWindow.height), box)
        assertTrue(box.width >= expectedWindow.width)
    }

    @Test
    fun ratio16by9sWindowIgnoresTheSourceEntirely() {
        // The point of a forced ratio: the same window for a wide film and a
        // 4:3 lesson alike, because only the container and the ratio decide
        // its shape — never the video's own, unlike Fill.
        assertEquals(framed(Framing.RATIO_16_9, wideFilm).window, framed(Framing.RATIO_16_9, lesson).window)
    }

    @Test
    fun ratio4by3CropsA16by9EpisodeIntoA4by3WindowWithoutStretchingIt() {
        val (box, window) = framed(Framing.RATIO_4_3, 16f / 9f)
        val expectedWindow = VideoBox(900f * (4f / 3f), 900f)
        assertEquals(expectedWindow, window)
        assertEquals(16f / 9f, box.width / box.height, absoluteTolerance = 0.001f)
        assertTrue(box.width >= expectedWindow.width) // covers the window, cropping the sides
    }

    // -- what a viewer can actually see, once the box is clipped to the window --

    @Test
    fun fitsAndFillsWindowIsTheWholeContainer() {
        assertEquals(VideoBox(containerWidth, containerHeight), framed(Framing.FIT, wideFilm).window)
        assertEquals(VideoBox(containerWidth, containerHeight), framed(Framing.FILL, wideFilm).window)
    }

    @Test
    fun aNamedRatiosWindowIsNeverBiggerThanTheContainer() {
        for (framing in listOf(Framing.RATIO_16_9, Framing.RATIO_4_3)) {
            val window = framed(framing, wideFilm).window
            assertTrue(window.width <= containerWidth)
            assertTrue(window.height <= containerHeight)
        }
    }

    // -- what the sheet offers, and what a corrupt preference falls back to --

    @Test
    fun theStoredFramingOrFitForAnythingElse() {
        assertEquals(Framing.FILL, Framing.orDefault("fill"))
        assertEquals(Framing.FIT, Framing.orDefault(null))
        assertEquals(Framing.FIT, Framing.orDefault("sideways"))
        assertEquals(Framing.FIT, Framing.Default)
    }

    @Test
    fun theFourFramingsMatchTheWebsNamesAndLabels() {
        assertEquals(listOf("fit", "fill", "16:9", "4:3"), Framing.entries.map { it.stored })
        assertEquals(listOf("Fit", "Fill", "16:9", "4:3"), Framing.entries.map { it.label })
    }

    @Test
    fun aNonsenseSourceOrContainerAnswersTheContainerUnchanged() {
        assertEquals(Framed(VideoBox(containerWidth, containerHeight), VideoBox(containerWidth, containerHeight)), frame(Framing.FIT, 0f, containerWidth, containerHeight))
        assertEquals(Framed(VideoBox(containerWidth, containerHeight), VideoBox(containerWidth, containerHeight)), frame(Framing.FILL, Float.NaN, containerWidth, containerHeight))
        assertEquals(Framed(VideoBox(0f, 0f), VideoBox(0f, 0f)), frame(Framing.FIT, wideFilm, 0f, 0f))
    }

    private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }
}
