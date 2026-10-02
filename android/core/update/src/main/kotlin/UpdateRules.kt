package update

/** How long one look at the channel stands for: a release is not urgent, and every check is a Telegram request. */
const val CHECK_INTERVAL_MS = 60 * 60 * 1000L

/**
 * Whether to look for a release now. Never while something plays — the APK
 * download would compete with the player for Telegram file requests — and
 * never in a build that does not update itself.
 */
fun shouldCheck(
    enabled: Boolean,
    playing: Boolean,
    lastCheckAtMs: Long?,
    nowMs: Long,
): Boolean = enabled && !playing && (lastCheckAtMs == null || nowMs - lastCheckAtMs >= CHECK_INTERVAL_MS)

/** Android installs an update only over a lower versionCode. */
fun isNewer(
    releaseCode: Long,
    installedCode: Long,
): Boolean = releaseCode > installedCode

/**
 * The files in the updates directory to delete: everything except the APK
 * for [wantedCode] — older downloads, partial ones, the copy of the version
 * now installed. With nothing wanted, all of them.
 */
fun staleFiles(
    names: List<String>,
    wantedCode: Long?,
): List<String> = names.filter { it != "$wantedCode.apk" }
