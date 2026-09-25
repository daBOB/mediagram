// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import java.nio.charset.StandardCharsets

/**
 * Turns a set's subtitle text — WebVTT, exactly as the uploader's sidecar
 * wrote it (`crates/mediagram/src/course/sidecars.rs`) — into plain
 * [TimedCue]s, using media3's own `WebvttParser` rather than a hand-written
 * one: it ships with this app's media3 version (bundled in `media3-extractor`,
 * which `media3-exoplayer` already brings in as an `api` dependency) and
 * covers everything the uploader's own transcriber writes.
 *
 * ExoPlayer never sees this file at all — ports its cues onto Compose's own
 * `SubtitleLayer` instead, which is what gives this player an offset, a size
 * and a backing with no `media3-ui` `View` dependency and no re-preparing
 * the player to change any of them. See the module's own text renderer being
 * disabled in `PlayerFactory.kt` for the other half of that decision: no
 * embedded or forced track is ever offered either, matching the web, which
 * never extracts one from a container to begin with.
 */
fun parseWebVttCues(vtt: String): List<TimedCue> {
    val bytes = vtt.toByteArray(StandardCharsets.UTF_8)
    val cues = mutableListOf<TimedCue>()
    WebvttParser().parse(bytes, SubtitleParser.OutputOptions.allCues()) { cuesWithTiming ->
        // A cue group can carry more than one `Cue` (multiple simultaneous
        // lines); joined into one block, the same as one subtitle box holds
        // more than one line of dialogue. Markup is already stripped by the
        // time `Cue.text` is read — as far as `<video>` itself ever
        // interprets a VTT tag.
        val text = cuesWithTiming.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
        if (text.isNotBlank()) {
            cues += TimedCue(
                startMs = cuesWithTiming.startTimeUs / 1000,
                endMs = cuesWithTiming.endTimeUs / 1000,
                text = text,
            )
        }
    }
    return cues
}
