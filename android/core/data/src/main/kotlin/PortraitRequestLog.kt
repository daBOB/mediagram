package data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which people this session has finished asking [CatalogRepository.fetchPortrait]
 * about, and the portraits those fetches found — so a cast row or a person
 * page asks at most once per person while the app process stays alive,
 * however often it recomposes or reopens. A device restart is a new session
 * and asks again, the same as a device that never asked at all.
 *
 * A fetch counts as finished once it answered, with a portrait or without
 * one, or failed; one cut short (a screen left mid-request) never finishes,
 * so the next card to ask fetches again.
 */
@Singleton
class PortraitRequestLog
    @Inject
    constructor() {
        private val finished = ConcurrentHashMap.newKeySet<Long>()
        private val paths = ConcurrentHashMap<Long, String>()

        /** Whether [personId]'s portrait still has to be fetched: no fetch for them has finished this session. */
        fun needsFetch(personId: Long): Boolean = personId !in finished

        /** The portrait a finished fetch found for [personId], or `null` when none did (or none has finished). */
        fun pathOf(personId: Long): String? = paths[personId]

        /** Records that [personId]'s fetch finished, keeping [path] when it found one. */
        fun finish(personId: Long, path: String?) {
            if (path != null) paths[personId] = path
            finished += personId
        }
    }
