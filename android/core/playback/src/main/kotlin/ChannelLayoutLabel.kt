package playback

/**
 * A channel count as a viewer would say it.
 *
 * Worth a file of its own because a film routinely carries the same
 * language twice, once as 5.1 and once as a stereo downmix, and "German /
 * German" is not a menu — the channel count is often the only thing telling
 * the two rows apart.
 */
fun channelLayoutLabel(channelCount: Int): String = when {
    channelCount <= 0 -> ""
    channelCount == 1 -> "mono"
    channelCount == 2 -> "stereo"
    // 6 and 8 are the two layouts everyone names; the rest are rare enough
    // that the bare count is clearer than a guess at a layout.
    channelCount == 6 -> "5.1"
    channelCount == 8 -> "7.1"
    else -> "${channelCount}ch"
}
