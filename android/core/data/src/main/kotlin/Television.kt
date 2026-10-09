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

/** Android's own line between a phone and a tablet, its `sw600dp` resource qualifier. */
private const val TABLET_SMALLEST_WIDTH_DP = 600

/**
 * A tablet is at least 600dp across its smallest side, where Android itself
 * draws the line. A television reports less (960×540dp) and is not one.
 */
fun isTablet(context: Context): Boolean =
    context.resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
