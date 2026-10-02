package update

import java.io.File

/** Puts a downloaded APK in this app's place. The outcome arrives at [AppUpdater.onInstallResult]. */
interface ApkInstaller {
    /** `null` when [apk] may replace this app, else the reason it may not. */
    fun refusal(apk: File): String?

    fun install(apk: File)
}

/**
 * Whether an APK may replace this app: the same package, a higher
 * versionCode, the same signing key. An archive whose signers this Android
 * version cannot read (below API 28) is left to PackageInstaller, which
 * refuses another key on its own.
 */
fun apkRefusal(
    archivePackage: String?,
    archiveCode: Long,
    archiveSigners: Set<String>,
    ownPackage: String,
    installedCode: Long,
    ownSigners: Set<String>,
): String? =
    when {
        archivePackage == null -> "the download is not an APK this device can read"
        archivePackage != ownPackage -> "the download is $archivePackage, not this app"
        archiveCode <= installedCode -> "the download is not newer than this app"
        archiveSigners.isNotEmpty() && archiveSigners != ownSigners -> "the download is signed with another key"
        else -> null
    }
