package setup

import settings.LibrarySettings

/**
 * A library store that answers but will not be written to, which is what
 * the keystore does when the key behind it has been invalidated while the
 * app was running.
 *
 * Refusing only the write is what makes the test worth having: the flow
 * gets all the way to a chosen library before anything goes wrong, so the
 * failure has to be told apart from a channel that has nothing pinned in
 * it. No amount of picking differently gets past this one.
 */
class RefusingLibrarySettings : LibrarySettings {
    override suspend fun read(): String? = null
    override suspend fun write(handle: String): Unit = throw SecurityException("keystore unavailable")
    override suspend fun clear(): Unit = throw SecurityException("keystore unavailable")
}
