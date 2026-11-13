package ui.tv.chrome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.tv.material3.Text
import com.mediagram.android.core.designsystem.R
import designsystem.LocalCatalogueTones
import designsystem.TvType
import designsystem.tvSp

/*
 * Funstation's Android runs inside WayDroid and cannot see the Bluetooth remote, which is paired
 * to the Linux host. The host hands the remote's battery in with
 * `am broadcast -a REMOTE_BATTERY_ACTION -p com.mediagram.android --ei percent N` through
 * `waydroid shell`, which runs as root. A device nobody tells — the TV box, a phone — never shows it.
 */
internal const val REMOTE_BATTERY_ACTION = "com.mediagram.android.REMOTE_BATTERY"
internal const val REMOTE_BATTERY_PERCENT = "percent"

// Only the system, the shell and root hold it, so no installed app can set the number.
internal const val REMOTE_BATTERY_SENDER = "android.permission.DUMP"

private const val PREFS = "remote_battery"
private const val KEY = "percent"

/**
 * The remote's last reported battery, or `null` when nothing ever reported one. Kept across
 * launches: the host repeats it only every few minutes, and the rail should not wait for that.
 */
@Composable
internal fun rememberRemoteBattery(): Int? {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var percent by remember(prefs) { mutableStateOf(prefs.getInt(KEY, -1).takeIf { it in 0..100 }) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val said = intent.getIntExtra(REMOTE_BATTERY_PERCENT, -1)
                    if (said !in 0..100) return
                    percent = said
                    prefs.edit { putInt(KEY, said) }
                }
            }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(REMOTE_BATTERY_ACTION), REMOTE_BATTERY_SENDER, null, ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

/** The remote and its battery, stacked so it fits the collapsed rail's icon column. */
@Composable
internal fun TvRemoteBattery(percent: Int, modifier: Modifier = Modifier) {
    val tones = LocalCatalogueTones.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics { contentDescription = "Remote battery $percent%" },
    ) {
        Image(painter = painterResource(R.drawable.core_designsystem_ic_remote), contentDescription = null, colorFilter = ColorFilter.tint(tones.quiet), modifier = Modifier.size(RailIcon))
        Text(text = "$percent%", style = TvType.labelSmall.copy(fontSize = 10.tvSp, fontWeight = FontWeight.Medium), color = tones.quiet, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
    }
}
