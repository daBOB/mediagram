package player

/**
 * What this viewer has chosen for the title currently open. Speed is the
 * only field this phase writes; audio, subtitles and framing join it in
 * later phases rather than inventing their own stores, so a leak across
 * titles has exactly one place to be prevented — [PlayerViewModel.open]
 * always replaces this with [Default] before the remembered choice (or
 * the default) is applied, since the player underneath is a single
 * app-scoped instance that never resets itself.
 */
data class PlayerChoices(val speed: Float = 1f) {
    companion object {
        val Default = PlayerChoices()
    }
}
