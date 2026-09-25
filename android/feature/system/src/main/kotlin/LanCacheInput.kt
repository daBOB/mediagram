package system

import java.net.MalformedURLException
import java.net.URL

/**
 * What a viewer typed into the manual address field, turned into what
 * [playback.LanCacheSettings] actually stores. Blank clears the override.
 * Anything else gets `http://` prepended when it has no scheme of its own
 * — `avahi-browse` and the server's own status line both print a bare
 * `host:port`, and that is exactly what a viewer copies in — and is
 * refused with a sentence to show rather than saved and left to crash
 * discovery later the first time it is actually opened as a URL.
 */
internal fun normalizeManualAddress(raw: String): Result<String?> {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return Result.success(null)
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    return try {
        URL(withScheme)
        Result.success(withScheme)
    } catch (e: MalformedURLException) {
        Result.failure(IllegalArgumentException("Could not understand that address."))
    }
}

/**
 * `ACCESS_LOCAL_NETWORK` is enforced from API 37 (Android 17) onward and
 * does not exist as a platform permission below it —
 * `ContextCompat.checkSelfPermission` for an unknown permission name
 * answers `DENIED` there regardless, which would otherwise show "Needs
 * local network permission" on every older device, with no prompt that
 * could ever satisfy it. A plain function of [sdkInt] and [granted]
 * because Robolectric (as of this project's version) has no shadow for
 * API 37 to exercise [system.LanCacheViewModel]'s own check against.
 */
internal fun localNetworkPermissionGranted(
    sdkInt: Int,
    granted: Boolean,
): Boolean = sdkInt < 37 || granted

private val TOKEN_PATTERN = Regex("^[0-9a-f]{64}$")

/** The token exactly as `mediagram_cache token` prints it: 64 lowercase hex characters, trimmed of surrounding whitespace. */
internal fun normalizePairingToken(raw: String): Result<String> {
    val trimmed = raw.trim()
    return if (TOKEN_PATTERN.matches(trimmed)) {
        Result.success(trimmed)
    } else {
        Result.failure(IllegalArgumentException("That does not look like a pairing token — it should be 64 lowercase hex characters."))
    }
}
