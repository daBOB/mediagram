package catalog

/**
 * Where in the library a viewer currently is. The catalog is the root; a
 * collection is named after whatever course or show it opened; a season is
 * named after itself and is reachable from a show's collection screen, when
 * that show has more than one; a title is named after itself, and is
 * reachable from any of them; a hand-built list is named after itself and is
 * reachable from the Collections tab; the system screen sits alongside all
 * of them rather than under any.
 */
sealed interface Destination {
    data object Catalog : Destination

    data class Collection(
        val name: String,
    ) : Destination

    data class Season(
        val name: String,
    ) : Destination

    data class Title(
        val name: String,
    ) : Destination

    /** One hand-built list, opened from the Collections tab. */
    data class List(
        val name: String,
    ) : Destination

    /** Every result for one query, ranked flat rather than shelved. */
    data object Search : Destination

    /** Everything tagged with one genre: films, then series. */
    data class Genre(
        val name: String,
    ) : Destination

    /** A person's page: their portrait, and the titles this profile can see them in. */
    data class Person(
        val name: String,
    ) : Destination

    /** A film franchise's own page, opened from a collection card. */
    data class Franchise(
        val name: String,
    ) : Destination

    /** The Movies department's own paged shelf — "All N films", one step in from its front page. */
    data object MoviesPage : Destination

    /** Every genre the library holds, as a page of its own — not [Genre], which is one shelf. */
    data object Genres : Destination

    /** Films, shows and courses, newest arrival first. */
    data object Latest : Destination

    data object System : Destination

    data object TmdbKey : Destination

    data object Settings : Destination
}

/**
 * What the bar says it is showing. Read from the destination rather than
 * passed in by each screen, so the catalog, a collection and the system
 * screen cannot each spell their own title differently.
 */
fun barTitleFor(destination: Destination): String =
    when (destination) {
        Destination.Catalog -> "Mediagram"
        is Destination.Collection -> destination.name
        is Destination.Season -> destination.name
        is Destination.Title -> destination.name
        is Destination.List -> destination.name
        Destination.Search -> "Search"
        is Destination.Genre -> destination.name
        is Destination.Person -> destination.name
        is Destination.Franchise -> destination.name
        Destination.MoviesPage -> "Movies"
        Destination.Genres -> "Genres"
        Destination.Latest -> "Latest"
        Destination.System -> "System"
        Destination.TmdbKey -> "TMDB key"
        Destination.Settings -> "Settings"
    }

/**
 * Whether the bar offers a way back, and what it is called. The catalog is
 * the top of the tree; a back arrow there would either do nothing or leave
 * the app, and both are worse than no arrow.
 */
fun backLabelFor(destination: Destination): String? =
    when (destination) {
        Destination.Catalog -> null
        is Destination.Collection -> "Back"
        is Destination.Season -> "Back"
        is Destination.Title -> "Back"
        is Destination.List -> "Back"
        Destination.Search -> "Back"
        is Destination.Genre -> "Back"
        is Destination.Person -> "Back"
        is Destination.Franchise -> "Back"
        Destination.MoviesPage -> "Back"
        Destination.Genres -> "Back"
        Destination.Latest -> "Back"
        Destination.System -> "Back"
        Destination.TmdbKey -> "Back"
        Destination.Settings -> "Back"
    }

/** Whether the search action belongs in the bar — everywhere except the search screen itself. */
fun showsSearchAction(destination: Destination): Boolean = destination != Destination.Search
