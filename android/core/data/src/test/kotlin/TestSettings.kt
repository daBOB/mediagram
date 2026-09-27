package data

import kotlinx.coroutines.runBlocking
import settings.InMemoryLibrarySettings
import settings.LibrarySettings

/** [LibrarySettings] with [handle] already chosen — what every core:data test that skips the picker starts from. */
fun settingsWithAChosenLibrary(handle: String = "a1b2c3"): LibrarySettings =
    InMemoryLibrarySettings().apply { runBlocking { write(handle) } }
