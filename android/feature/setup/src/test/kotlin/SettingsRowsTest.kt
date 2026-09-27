package setup

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRowsTest {
    /** A row still being asked says so; it is never blank, which would read as "nothing here". */
    @Test
    fun rowsStillBeingAskedSayEllipsis() {
        assertEquals(
            listOf("Account" to "…", "Library" to "…", "Datacenter" to "…", "Session" to "…"),
            telegramRows(SettingsUiState()),
        )
    }

    @Test
    fun answeredRowsSayTheirAnswers() {
        val state =
            SettingsUiState(
                account = "A Viewer (@viewer)",
                library = "Mediagram",
                datacenter = "DC 4",
                connection = "Signed in; Telegram answered",
            )
        assertEquals(
            listOf(
                "Account" to "A Viewer (@viewer)",
                "Library" to "Mediagram",
                "Datacenter" to "DC 4",
                "Session" to "Signed in; Telegram answered",
            ),
            telegramRows(state),
        )
    }

    @Test
    fun theIndexRowReadsBeforeTheFirstReplyIsStillWaiting() {
        assertEquals("Reading…", telegramStatus(SettingsUiState()))
    }

    @Test
    fun theIndexRowNamesWhoIsSignedIn() {
        val state = SettingsUiState(account = "A Viewer (@viewer)", connection = "Signed in; Telegram answered")
        assertEquals("A Viewer (@viewer) · signed in", telegramStatus(state))
    }

    @Test
    fun theIndexRowSaysNotSignedInWhenTelegramDidNotAnswer() {
        val state = SettingsUiState(account = "—", connection = "Telegram did not answer")
        assertEquals("Not signed in", telegramStatus(state))
    }
}
