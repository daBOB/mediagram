package playback

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The `Authorization` header's scheme — matches `mediagram_cache`'s `token::SCHEME`. */
const val LAN_AUTH_SCHEME = "MGC1"

/**
 * `HMAC-SHA256(token, "PUT\n{path}\n{total}\n" + hex(sha256(body)))`, hex
 * encoded — byte-for-byte what `mediagram_cache::token::sign` computes.
 *
 * The HMAC key is [token]'s 64 ASCII bytes exactly as printed — the hex
 * *string*, never decoded to the 32 bytes it represents. Decoding it first
 * silently produces a different, wrong, signature; there is no way for a
 * server checking against the real key to tell that apart from a bad
 * pairing, so this is the one line in this file the shared test vector
 * exists to pin down.
 */
internal fun sign(
    token: String,
    method: String,
    path: String,
    total: Long,
    body: ByteArray,
): String {
    val canonical = "$method\n$path\n$total\n${sha256Hex(body)}"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(token.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
    return mac.doFinal(canonical.toByteArray(Charsets.US_ASCII)).toLowerHex()
}

/**
 * Pulls the three numeric fields this app cares about straight out of
 * `GET /v1/status`'s flat JSON body with regex rather than a JSON library —
 * this server's own response shape is fixed and controlled by this same
 * codebase (`crates/mediagram-cache/src/http.rs`'s `status` handler), so a
 * general-purpose parser would buy nothing but a new dependency. `null` if
 * any of the three is missing or not a plain non-negative integer.
 */
internal fun statusFromJson(body: String): LanServerStatus? {
    val held = longField(body, "held_bytes") ?: return null
    val budget = longField(body, "budget_bytes") ?: return null
    val chunks = longField(body, "chunks") ?: return null
    return LanServerStatus(held, budget, chunks)
}

private fun longField(
    body: String,
    name: String,
): Long? = Regex(""""$name"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toLongOrNull()

private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

private fun ByteArray.toLowerHex(): String = joinToString("") { "%02x".format(it) }
