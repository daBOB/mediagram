package ui.tv.setup

import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `KeyboardType` never reaches the semantics tree Robolectric or the
 * instrumented set can query — this pins the override rule
 * [resolvedKeyboardType] exists for directly instead: a secret field is
 * always `Password`, regardless of what was asked for, the same as the
 * phone's own secret fields; anything else keeps the type its screen
 * chose — `Number` for `api_id`, plain `Text` where the phone does not
 * specialise it.
 */
class TvTextQuestionKeyboardTypeTest {
    @Test
    fun aSecretFieldIsAlwaysPasswordRegardlessOfTheRequestedType() {
        assertEquals(KeyboardType.Password, resolvedKeyboardType(secret = true, keyboardType = KeyboardType.Number))
        assertEquals(KeyboardType.Password, resolvedKeyboardType(secret = true, keyboardType = KeyboardType.Text))
    }

    @Test
    fun aNonSecretFieldKeepsTheNumberTypeApiIdAsksFor() {
        assertEquals(KeyboardType.Number, resolvedKeyboardType(secret = false, keyboardType = KeyboardType.Number))
    }

    @Test
    fun aNonSecretFieldDefaultsToTextTheWayEverySignInStepButThePasswordDoes() {
        assertEquals(KeyboardType.Text, resolvedKeyboardType(secret = false, keyboardType = KeyboardType.Text))
    }
}
