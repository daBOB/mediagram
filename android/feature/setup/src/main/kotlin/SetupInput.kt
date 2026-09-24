package setup

/*
 * What the two typed-in values have to look like before anything is
 * stored, and the sentences shown when they do not.
 *
 * These reject the obviously wrong, not the merely unlucky: a wrong-but
 * well-formed api hash cannot be told apart from a right one without
 * asking Telegram, and finding out that way is the next screen's job. What
 * is caught here is the shape — a transposed digit count, a pasted
 * fragment — which would otherwise be stored and then fail much later,
 * somewhere that cannot say why.
 *
 * No message ever quotes what was typed. A malformed api hash is still key
 * material, and an error string is the easiest place for one to escape.
 */

/** Telegram issues the api hash as exactly 32 hexadecimal characters. */
private val API_HASH_SHAPE = Regex("[0-9a-f]{32}")

internal const val API_ID_ERROR = "The api_id is the number shown next to your application on my.telegram.org."

internal const val API_HASH_ERROR = "The api_hash is 32 hexadecimal characters, copied from my.telegram.org."

fun apiIdOrNull(typed: String): Int? = typed.trim().toIntOrNull()?.takeIf { it > 0 }

fun apiHashOrNull(typed: String): String? = typed.trim().lowercase().takeIf(API_HASH_SHAPE::matches)
