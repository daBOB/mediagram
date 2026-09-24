package player

import playback.AudioOption

/**
 * What this viewer has chosen for the title currently open. Speed and the
 * audio menu are the fields this phase writes; subtitles and framing join
 * them later rather than inventing their own stores, so a leak across
 * titles has exactly one place to be prevented — [PlayerViewModel.open]
 * always replaces this with [Default] before the remembered choice (or
 * the default) is applied, since the player underneath is a single
 * app-scoped instance that never resets itself.
 */
data class PlayerChoices(
    val speed: Float = 1f,
    /**
     * The open title's audio tracks, in menu order. Empty before the file's
     * own tracks and the remembered language have both resolved, and for a
     * file with only one stream to offer — either way, nothing to show.
     */
    val audioOptions: List<AudioOption> = emptyList(),
) {
    companion object {
        val Default = PlayerChoices()
    }
}
