package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.Entry
import catalog.HomeRow
import catalog.RowContent
import model.Kind
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TvHome]'s focus: it lands on arrival, and then it stays wherever the
 * viewer moved it — rows arriving above while the viewer browses, as
 * Continue does the moment a fetch or a play finishes, move the plates but
 * never the remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeStateTest : TvScreenStateTest() {
    private val latest = row("Latest films", films(3).reversed())

    @Test
    fun aRowArrivingAboveDoesNotPullTheRemoteBackToTheRestoredPlate() {
        val rows = mutableStateOf(listOf(latest))
        show { TvHome(rows = rows.value, watch = WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onSeeAll = {}, restoreKey = "film-0") }
        compose.onNodeWithText("Film 0").assertIsFocused()

        compose.onNodeWithText("Film 1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnUiThread { rows.value = listOf(row("Continue", listOf(set("other", Kind.MOVIE, "Other", addedAt = 9))), latest) }
        compose.waitForIdle()

        compose.onNodeWithText("Film 1").assertIsFocused()
    }

    private fun row(
        title: String,
        sets: List<model.MediaSet>,
    ) = HomeRow(title = title, seeAll = null, total = sets.size, content = RowContent.Entries(sets.map(Entry::Film)))
}
