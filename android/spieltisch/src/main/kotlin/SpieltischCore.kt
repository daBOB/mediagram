package com.mediagram.spieltisch

import uniffi.spieltisch_core.MediaImages
import uniffi.spieltisch_core.NightUrls
import uniffi.spieltisch_core.SpieltischConfig

/** The native core's night, behind an interface so the screen's tests need no native library. */
interface SpieltischCore {
    /** Hosts a night, ending any night already running first; [images] fetches its titles' images. */
    suspend fun startNight(
        config: SpieltischConfig,
        images: MediaImages,
    ): NightUrls

    /** True when a game was running and the room is back in the lobby. */
    suspend fun nightBack(): Boolean

    /** Ends the night; nothing to do when none runs. */
    suspend fun stopNight()
}

/** The generated bindings over libspieltisch_core.so. */
object NativeSpieltischCore : SpieltischCore {
    override suspend fun startNight(
        config: SpieltischConfig,
        images: MediaImages,
    ): NightUrls = uniffi.spieltisch_core.startNight(config, images)

    override suspend fun nightBack(): Boolean = uniffi.spieltisch_core.nightBack()

    override suspend fun stopNight() = uniffi.spieltisch_core.stopNight()
}
