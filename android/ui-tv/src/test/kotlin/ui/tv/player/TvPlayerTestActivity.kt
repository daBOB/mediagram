package ui.tv.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.tv.material3.Text
import model.MediaSet
import player.PlayerViewModel
import ui.tv.TvShell
import ui.tv.TvTheme

/**
 * Hosts [TvPlayerScreen] over the Activity's real ViewModelStore and saved
 * state, so a configuration change recreates the screen the way a real
 * one does — and a "Library" line stands in for wherever leaving goes.
 * A switch to another title of [run] moves to it as the library does, and
 * is written down in [switches].
 */
class TvPlayerTestActivity : ComponentActivity() {
    lateinit var playerViewModel: PlayerViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerViewModel = ViewModelProvider(this, fixture.factory)[PlayerViewModel::class.java]
        setContent {
            TvTheme {
                TvShell {
                    var playing by rememberSaveable { mutableStateOf(true) }
                    var open by rememberSaveable { mutableStateOf("set-one") }
                    if (playing) {
                        TvPlayerScreen(
                            setId = open,
                            set = set?.takeIf { it.setId == open },
                            run = run,
                            onBack = { playing = false },
                            onSwitch = { id, _ ->
                                switches += id
                                open = id
                            },
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
        internal lateinit var fixture: TvPlayerFixture
        internal var set: MediaSet? = null
        internal var run: List<String> = emptyList()
        internal val switches = mutableListOf<String>()
    }
}
