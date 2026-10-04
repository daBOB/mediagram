package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/**
 * The harness every browse page's own test shares: one activity per test,
 * its content set under a plain [MaterialTheme], closed after — the same
 * steps [ShowsDepartmentScreenTest] spells out for itself.
 */
abstract class BrowsePageTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    protected fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    /** Where [text] actually laid out, unmerged — a tile's own words sit inside the tile's merged node. */
    protected fun boundsOf(text: String): DpRect = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    protected fun DpRect.contains(inner: DpRect): Boolean =
        inner.left >= left && inner.right <= right && inner.top >= top && inner.bottom <= bottom

    protected fun film(
        id: String,
        year: Int = 2000,
        addedAt: Long = 0,
        backdrop: String? = null,
        collectionId: Long? = null,
    ): MediaSet =
        MediaSet(
            setId = id, kind = Kind.MOVIE, title = "Film $id", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = year, durationSecs = 6_000,
            posterPath = null, totalBytes = 0, addedAt = addedAt, backdropPath = backdrop,
            collectionId = collectionId, collectionName = collectionId?.let { "Franchise $it" },
        )

    protected fun collection(name: String, kind: CollectionKind, addedAt: Long = 0): Entry.Collection {
        val item = MediaSet(
            setId = "$name-1", kind = if (kind == CollectionKind.SHOW) Kind.EPISODE else Kind.TUTORIAL,
            title = "Part one", show = name, chapter = null, path = null, season = 1, episodeFirst = 1,
            episodeLast = null, year = 2020, durationSecs = 1_800, posterPath = null, totalBytes = 0, addedAt = addedAt,
        )
        return Entry.Collection(
            key = "${kind.name}/$name", kind = kind, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division(name, 1, listOf(item), emptyList())),
        )
    }
}
