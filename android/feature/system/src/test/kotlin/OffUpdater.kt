package system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import settings.InMemoryLibrarySettings
import testing.FakeCore
import testing.FakeCoreProvider
import update.AppUpdater
import update.ApkInstaller
import update.UpdateConfig
import java.io.File

/** An updater of a build that never updates itself: its status stays `Off`, so the Updates row is hidden. */
fun offUpdater(): AppUpdater =
    AppUpdater(
        UpdateConfig(enabled = false, installedVersionCode = 1, updatesDir = File("unused")),
        FakeCoreProvider(FakeCore()),
        InMemoryLibrarySettings(),
        { false },
        object : ApkInstaller {
            override fun refusal(apk: File): String? = null

            override fun install(apk: File) = Unit
        },
        CoroutineScope(Dispatchers.Unconfined),
    )
