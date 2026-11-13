package com.mediagram.spieltisch

import kotlinx.coroutines.CompletableDeferred
import uniffi.spieltisch_core.MediaImageKind
import uniffi.spieltisch_core.MediaImages
import uniffi.spieltisch_core.NightUrls
import uniffi.spieltisch_core.SpieltischConfig

/** The night without the native library: it records what it was asked and answers what a test sets. */
class FakeSpieltischCore : SpieltischCore {
    var urls = NightUrls("http://127.0.0.1:8080/tv", "http://192.168.0.2:8080/")

    /** Raised by [startNight] instead of answering. */
    var startFailure: Throwable? = null

    /** Every config [startNight] was started with, in order. */
    val starts = mutableListOf<SpieltischConfig>()

    /** The images every start was handed, in order. */
    val startImages = mutableListOf<MediaImages>()

    /** When set, [startNight] suspends on it after recording the config: a start still in flight. */
    var startGate: CompletableDeferred<Unit>? = null

    /** What [nightBack] answers: whether a game was running. */
    var backAnswer = false

    /** How many times [nightBack] ran. */
    var backs = 0
        private set

    var stops = 0
        private set

    /** When set, [stopNight] suspends on it after counting: a stop still under way. */
    var stopGate: CompletableDeferred<Unit>? = null

    override suspend fun startNight(
        config: SpieltischConfig,
        images: MediaImages,
    ): NightUrls {
        starts += config
        startImages += images
        startGate?.await()
        startFailure?.let { throw it }
        return urls
    }

    override suspend fun nightBack(): Boolean {
        backs++
        return backAnswer
    }

    override suspend fun stopNight() {
        stops++
        stopGate?.await()
    }
}

/** No media app on the box: every night is unanswered. */
val NoMediaApp = MediaLibrary { MediaNight.Unanswered }

/** A media app that hands out no images. */
object NoImages : MediaImages {
    override fun fetch(
        key: String,
        kind: MediaImageKind,
    ): String? = null
}

/** Remembers what the view model asked to announce on the network, and what is announced now. */
class RecordingAnnouncer : GameNightAnnouncer {
    val announced = mutableListOf<String>()

    /** The join URL phones would find now; null once withdrawn. */
    var live: String? = null
        private set

    override fun announce(joinUrl: String) {
        announced += joinUrl
        live = joinUrl
    }

    override fun withdraw() {
        live = null
    }
}
