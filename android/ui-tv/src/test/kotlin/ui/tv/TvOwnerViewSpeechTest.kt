package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.managingAs
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * What a screen reader hears of the owner's view on television ([TvProfilesHarness]) over
 * [twoHouseholds] with Mia, Bo's kid, at Home: one name for each row, as the web gives it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvOwnerViewSpeechTest : TvProfilesHarness() {
    private fun ownersView() {
        launch(emptyList(), chosen = null) {
            twoHouseholds()(this)
            profiles = profiles + CoreProfile("m", "Mia", kids = true, kidsAge = 6u, parentId = "b")
        }
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        pin("1234")
        compose.onNodeWithText(managingAs("andre")).assertExists()
    }

    /** Each household's Add someone is "Add someone to Home", once — not that and its words again. */
    @Test
    fun addSomeoneIsNamedOnceForItsHousehold() {
        ownersView()
        compose
            .onNode(hasContentDescription("${HouseholdWords.ADD_SOMEONE_TO} Home") and hasClickAction())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    /** Mia is not the owner's to change: no control, but her name and line are one thing said. */
    @Test
    fun aReadOnlyRowIsReadAsOne() {
        ownersView()
        compose.onNode(hasText("Mia") and hasText("Kid · Bo · FSK 6") and !hasClickAction()).assertExists()
    }
}
