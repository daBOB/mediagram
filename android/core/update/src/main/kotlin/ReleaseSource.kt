package update

import java.io.File

/**
 * A release an app can install. [download] puts its APK at the file it is
 * given, and leaves nothing there unless the bytes are the release's own.
 */
class Release(
    val versionName: String,
    val versionCode: Long,
    val download: suspend (apk: File) -> Unit,
)

/** What a [ReleaseSource] found when asked. */
sealed interface Lookup {
    /** The source could not be asked now. With a [reason] the updater says so; without, it just asks again at the next foreground. */
    data class Unavailable(val reason: String? = null) : Lookup

    /** The newest [release], or `null` when none is published. */
    data class Found(val release: Release?) : Lookup
}

/** Where an app's newest release comes from and how it is fetched: the media app's from its library channel, Spieltisch's from the media app. */
fun interface ReleaseSource {
    suspend fun latest(): Lookup

    /** A sentence a person can act on for a failure of [latest] or a download, or `null` for one that is not. */
    fun sentence(failure: Throwable): String? = null
}
