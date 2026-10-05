package ui.tv.player

import androidx.compose.ui.focus.FocusRequester

/** The focus stops the player screen moves the remote between — each attached for as long as its control is drawn, never added or dropped by a condition. */
internal class TvPlayerFocus {
    val playPause = FocusRequester()
    val seekBar = FocusRequester()
    val cc = FocusRequester()
    val subtitleOptions = FocusRequester()
    val speed = FocusRequester()
    val audio = FocusRequester()
    val framing = FocusRequester()
    val episodes = FocusRequester()
    val marks = FocusRequester()
    val upNext = FocusRequester()
    val notes = FocusRequester()
    val retry = FocusRequester()
    val notesRegion = FocusRequester()

    /** The control a menu or the episode list was opened from: where the remote goes back to when it closes. */
    var opener: FocusRequester = playPause

    /** The tool [menu] is opened from. */
    fun openerOf(menu: TvCardMenu): FocusRequester =
        when (menu) {
            TvCardMenu.Subtitles, TvCardMenu.SubtitleStyle -> subtitleOptions
            TvCardMenu.Speed -> speed
            TvCardMenu.Audio -> audio
            TvCardMenu.Framing -> framing
        }
}
