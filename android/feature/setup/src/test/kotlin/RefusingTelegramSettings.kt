package setup

import settings.TelegramCredentials
import settings.TelegramSettings

/**
 * A store that will not answer, standing in for the keystore refusing after
 * a backup restore onto another device or after the key behind it has been
 * invalidated. `SecurityException` is what androidx.security actually
 * throws there, and every question setup asks goes through this store —
 * which is why the failure has to land somewhere a person can act on.
 */
class RefusingTelegramSettings : TelegramSettings {
    override suspend fun read(): TelegramCredentials? = throw SecurityException("keystore unavailable")

    override suspend fun write(
        apiId: Int,
        apiHash: String,
    ): Unit = throw SecurityException("keystore unavailable")

    override suspend fun clear(): Unit = throw SecurityException("keystore unavailable")
}
