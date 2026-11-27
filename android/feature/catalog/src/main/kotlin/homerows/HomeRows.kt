package homerows

import catalog.continueWall
import catalog.profileShelves
import catalog.profileViewOf
import catalog.watchlistWall
import model.MediaSet
import model.Profile
import model.WatchSnapshot

/** Who is watching on this TV, as the home screen names them. */
data class HomePerson(
    val id: String,
    val name: String,
    val kids: Boolean,
)

/**
 * One card on the home screen. [positionSecs], [durationSecs] and [updatedAt]
 * are the resume point, set on Continue cards only.
 */
data class HomeTitle(
    val setId: String,
    val kind: String,
    val title: String,
    val show: String?,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    val positionSecs: Double? = null,
    val durationSecs: Double? = null,
    val updatedAt: Long? = null,
)

/** What the TV home screen shows for [person]: their Continue and My List, nothing they may not see. */
data class HomeRows(
    val person: HomePerson?,
    val continuing: List<HomeTitle>,
    val myList: List<HomeTitle>,
) {
    /** The titles whose pictures the home screen may ask for: exactly the ones on its rows. */
    val imageIds: Set<String> get() = (continuing + myList).mapTo(mutableSetOf(), HomeTitle::setId)

    companion object {
        val None = HomeRows(null, emptyList(), emptyList())
    }
}

/**
 * [person]'s rows over the library's [sets] (before any profile's filter) and [watch], their own
 * snapshot. The same filter and the same walls the catalog shows them, so a kid's home screen
 * holds nothing the kid's library would not.
 */
fun homeRowsOf(
    person: Profile?,
    sets: List<MediaSet>,
    watch: WatchSnapshot,
): HomeRows {
    if (person == null) return HomeRows.None
    val shelves = profileShelves(profileViewOf(person, watch), sets)
    val positions = watch.progress.associateBy { it.setId }
    val continuing =
        continueWall(shelves, watch).map { set ->
            val at = positions.getValue(set.setId)
            set.toHomeTitle().copy(positionSecs = at.at, durationSecs = at.duration, updatedAt = at.updatedAt)
        }
    return HomeRows(
        person = HomePerson(person.id, person.name, person.kids),
        continuing = continuing,
        myList = watchlistWall(shelves, watch).map { it.toHomeTitle() },
    )
}

private fun MediaSet.toHomeTitle() = HomeTitle(setId, kind.name, title, show, season, episodeFirst, year)
