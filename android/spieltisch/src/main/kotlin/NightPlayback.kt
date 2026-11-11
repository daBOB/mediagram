package com.mediagram.spieltisch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import update.PlaybackActivity

/**
 * Never "playing", except while a stop is under way. An install ends the host and the phones'
 * connections, but a night lives only while its screen shows: the screen's stop runs when it goes to
 * the background, before the updater's install on the same transition, so an install never meets a
 * running night. All that is left to wait out is that stop itself. A check or a download costs the
 * box some bandwidth, not the night, so they run during one.
 */
fun NightSession.asPlayback() =
    PlaybackActivity {
        // `stopping` belongs to the main thread.
        withContext(Dispatchers.Main.immediate) { stopping }?.join()
        false
    }
