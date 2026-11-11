package update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.nio.file.Files

/** An updater over a scripted source and a recording installer, installed at versionCode 92002. */
internal abstract class UpdaterTestBase {
    protected val dir: File = Files.createTempDirectory("updates").toFile()
    protected val source = FakeSource()
    protected var playing = false
    protected val installer = RecordingInstaller()

    protected fun updater(enabled: Boolean = true): AppUpdater =
        AppUpdater(
            UpdateConfig(enabled = enabled, installedVersionCode = 92_002, updatesDir = dir),
            source,
            { playing },
            installer,
            CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        ).apply { now = { 1_000_000L } }
}

internal class SignedOut : Exception()

/** A source that is told what to answer, and writes `1, 2, 3` when a release is downloaded. */
internal class FakeSource : ReleaseSource {
    var latest: Lookup = Lookup.Found(release(93_000L))
    var lookupFailure: Exception? = null
    var downloadFailure: Exception? = null
    var downloadGate: CompletableDeferred<Unit>? = null
    var asked = 0
    val downloadedPaths = mutableListOf<String>()

    fun release(
        code: Long,
        name: String = "0.93.0",
    ) = Release(name, code) { apk ->
        downloadedPaths += apk.path
        downloadFailure?.let { throw it }
        downloadGate?.await()
        apk.writeBytes(byteArrayOf(1, 2, 3))
    }

    override suspend fun latest(): Lookup {
        asked++
        lookupFailure?.let { throw it }
        return latest
    }

    override fun sentence(failure: Throwable): String? =
        if (failure is SignedOut) "this device was signed out of Telegram" else null
}

internal class RecordingInstaller : ApkInstaller {
    var refusal: String? = null
    val installed = mutableListOf<File>()
    var installFailure: Exception? = null
    var attempts = 0

    override fun refusal(apk: File): String? = refusal

    override fun install(apk: File) {
        attempts++
        installFailure?.let { throw it }
        installed += apk
    }
}
