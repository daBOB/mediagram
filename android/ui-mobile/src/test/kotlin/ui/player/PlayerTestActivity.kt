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
                        PlayerScreen(
                            "set-one",
                            run,
                            null,
                            handPicked = false,
                            onBack = { showingPlayer = false },
                            onSwitch = { id, _ -> switches += id },
                            viewModel = playerViewModel,
                        )
                    } else {
                        Text("Library")
                    }
                }
            }
        }
    }

    companion object {
        internal lateinit var fixture: PlayerLifecycleFixture

        /** The run the title opens on: none — a film — unless a test gives it one. */
        internal var run: List<String> = emptyList()

        /** Every title a switch asked to move to, in order. */
        internal val switches = mutableListOf<String>()
    }
}
