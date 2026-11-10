package com.mediagram.spieltisch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import uniffi.spieltisch_core.gameCatalogue

/** The app is the game night: it opens on the shelf, and leaving the shelf leaves the app. */
class MainActivity : ComponentActivity() {
    private val model by viewModels<GameNightViewModel> {
        viewModelFactory { initializer { GameNightViewModel((application as SpieltischApp).night) } }
    }

    // Whatever the answer, the night goes on: without the permission only the phones on the Wi-Fi miss it.
    private val askLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { showNight() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nobody touches the remote during a game: the TV must not dim into its screensaver.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // From API 37 phones reach the host and find its announcement only with this, so it is asked
        // before the screen that starts the night is shown.
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            askLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            showNight()
        }
    }

    private fun showNight() {
        val games = shelfGames(gameCatalogue())
        setContent { SpieltischTheme { TvGamesFrame(model, games, leave = ::finish) } }
    }
}
