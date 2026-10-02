package update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Where PackageInstaller reports how a self-update ended. */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {
    @Inject
    lateinit var updater: AppUpdater

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        updater.onInstallResult(
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            confirm = confirm,
        )
    }
}
