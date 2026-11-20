package ui.common.player

/** What fills the stage where a picture would be: the title's poster while it has sound and no picture, otherwise nothing (black, as before). */
fun stageCoverOf(
    audioOnly: Boolean,
    posterPath: String?,
): String? = posterPath.takeIf { audioOnly }
