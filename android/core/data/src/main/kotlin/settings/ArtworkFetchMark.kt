package data.settings

import android.content.Context

/**
 * The installed index, by its push time, whose artwork this device last
 * finished fetching — kept across starts, so an index that arrived while
 * nothing fetched for it (the app asleep when it was published, killed
 * mid-fetch, or a build that did not fetch on opening) is still caught up
 * on the next start, once, rather than waiting for a newer index or Update.
 */
interface ArtworkFetchMark {
    fun fetchedFor(): Long?

    fun mark(publishedAt: Long)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryArtworkFetchMark(private var fetched: Long? = null) : ArtworkFetchMark {
    override fun fetchedFor(): Long? = fetched

    override fun mark(publishedAt: Long) {
        fetched = publishedAt
    }
}

/** Plain preferences, as the shelf view's are: which index was fetched for is no secret. */
class SharedPreferencesArtworkFetchMark(context: Context) : ArtworkFetchMark {
    private val preferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override fun fetchedFor(): Long? = preferences.getLong(KEY_FETCHED_FOR, NONE).takeIf { it != NONE }

    override fun mark(publishedAt: Long) = preferences.edit().putLong(KEY_FETCHED_FOR, publishedAt).apply()

    private companion object {
        const val PREFS_FILE_NAME = "artwork_fetch_mark"
        const val KEY_FETCHED_FOR = "fetched_for"
        const val NONE = Long.MIN_VALUE
    }
}
