package player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import playback.AudioTrackFacts

/**
 * The real-`Player` mechanics [AudioChoiceController] drives — split out to
 * keep that file under the project's line guideline. Nothing here decides
 * *which* track to pick; that is `playback.audioTrackForLanguage`, plain
 * enough to test without any of this.
 */

/**
 * This open's audio tracks, read off a real `Tracks` the moment ExoPlayer
 * reports it. A track the device cannot decode is left out entirely —
 * this app carries no FFmpeg extension, so `DefaultTrackSelector` already
 * knows a DTS or TrueHD stream on a stock build has nothing to play it
 * with, and offering a choice this app cannot honour would be worse than
 * not offering it.
 */
internal fun extractAudioFacts(tracks: Tracks): List<AudioTrackFacts> {
    val result = mutableListOf<AudioTrackFacts>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            result += AudioTrackFacts(
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                language = format.language,
                label = format.label,
                channelCount = format.channelCount,
                sampleMimeType = format.sampleMimeType,
                isSelected = group.isTrackSelected(trackIndex),
            )
        }
    }
    return result
}

/** The audio group at [groupIndex] in [tracks], or `null` if there is none there — the tracks changed out from under this open, which the next `onTracksChanged` will correct. */
private fun audioTrackGroup(tracks: Tracks, groupIndex: Int): TrackGroup? =
    tracks.groups.getOrNull(groupIndex)?.takeIf { it.type == C.TRACK_TYPE_AUDIO }?.mediaTrackGroup

/**
 * Selects (`groupIndex`, `trackIndex`) on [player], its `TrackGroup` found
 * in [tracks] — the exact snapshot a caller's facts came from, never a
 * fresh `player.currentTracks` lookup, which may already answer for a
 * different open by the time this runs. `false`, applying nothing, when
 * there is no live player or that group is no longer there.
 */
internal fun pinAudioTrack(player: Player?, tracks: Tracks?, groupIndex: Int, trackIndex: Int): Boolean {
    val group = tracks?.let { audioTrackGroup(it, groupIndex) } ?: return false
    if (player == null) return false
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setOverrideForType(TrackSelectionOverride(group, trackIndex))
        .build()
    return true
}

/** Drops whatever audio override [applyAudioOverride] left on [player] — the singleton never does this itself between titles. */
internal fun clearAudioOverride(player: Player) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .build()
}
