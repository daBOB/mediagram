package data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which people this session has already asked [CatalogRepository.fetchPortrait]
 * for — at most once per person per session: a cast row or a person page
 * asks once, even recomposed or reopened many times while the app process
 * stays alive. A device restart is a new session and asks again, the same
 * as a device that never asked at all.
 *
 * This only reserves; `ui.catalog.rememberPortrait` (the sole caller) is
 * what decides whether a reservation whose fetch was cut short — a screen
 * left mid-request — gets tried again later in the same session.
 */
@Singleton
class PortraitRequestLog
    @Inject
    constructor() {
        private val asked = ConcurrentHashMap.newKeySet<Long>()

        /** Whether this is the first time this session has asked for [personId]'s portrait. */
        fun shouldRequest(personId: Long): Boolean = asked.add(personId)
    }
