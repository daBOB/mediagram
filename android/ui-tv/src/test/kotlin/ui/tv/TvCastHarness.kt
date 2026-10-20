package ui.tv

import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import data.LiveSession
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
import model.Profile
import model.together.CastState
import model.together.Member
import model.together.Playback
import model.together.offered
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import ui.tv.catalog.set
import ui.tv.player.TvPlayerScreenTag
import ui.tv.player.TvPlayerTitleTag
import java.time.Duration

/**
 * What every cast test opens on: the whole app over a [TvAppFixture] whose
 * household is Ada (the TV's own profile, unless a test opens on the picker),
 * Bo, a grown-up who casts, and Kim, a kid with a daily limit — and the moves
 * a test makes: a cast from the tablet, the cast lost to another TV, the
 * remote's keys and Back, and time passing.
 */
abstract class TvCastHarness {
    @get:Rule val compose = createEmptyComposeRule()
    internal lateinit var fixture: TvAppFixture
    internal lateinit var controller: ActivityController<TvAppTestActivity>
    private val tablet = Member("tab", "Tablet")
    private var requests = 900

    @Before
    fun stubHilt() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    internal fun launch(chosen: String? = "ada") {
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(ADA, BO, KIM), chosen, library())
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    /** The tablet asks the TV to play [set] as [profile]; [replace] is its answer to the TV being busy. */
    internal fun cast(
        set: String,
        profile: String = "bo",
        replace: Boolean = false,
    ) {
        val offer = offered("s-$set", tablet, Playback(set, playing = false, pos = 0.0, rate = 1.0), profile, "home", System.currentTimeMillis())
        val asked = if (replace) offer.copy(cast = offer.cast?.copy(state = CastState.REPLACE)) else offer
        compose.runOnUiThread { fixture.casts.fake.requests.value = LiveSession(requests++, asked) }
        compose.waitForIdle()
    }

    internal fun lose() {
        compose.runOnUiThread { fixture.casts.lose() }
        compose.waitForIdle()
    }

    internal fun playing(title: String) {
        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()
        compose.onNode(hasTestTag(TvPlayerTitleTag) and hasText(title, substring = true)).assertExists()
    }

    /** Opens [title]'s page from Home and plays it from there, as a viewer does. */
    internal fun playFromItsPage(title: String) {
        open(hasContentDescription(title) and hasClickAction())
        compose.onNodeWithText("▶ Play").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        playing(title)
    }

    internal fun open(card: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(card)
        compose.onNode(card).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    internal fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    internal fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    internal fun wait(time: Duration) {
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(time) }
        compose.waitForIdle()
    }

    internal companion object {
        val ADA = Profile("ada", "Ada")
        val BO = Profile("bo", "Bo")
        val KIM = Profile("kim", "Kim", kids = true, kidsAge = 12, dailyLimit = 30)

        /** Only Home draws the cover, and its Watch now with it. */
        val WatchNow = hasContentDescription("Watch now: ", substring = true)

        /** Three films with art enough for the cover, a show of two episodes any child may watch, and one of two no child is shown. */
        fun library() =
            (0 until 3).map {
                set("film-$it", Kind.MOVIE, "Film $it", addedAt = it.toLong()).copy(backdropPath = "/b-$it.jpg", posterPath = "/p-$it.jpg")
            } +
                (1..2).map {
                    set("e$it", Kind.EPISODE, if (it == 1) "Pilot" else "Second", show = "A Show", addedAt = 10L + it, episode = it).copy(season = 1, fsk = "0")
                } +
                (1..2).map {
                    set("l$it", Kind.EPISODE, "Late $it", show = "A Late Show", addedAt = 20L + it, episode = it).copy(season = 1, fsk = "16")
                }
    }
}
