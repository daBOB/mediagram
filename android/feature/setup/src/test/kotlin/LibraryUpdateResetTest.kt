package setup

import data.LibraryUpdateKind
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The library update is app-wide, so leaving an account must take its library with it. */
class LibraryUpdateResetTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private suspend fun aReadLibrary(): SetupFixture =
        SetupFixture(core = FakeCore(authorized = true)).signedIn().apply {
            library.write("films")
            libraryUpdate.update(LibraryUpdateKind.Read)
            assertNotNull(libraryUpdate.library.value)
        }

    @Test
    fun startingOverForgetsTheLibraryItHadRead() =
        runTest {
            val fixture = aReadLibrary()
            fixture.viewModel().startOver()
            assertNull(fixture.libraryUpdate.library.value)
        }

    @Test
    fun signingOutForgetsTheLibraryItHadRead() =
        runTest {
            val fixture = aReadLibrary()
            fixture.settingsViewModel().signOut()
            assertNull(fixture.libraryUpdate.library.value)
        }
}
