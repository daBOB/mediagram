package data

import data.settings.InMemoryLibrarySettings
import data.settings.LibrarySettings
import kotlinx.coroutines.runBlocking

/** [LibrarySettings] with [handle] already chosen — what every core:data test that skips the picker starts from. */
fun settingsWithAChosenLibrary(handle: String = "a1b2c3"): LibrarySettings =
    InMemoryLibrarySettings().apply { runBlocking { write(handle) } }
