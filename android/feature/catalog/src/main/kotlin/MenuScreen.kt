package catalog

/**
 * A screen the overflow menu opens, over whatever the library is showing,
 * and the name the bar gives it.
 *
 * Asking for one from another is a move, not an addition: the key screen
 * asked for from the system screen replaces it rather than stacking over
 * it, so Back never lands on a menu screen the viewer has long since
 * stopped asking for.
 */
enum class MenuScreen(
    val destination: Destination,
) {
    System(Destination.System),
    TmdbKey(Destination.TmdbKey),
    Settings(Destination.Settings),
}
