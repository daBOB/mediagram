package player

/**
 * As [ellipsize], but a title made of parts ("Show · S1E4", the name an
 * episode is queued under) keeps its last part whole and shortens the ones
 * before it: cutting the end would drop the episode code, the one part that
 * tells one episode of a show from the next.
 */
internal fun ellipsizeKeepingLastPart(title: String, maxLength: Int): String {
    if (title.length <= maxLength || PART_SEPARATOR !in title) return ellipsize(title, maxLength)
    val last = title.substringAfterLast(PART_SEPARATOR)
    val head = title.substringBeforeLast(PART_SEPARATOR)
    return ellipsize(head, (maxLength - last.length - PART_SEPARATOR.length).coerceAtLeast(1)) + PART_SEPARATOR + last
}

private const val PART_SEPARATOR = " · "
