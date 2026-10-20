package player.together

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import model.together.CastTracks
import model.together.Session
import model.together.Track
import model.together.tracked
import playback.AudioOption
import player.PlayerChoices
import player.SUBTITLES_OFF

/** The TV's subtitle and audio menu as a cast holds it: the lists, and the ids playing (`""` for none). */
private data class Menu(
    val tracks: CastTracks,
    val audio: String,
    val text: String,
)

private fun AudioOption.id() = "$groupIndex.$trackIndex"

/**
 * The TV's own subtitle and audio menu, shared with the remote while this
 * screen plays a cast as its TV, so the remote's Subtitles & audio menu is
 * the one the TV shows. Once the title's tracks are known, and after every
 * change to them, the list and the tracks playing are written into the
 * session, unless that is what was last written. A remote's choice,
 * an edit by another screen that changes `audio` or `text`, is applied here
 * as the viewer's own pick would be. Plain watch-together members never
 * touch these fields: there every screen picks its own.
 */
internal class CastTrackSync(
    private val controller: TogetherController,
    choices: StateFlow<PlayerChoices>,
    private val chooseAudio: (AudioOption) -> Unit,
    private val chooseText: (String) -> Unit,
    scope: CoroutineScope,
) {
    /** What this screen last wrote, or took from a remote: the menu as the session holds it. */
    private var written: Menu? = null

    init {
        scope.launch { combine(choices, controller.following, ::Pair).collect { (c, s) -> sync(c, s) } }
    }

    private fun sync(
        choices: PlayerChoices,
        session: Session?,
    ) {
        val id = controller.id
        val cast = session?.cast?.takeIf { it.tv == controller.screen }
        if (id == null || cast == null) {
            written = null
            return
        }
        val last = written
        val held = Menu(cast.tracks, cast.audio, cast.text)
        val own = session.by == controller.screen
        // Another screen's edit that kept the list is the remote's choice; one that lost it was made before the list was, and it goes again.
        val lost = !own && last != null && held.tracks != last.tracks
        if (!own && last != null && !lost && held != last) return take(choices, held, last)
        val mine = menuOf(choices, session)
        if (mine.tracks == CastTracks() || (mine == last && !lost)) return
        written = mine
        controller.live.tracks(id, controller.screen, mine.tracks, mine.audio, mine.text)
    }

    /** The remote's choice, played here; the player's next choices then match it, and are not written back. */
    private fun take(
        choices: PlayerChoices,
        held: Menu,
        last: Menu,
    ) {
        written = held
        if (held.audio != last.audio) choices.audioOptions.firstOrNull { it.id() == held.audio }?.let(chooseAudio)
        if (held.text != last.text) chooseText(held.text.ifEmpty { SUBTITLES_OFF })
    }

    /** [choices] as the session would hold them, cut to fit as [tracked] cuts them. */
    private fun menuOf(
        choices: PlayerChoices,
        session: Session,
    ): Menu {
        val audio = choices.audioOptions
        val text = choices.subtitleOptions.filter { it.value != SUBTITLES_OFF }
        val cast =
            tracked(
                session,
                controller.screen,
                CastTracks(audio.map { Track(it.id(), it.text) }, text.map { Track(it.value, it.label) }),
                audio.firstOrNull { it.selected }?.id().orEmpty(),
                text.firstOrNull { it.selected }?.value.orEmpty(),
            ).cast!!
        return Menu(cast.tracks, cast.audio, cast.text)
    }
}
