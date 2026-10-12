package data

import data.settings.LibrarySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TogetherEdit
import uniffi.mediagram_core.TogetherMessage

/**
 * Watch-together session messages (`docs/mlib-spec.md` §7b) in the chosen
 * library's channel, through the core. Only I/O: the rules are in
 * `model.together`, and what this device makes of them is [TogetherLive].
 *
 * Every call but [heard] throws while there is no core, no sign-in or no
 * chosen library, or while Telegram can't be reached.
 */
interface TogetherChannel {
    /** Session messages among the newest 100 in the channel, newest first. Never a search: it misses fresh messages. */
    suspend fun recent(): List<TogetherMessage>

    /** A message's current text; `null` once it is deleted. */
    suspend fun read(id: Int): String?

    /** Sends a new session message, never pinned, and answers its id. */
    suspend fun send(text: String): Int

    suspend fun edit(
        id: Int,
        text: String,
    ): TogetherEdit

    suspend fun delete(id: Int)

    /**
     * The together message heard, once per push. The core keeps only the
     * newest one heard and does not clear it, so two pushes can read the
     * same message; and it hears this device's own edits come back. Collecting
     * this is listening (`LibraryEvents`), so it lasts only as long as that.
     */
    fun heard(): Flow<TogetherMessage>
}

class CoreTogetherChannel(
    private val coreProvider: CoreProvider,
    private val settings: LibrarySettings,
    private val events: LibraryEvents,
) : TogetherChannel {
    // coreOrNull, not awaitCore: a device not signed in yet answers "unavailable" at once rather than waiting for ever.
    private suspend fun <T> library(call: suspend CoreInterface.(handle: String) -> T): T {
        val core = coreProvider.coreOrNull() ?: error("no core: not signed in")
        check(core.isAuthorized()) { "not signed in" }
        val handle = settings.read() ?: error("no library is chosen")
        return core.call(handle)
    }

    override suspend fun recent() = library { togetherRecent(it) }

    override suspend fun read(id: Int) = library { togetherRead(it, id) }

    override suspend fun send(text: String) = library { togetherSend(it, text) }

    override suspend fun edit(
        id: Int,
        text: String,
    ) = library { togetherEdit(it, id, text) }

    override suspend fun delete(id: Int) = library { togetherDelete(it, id) }

    override fun heard(): Flow<TogetherMessage> =
        events
            .events()
            .filter { it == LibraryEvent.TOGETHER }
            .mapNotNull { coreProvider.coreOrNull()?.togetherHeard() }
}
