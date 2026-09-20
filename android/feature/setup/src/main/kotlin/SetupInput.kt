package setup

/**
 * What the four typed-in values have to look like before anything is
 * stored, and the sentences shown when they do not.
 *
 * These reject the obviously wrong, not the merely unlucky: a wrong-but
 * well-formed api hash or package key cannot be told apart from a right one
 * without asking Telegram or fetching the package, and finding out that way
 * is the next screen's job. What is caught here is the shape — a
 * transposed digit count, a pasted fragment, a URL with no scheme — which
 * would otherwise be stored and then fail much later, somewhere that
 * cannot say why.
 *
 * No message ever quotes what was typed. A malformed api hash is still key
 * material, and an error string is the easiest place for one to escape.
 */

/** Telegram issues the api hash as exactly 32 hexadecimal characters. */
private val API_HASH_SHAPE = Regex("[0-9a-f]{32}")

/**
 * The package key is 32 bytes in standard base64, which is always 43
 * characters of alphabet plus one `=` of padding. Matched rather than
 * decoded: `java.util.Base64` needs a higher minimum API than this app
 * sets, and the shape is exact enough that decoding would add nothing.
 */
private val PACKAGE_KEY_SHAPE = Regex("[A-Za-z0-9+/]{43}=")

internal const val API_ID_ERROR = "The api_id is the number shown next to your application on my.telegram.org."

internal const val API_HASH_ERROR = "The api_hash is 32 hexadecimal characters, copied from my.telegram.org."

internal const val PACKAGE_URL_ERROR = "The library address is the full https:// address of the published latest.json."

internal const val PACKAGE_KEY_ERROR = "The library key is 32 bytes in base64 — 44 characters, ending in '='."

internal fun apiIdOrNull(typed: String): Int? = typed.trim().toIntOrNull()?.takeIf { it > 0 }

internal fun apiHashOrNull(typed: String): String? =
    typed.trim().lowercase().takeIf(API_HASH_SHAPE::matches)

/**
 * Stored exactly as typed beyond the trim: the core fetches this address
 * itself and resolves the package files relative to it, so a slash removed
 * here would change which files it goes on to ask for.
 */
internal fun packageUrlOrNull(typed: String): String? {
    val trimmed = typed.trim()
    val scheme = listOf("https://", "http://").firstOrNull(trimmed::startsWith) ?: return null
    return trimmed.takeIf { it.length > scheme.length }
}

internal fun packageKeyOrNull(typed: String): String? =
    typed.trim().takeIf(PACKAGE_KEY_SHAPE::matches)
