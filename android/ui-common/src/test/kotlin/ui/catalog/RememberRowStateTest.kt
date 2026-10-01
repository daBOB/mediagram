package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * A newest-first row refreshed while the viewer looks at it. On the tablet
 * the Series "New episodes" row kept opening on Bones while Seinfeld and
 * Boston Legal, newer, sat scrolled off to its left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RememberRowStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val shows = mutableStateOf(listOf("Bones", "Babylon Berlin", "Alien: Earth", "A Discovery of Witches", "21 Jump Street", "The Deuce"))
    private lateinit var state: LazyListState
    private var focused = false

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(nested: Boolean = false) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                // Every real row sits in a page's own lazy column, which
                // composes its items during its own measure.
                if (nested) {
                    LazyColumn { item(key = "row") { KeptRow() } }
                } else {
                    KeptRow()
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun KeptRow() {
        state = rememberRowState(shows.value, inUse = { focused })
        Row(shows.value, state)
    }

    private fun arrive(vararg newer: String) {
        compose.runOnUiThread { shows.value = newer.toList() + shows.value }
        compose.waitForIdle()
    }

    private fun firstShown(): Any? = compose.runOnIdle { state.layoutInfo.visibleItemsInfo.first().key }

    @Test
    fun aRowAtItsStartShowsWhatArrivesInFront() {
        show()
        arrive("Seinfeld", "Boston Legal")
        assertEquals("Seinfeld", firstShown())
    }

    @Test
    fun aRowInsideAPageShowsWhatArrivesInFront() {
        show(nested = true)
        arrive("Seinfeld", "Boston Legal")
        assertEquals("Seinfeld", firstShown())
    }

    @Test
    fun aRowHoldingFocusKeepsItsPlaceWhenSomethingArrivesInFront() {
        focused = true
        show()
        arrive("Seinfeld")
        assertEquals("Bones", firstShown())
    }

    @Test
    fun aRowScrolledIntoKeepsItsPlaceWhenSomethingArrivesInFront() {
        show()
        compose.runOnUiThread { state.requestScrollToItem(2) }
        compose.waitForIdle()
        arrive("Seinfeld")
        assertEquals("Alien: Earth", firstShown())
    }
}

@Composable
private fun Row(
    shows: List<String>,
    state: LazyListState,
) {
    LazyRow(state = state, modifier = Modifier.width(300.dp)) {
        items(shows, key = { it }) { BasicText(it, Modifier.width(100.dp)) }
    }
}
