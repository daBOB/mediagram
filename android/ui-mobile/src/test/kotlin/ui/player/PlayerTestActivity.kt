package ui.player

import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import player.PlayerViewModel

/** Uses the Activity's real ViewModelStore and saved state across Robolectric recreation. */
class PlayerTestActivity : ComponentActivity() {
    lateinit var playerViewModel: PlayerViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerViewModel = ViewModelProvider(this, fixture.factory)[PlayerViewModel::class.java]
        setContent {
            // Exercise the production ContextWrapper traversal as well as the lifecycle observer.
            CompositionLocalProvider(LocalContext provides ContextThemeWrapper(this, android.R.style.Theme_Material)) {
                MaterialTheme {
                    var showingPlayer by rememberSaveable { mutableStateOf(true) }
                    if (showingPlayer) {
                        PlayerScreen("set-one", null, onBack = { showingPlayer = false }, viewModel = playerViewModel)
                    } else {
                        Text("Library")
                    }
                }
            }
        }
    }

    companion object {
        internal lateinit var fixture: PlayerLifecycleFixture
    }
}
