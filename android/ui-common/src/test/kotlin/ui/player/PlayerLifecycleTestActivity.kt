package ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import player.PlayerViewModel

/**
 * Hosts [PlayerLifecycle] alone, without the material-styled screen it
 * normally sits under, so its own effects — open, stop, save — can be
 * proven without a real player or catalog behind them.
 */
class PlayerLifecycleTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var shown by rememberSaveable { mutableStateOf(true) }
            hide = { shown = false }
            if (shown) {
                PlayerLifecycle(viewModel = viewModel, setId = "set-one", fsk = "12")
            }
        }
    }

    companion object {
        internal lateinit var viewModel: PlayerViewModel

        /** What a real screen's own back action removes the Composition with. */
        internal var hide: () -> Unit = {}
    }
}
