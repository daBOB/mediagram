package update

/** Where this device stands with the newest release — what the System screen's Updates row reads. */
sealed interface UpdateStatus {
    /** A debug or benchmark build: it never updates itself, and the row is hidden. */
    data object Off : UpdateStatus

    data object NotChecked : UpdateStatus

    data class UpToDate(val checkedAtMs: Long) : UpdateStatus

    data class Downloading(val versionName: String) : UpdateStatus

    data class Ready(val versionName: String) : UpdateStatus

    /** Android asked for a confirm screen (installs from this app not allowed yet, or Android 10–11); it opens the next time the app does. */
    data class ConfirmWaiting(val versionName: String) : UpdateStatus

    data class Failed(val reason: String) : UpdateStatus
}

/** The Updates row's text, or `null` when the row is not shown. */
fun updateLine(
    status: UpdateStatus,
    nowMs: Long,
): String? =
    when (status) {
        UpdateStatus.Off -> null
        UpdateStatus.NotChecked -> "not checked yet"
        is UpdateStatus.UpToDate -> "up to date · checked ${agoOf(nowMs - status.checkedAtMs)}"
        is UpdateStatus.Downloading -> "${status.versionName} · downloading"
        is UpdateStatus.Ready -> "${status.versionName} ready · installs when you leave the app"
        is UpdateStatus.ConfirmWaiting -> "${status.versionName} ready · confirm when asked"
        is UpdateStatus.Failed -> "last update failed: ${status.reason}"
    }

private fun agoOf(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}
