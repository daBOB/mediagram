package setup

/**
 * One library as the picker shows it.
 *
 * A [title] to render and a [handle] to send back, and deliberately nothing
 * else. The handle is a random name the core minted for the channel, not
 * the channel's identifier and not computed from one, so a surface holding
 * this learns what it may read and never where the bytes live — the rule
 * the byte path is held to, kept at the same line.
 */
data class LibraryOption(val handle: String, val title: String)
