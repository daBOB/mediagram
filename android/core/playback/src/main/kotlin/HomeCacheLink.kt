package playback

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
 * over, a server found or lost, a token saved — and a lost server clears it, so
 * the core never waits on a box that has gone.
 */
fun CoroutineScope.linkHomeCache(
    cores: Flow<CoreInterface?>,
    server: Flow<LanServer?>,
    tokenSaves: Flow<Int>,
    token: suspend () -> String?,
): Job =
    launch {
        combine(cores.filterNotNull(), server, tokenSaves) { core, found, _ -> core to found }.collect { (core, found) ->
            val paired = found?.let { orDefault(null, "reading the LAN cache token") { token() } }
            orDefault(Unit, "naming the home cache server to the core") { core.setHomeCache(found?.baseUrl, paired) }
        }
    }
