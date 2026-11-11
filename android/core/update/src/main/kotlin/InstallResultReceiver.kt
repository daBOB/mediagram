package update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** The application class of an app that updates itself: the receiver below reaches its updater through it. */
interface UpdaterHost {
    val updater: AppUpdater
}

/** Where PackageInstaller reports how a self-update ended. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val updater = (context.applicationContext as? UpdaterHost)?.updater ?: return
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        updater.onInstallResult(
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            confirm = confirm,
        )
    }
}
