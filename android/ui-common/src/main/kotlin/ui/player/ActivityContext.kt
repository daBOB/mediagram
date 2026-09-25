package ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * `LocalContext.current` is not necessarily the Activity itself — a
 * `ContextThemeWrapper` or a dialog host wraps it, and a naive
 * `as? Activity` cast would silently see neither and always stop. This
 * unwraps `ContextWrapper.baseContext` until it finds one, matching how
 * `MainActivity` (no such wrapper today) and any future one both resolve
 * correctly.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
