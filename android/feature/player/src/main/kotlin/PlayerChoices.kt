package player

import playback.AudioOption
import playback.DEFAULT_CUE_BACKING
import playback.DEFAULT_CUE_SIZE_PERCENT
import playback.Framing

/**
 * What this viewer has chosen for the title currently open, framing
 * included — one store rather than each choice inventing its own, so a leak
 * across titles has exactly one place to be prevented — [PlayerViewModel.open] always
 * replaces this with [Default] before the remembered choice (or the
 * default) is applied, since the player underneath is a single app-scoped
 * instance that never resets itself.
 *
 * The subtitle size and backing are kept raw here (a percent, a stored
 * value) rather than as the `CueAppearance` they render to — the sheet
 * needs to know which row is selected, which a rendering-only value cannot
 * always be read back out of unambiguously, and `SubtitleLayer` computes
 * its own appearance from these at the one place that draws it.
 *
 * The cues themselves are not a field here: `PlayerChoicesController.subtitleCues`
 * carries them separately, the way `openSet` does — a list that can run to
 * a feature film's whole transcript has no business being copied on every
 * speed or size change this same object also carries.
 */
data class PlayerChoices(
    val speed: Float = 1f,
    /**
     * The open title's audio tracks, in menu order. Empty before the file's
     * own tracks and the remembered language have both resolved, and for a
     * file with only one stream to offer — either way, nothing to show.
     */
    val audioOptions: List<AudioOption> = emptyList(),
    /** The Subtitles section's rows: "Off" plus a language per subtitle track. Empty for a file with none, which is also what hides the section. */
    val subtitleOptions: List<SubtitleOption> = emptyList(),
    /** Whether the open title has any subtitle track at all — a forced-only file still gates the style/offset controls open, unlike [subtitleOptions], which is empty for it. */
    val subtitleStyleVisible: Boolean = false,
    /** A subtitle cue's size, as a percent of the base text size — one of `playback.CUE_SIZES`. */
    val subtitleSizePercent: Int = DEFAULT_CUE_SIZE_PERCENT,
    /** A subtitle cue's backing — one of `playback.CUE_BACKINGS`' stored values. */
    val subtitleBacking: String = DEFAULT_CUE_BACKING,
    /** How far a subtitle cue is shifted along the clock. */
    val subtitleOffsetMs: Long = 0L,
    /** How the picture sits in the window — the sheet's own row, or a pinch. */
    val framing: Framing = Framing.Default,
) {
    /** Whether the CC control has a regular track to act on. */
    val ccVisible: Boolean get() = subtitleOptions.isNotEmpty()

    /** Whether a regular subtitle track is showing. */
    val subtitlesOn: Boolean get() = subtitleOptions.any { it.selected && it.value != SUBTITLES_OFF }

    companion object {
        val Default = PlayerChoices()
    }
}
