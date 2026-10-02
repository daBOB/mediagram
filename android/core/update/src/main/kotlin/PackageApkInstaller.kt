package update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/** [ApkInstaller] over PackageInstaller: one session, no prompt on Android 12+ (a self-update). */
class PackageApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ApkInstaller {
        override fun refusal(apk: File): String? {
            val pm = context.packageManager
            val archive = pm.getPackageArchiveInfo(apk.path, SIGNER_FLAGS)
            val installed = pm.getPackageInfo(context.packageName, SIGNER_FLAGS)
            return apkRefusal(
                archivePackage = archive?.packageName,
                archiveCode = archive?.let(::versionCodeOf) ?: 0,
                archiveSigners = archive?.let(::signersOf).orEmpty(),
                ownPackage = context.packageName,
                installedCode = versionCodeOf(installed),
                ownSigners = signersOf(installed),
            )
        }

        override fun install(apk: File) {
            val installer = context.packageManager.packageInstaller
            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("base.apk", 0, apk.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    // Mutable on 31+: the system fills in the status extras.
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val result = PendingIntent.getBroadcast(context, 0, Intent(context, InstallResultReceiver::class.java), flags)
                    session.commit(result.intentSender)
                }
            } catch (e: Exception) {
                installer.abandonSession(sessionId)
                throw e
            }
        }

        private companion object {
            @Suppress("DEPRECATION")
            val SIGNER_FLAGS = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

            @Suppress("DEPRECATION")
            fun versionCodeOf(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

            @Suppress("DEPRECATION")
            fun signersOf(info: PackageInfo): Set<String> {
                val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
                return signatures.orEmpty().map { signature ->
                    MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
                }.toSet()
            }
        }
    }
