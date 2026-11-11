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

    override fun onStart() {
        super.onStart()
        val updater = (application as SpieltischApp).updater
        updater.onForeground()
        // A confirm screen Android asked for while the app was in the background (installs from this app not allowed yet, or Android 10-11).
        updater.takeConfirmIntent()?.let { runCatching { startActivity(it) } }
    }

    override fun onStop() {
        super.onStop()
        // After the super call: the screen's own stop, which ends the night, has run by now, so the install may go ahead.
        (application as SpieltischApp).updater.onBackground()
    }

    private fun showNight() {
        val games = shelfGames(gameCatalogue())
        setContent { SpieltischTheme { TvGamesFrame(model, games, leave = ::finish) } }
    }
}
