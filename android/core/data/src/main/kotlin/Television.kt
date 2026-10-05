package data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * A television reports [Configuration.UI_MODE_TYPE_TELEVISION] from the
 * system's [UiModeManager]; every other device (phone, tablet) does not.
 * Lives in core:data, the lowest module that both the app (surface choice,
 * self-update) and the backdrop width can see.
 */
fun isTelevision(context: Context): Boolean =
    (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
        .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
