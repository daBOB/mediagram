package com.mediagram.spieltisch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import uniffi.spieltisch_core.MediaImages

/**
 * The game night's process-wide half. Leaving finishes the activity while its stop may still be
 * running, so the stop and the one NSD registration belong to the process, not to a screen: the next
 * screen's night waits for that stop, and its announcement replaces any left behind.
 */
class NightSession(
    val core: SpieltischCore,
    val announcer: GameNightAnnouncer,
    /** Stops run here: clearing a view model must not cancel one. */
    val scope: CoroutineScope,
    /** The app's files folder, where the host keeps the games' log. */
    val dataDir: String,
    /** The media app, which says whose night it is and which films it may show. */
    val media: MediaLibrary,
    /** Fetches those films' images from the media app. */
    val images: MediaImages,
) {
    init {
        // Nothing is kept from it: each night still asks for itself.
        media.warmUp()
    }

    /** The latest stop, which every later start waits out, whichever screen asked for it. Main thread only. */
    var stopping: Job? = null
}
