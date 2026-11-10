package com.mediagram.spieltisch

import kotlinx.coroutines.CompletableDeferred
import uniffi.spieltisch_core.GameEntry
import uniffi.spieltisch_core.NightUrls
import uniffi.spieltisch_core.SpieltischConfig

/** The night without the native library: it records what it was asked and answers what a test sets. */
class FakeSpieltischCore : SpieltischCore {
    var urls = NightUrls("http://127.0.0.1:8080/tv", "http://192.168.0.2:8080/")

    /** Raised by [startNight] instead of answering. */
    var startFailure: Throwable? = null

    /** Every config [startNight] was started with, in order. */
    val starts = mutableListOf<SpieltischConfig>()

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

    override suspend fun startNight(config: SpieltischConfig): NightUrls {
        starts += config
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

/** The host's catalogue in the lobby's order: more games than the row shows, Durak last and off screen. */
val Catalogue =
    listOf(
        GameEntry("quiz", "Buzzer-Quiz", 1u, 8u),
        GameEntry("poster", "Poster-Raten", 1u, 8u),
        GameEntry("kopfnuss", "Kopfnuss", 2u, 8u),
        GameEntry("dreifelder", "Dreifelder", 2u, 8u),
        GameEntry("race", "Heimlauf", 2u, 4u),
        GameEntry("grand_allee", "Grand Allee", 2u, 8u),
        GameEntry("fluechtig", "Flüchtig", 2u, 6u),
        GameEntry("mau_mau", "Mau-Mau", 2u, 8u),
        GameEntry("schwimmen", "Schwimmen", 2u, 8u),
        GameEntry("durak", "Durak", 2u, 6u),
    )
