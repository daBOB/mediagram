package data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which people this session has already asked [CatalogRepository.fetchPortrait]
 * for — once per person per session, the phase's own rule: a cast row or a
 * person page asks at most once, even recomposed or reopened many times
 * while the app process stays alive. A device restart is a new session and
 * asks again, the same as a device that never asked at all.
 */
@Singleton
class PortraitRequestLog
    @Inject
    constructor() {
        private val asked = ConcurrentHashMap.newKeySet<Long>()

        /** Whether this is the first time this session has asked for [personId]'s portrait. */
        fun shouldRequest(personId: Long): Boolean = asked.add(personId)
    }
