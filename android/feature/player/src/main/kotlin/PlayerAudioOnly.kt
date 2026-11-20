package player

import androidx.media3.common.C
import androidx.media3.common.Tracks
import player.together.TogetherController
import player.together.firstFrame

/**
 * Sound and no picture. The shared player first reports an empty list for every
 * title, so an empty one says nothing: only a list that has arrived and holds
 * no video track is an audio-only file.
 */
internal fun isAudioOnly(tracks: Tracks): Boolean = !tracks.isEmpty && !tracks.containsType(C.TRACK_TYPE_VIDEO)

/** Tracks arrived for the open title: keeps [PlayerViewModel.audioOnly] current. */
internal fun PlayerViewModel.noteTracks(
    tracks: Tracks,
    controller: TogetherController,
) {
    if (tracks.isEmpty) return
    _audioOnly.value = isAudioOnly(tracks)
    controller.heardTracks(tracks)
}

/** A cast offered playing starts at the first frame, and an MP3 never renders one: its tracks arriving is its first frame. */
internal fun TogetherController.heardTracks(tracks: Tracks) {
    if (isAudioOnly(tracks)) firstFrame()
}
