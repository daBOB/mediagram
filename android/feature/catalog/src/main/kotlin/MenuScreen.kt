package catalog

/**
 * A screen the overflow menu opens, over whatever the library is showing.
 *
 * Asking for one from another is a move, not an addition: the key screen
 * asked for from the system screen replaces it rather than stacking over
 * it, so Back never lands on a menu screen the viewer has long since
 * stopped asking for.
 */
enum class MenuScreen {
    System,
    TmdbKey,
    Settings,

    /**
     * Settings opened straight to its Storage section — a film page's own
     * "Raise the cache budget" link, the same shape [System] already gives
     * a direct section link rather than the general index [Settings] opens.
     */
    Storage,
}
