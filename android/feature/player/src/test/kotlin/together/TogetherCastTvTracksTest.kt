package player.together

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.CastTracks
import model.together.Track
import model.together.chose
import playback.AudioOption
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import kotlin.test.Test
import kotlin.test.assertEquals

/** The TV's subtitle and audio menu in a cast: written into the session once known, and a remote's choice played here. */
class TogetherCastTvTracksTest {
    private val english = AudioOption(0, 0, "en", "English", selected = true)
    private val german = AudioOption(1, 0, "de", "Deutsch", selected = false)
    private val known =
        PlayerChoices(
            audioOptions = listOf(english, german),
            subtitleOptions = listOf(SubtitleOption(SUBTITLES_OFF, "Off", false), SubtitleOption("en", "English", true), SubtitleOption("de", "Deutsch", false)),
        )

    /** What the sync hands the TV's player: the audio and subtitles a remote chose. */
    private class Picked {
        val audio = mutableListOf<AudioOption>()
        val text = mutableListOf<String>()
    }

    private fun TogetherRig.syncing(choices: MutableStateFlow<PlayerChoices>): Picked =
        Picked().also { picked -> CastTrackSync(controller, choices, { picked.audio += it }, { picked.text += it }, controller.scope) }

    @Test
    fun tracksAreWrittenOnceKnown() =
        runTest {
            val rig = casting()
            val choices = MutableStateFlow(PlayerChoices())
            rig.syncing(choices)
            runCurrent()
            assertEquals(1, rig.edits.size, "nothing is known yet")

            choices.value = known
            runCurrent()
            choices.value = known.copy(speed = 1.5f)
            runCurrent()

            val written = rig.edits.drop(1).single().cast!!
            assertEquals(CastTracks(listOf(Track("0.0", "English"), Track("1.0", "Deutsch")), listOf(Track("en", "English"), Track("de", "Deutsch"))), written.tracks)
            assertEquals("0.0", written.audio)
            assertEquals("en", written.text)
        }

    @Test
    fun aRemoteSubtitleChoiceIsApplied() =
        runTest {
            val rig = casting()
            val choices = MutableStateFlow(known)
            val picked = rig.syncing(choices)
            runCurrent()

            val subtitles = chose(rig.message(960), TABLET.screen, text = "de")
            rig.push(960, subtitles)
            rig.push(960, chose(subtitles, TABLET.screen, audio = "1.0"))
            // The player, having applied both.
            choices.value =
                known.copy(
                    audioOptions = listOf(english.copy(selected = false), german.copy(selected = true)),
                    subtitleOptions = known.subtitleOptions.map { it.copy(selected = it.value == "de") },
                )
            runCurrent()

            assertEquals(listOf("de"), picked.text)
            assertEquals(listOf(german), picked.audio, "the option as the menu holds it")
            assertEquals(2, rig.edits.size, "a choice applied here is not written back")
        }

    @Test
    fun aRemoteEditMadeBeforeTheListWasWrittenGetsTheListAgain() =
        runTest {
            val rig = casting()
            val accepted = rig.message(960)
            val picked = rig.syncing(MutableStateFlow(known))
            runCurrent()

            // A pause the tablet made on the session as it was before the TV's list reached it.
            rig.push(960, accepted.copy(playing = false, at = NOW + 1, by = TABLET.screen))

            assertEquals(emptyList(), picked.text, "an old empty choice is not the remote's")
            assertEquals(listOf("0.0", "0.0"), rig.edits.drop(1).map { it.cast?.audio })
        }
}
