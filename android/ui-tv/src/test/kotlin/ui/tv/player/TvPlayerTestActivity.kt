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
                    if (playing) {
                        TvPlayerScreen("set-one", set, onBack = { playing = false }, viewModel = playerViewModel)
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
    }
}
