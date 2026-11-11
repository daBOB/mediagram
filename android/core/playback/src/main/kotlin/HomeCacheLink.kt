package playback

import data.HomeCacheServer
import data.orDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import uniffi.mediagram_core.CoreInterface

/**
 * Keeps the core told which home cache server this device found and the
 * pairing token it holds, so artwork and TMDB answers are read from the same
 * box the chunks come from. Every change reaches it — a new core after a start
 * over, a server found or lost, the LAN cache switched or a token saved — and a
 * lost or switched-off server clears it, so the core never waits on a box it
 * should not use. [home] records what the core was last told, for the
 * enrichment fetch to read.
 */
fun CoroutineScope.linkHomeCache(
    cores: Flow<CoreInterface?>,
    server: Flow<LanServer?>,
    settingsChanges: Flow<Int>,
    home: HomeCacheServer,
    enabled: suspend () -> Boolean,
    token: suspend () -> String?,
): Job =
    launch {
        combine(cores.filterNotNull(), server, settingsChanges) { core, found, _ -> core to found }.collect { (core, found) ->
            val usable = found?.takeIf { orDefault(false, "reading the LAN cache switch") { enabled() } }
            val paired = usable?.let { orDefault(null, "reading the LAN cache token") { token() } }
            val told =
                orDefault(false, "naming the home cache server to the core") {
                    core.setHomeCache(usable?.baseUrl, paired)
                    true
                }
            if (told) home.linked(usable != null)
        }
    }
