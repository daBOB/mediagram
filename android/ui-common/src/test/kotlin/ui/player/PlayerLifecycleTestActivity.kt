package ui.player

import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import player.PlayerViewModel

/**
 * Hosts [PlayerLifecycle] alone, without the material-styled screen it
 * normally sits under, so its own effects — stop, save — can be
 * proven without a real player or catalog behind them.
 */
class PlayerLifecycleTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Exercise the production ContextWrapper traversal as well as the lifecycle observer.
            CompositionLocalProvider(LocalContext provides ContextThemeWrapper(this, android.R.style.Theme_Material)) {
                var shown by rememberSaveable { mutableStateOf(true) }
                hide = { shown = false }
                if (shown) {
                    PlayerLifecycle(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        internal lateinit var viewModel: PlayerViewModel

        /** What a real screen's own back action removes the Composition with. */
        internal var hide: () -> Unit = {}
    }
}
