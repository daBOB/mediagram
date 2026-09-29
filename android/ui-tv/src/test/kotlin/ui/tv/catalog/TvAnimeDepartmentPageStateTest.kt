package ui.tv.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import catalog.AnimeLibrary
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.animeDepartmentOf
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TvAnimeDepartmentPage] over real [animeDepartmentOf] output — replaces
 * the plain-wall check this shelf used to need (`theAnimeShelfIsAPlainWallThatKeepsBothItsShowAndItsFilm`,
 * `TvDepartmentPagesStateTest.kt`): the department page now keeps both a
 * show and a film, each under its own heading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1920dp-h1080dp")
class TvAnimeDepartmentPageStateTest : TvScreenStateTest() {
    private fun show(name: String) =
        Entry.Collection(
            key = "ANIME/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1,
            divisions = listOf(Division(name, 1, listOf(set("$name-e1", Kind.EPISODE, "E1", show = name, addedAt = 0, episode = 1)), emptyList())),
        )

    private fun film(id: String, addedAt: Long = 0) = set(id, Kind.MOVIE, id, addedAt = addedAt)

    @Test
    fun aShowDrawsUnderSeriesAndAFilmUnderFilms() {
        val library = AnimeLibrary(shows = listOf(show("Dragonball")), films = listOf(film("your-name")))
        val dept = animeDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!

        show { TvAnimeDepartmentPage(dept, WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onPlay = {}) }

        compose.onNodeWithText("Series").assertIsDisplayed()
        compose.onNodeWithText("Dragonball").assertIsDisplayed()
        compose.onNodeWithText("Films").assertIsDisplayed()
        compose.onNodeWithText("your-name").assertIsDisplayed()
    }

    @Test
    fun aShowOpensTheShowAndAFilmOpensItsTitlePageNeverPlayingDirectly() {
        val library = AnimeLibrary(shows = listOf(show("Dragonball")), films = listOf(film("your-name")))
        val dept = animeDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!
        var openedTitle: String? = null
        var openedCollection: String? = null
        var played: String? = null

        show {
            TvAnimeDepartmentPage(
                dept, WatchSnapshot.Empty,
                onOpenTitle = { openedTitle = it }, onOpenCollection = { openedCollection = it }, onPlay = { played = it },
            )
        }
        compose.onNodeWithText("your-name").performSemanticsAction(SemanticsActions.OnClick)

        assert(openedTitle == "your-name") { "expected the film to open its title page, got $openedTitle" }
        assert(played == null) { "the film must never play directly, got played=$played" }
        assert(openedCollection == null)
    }

    @Test
    fun continueWatchingWinsArrivalOverTheWallWhenItHasCards() {
        val continuing = film("resuming")
        val library = AnimeLibrary(shows = listOf(show("Dragonball")), films = listOf(continuing, film("your-name", addedAt = 1)))
        val dept = animeDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!.copy(continuing = listOf(continuing))

        show { TvAnimeDepartmentPage(dept, WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onPlay = {}) }

        compose.onNodeWithText("resuming").assertIsFocused()
    }

    /** [TvWall]'s own plate-0 default never outranks an explicit restore key naming a title further down. */
    @Test
    fun aRestoreKeyNamingAFilmWinsOverTheWallsOwnFirstPlateDefault() {
        val library = AnimeLibrary(shows = listOf(show("Dragonball")), films = listOf(film("your-name"), film("weathering", addedAt = 1)))
        val dept = animeDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!

        show { TvAnimeDepartmentPage(dept, WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onPlay = {}, restoreKey = "weathering") }

        compose.onNodeWithText("weathering").assertIsFocused()
    }
}
