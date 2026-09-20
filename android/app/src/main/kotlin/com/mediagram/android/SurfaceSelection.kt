package com.mediagram.android

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * A television reports [Configuration.UI_MODE_TYPE_TELEVISION] from the
 * system's [UiModeManager]; every other device (phone, tablet) does not.
 * Resolved once at launch so [MainActivity] can pick a Compose surface.
 */
fun isTelevision(context: Context): Boolean =
    (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
        .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
