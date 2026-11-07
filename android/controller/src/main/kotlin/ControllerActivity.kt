package com.mediagram.controller

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// The game night page's felt table, so the app and the page look like one thing.
private val Felt = Color(0xFF0F5A43)
private val Card = Color(0xFFFBF8F1)
private val Muted = Color(0xFFA9CDBF)
private val Ink = Color(0xFF0C2A22)
private val Yellow = Color(0xFFF6C731)

class ControllerActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("controller", Context.MODE_PRIVATE) }
    private val bridge by lazy { AppBridge(prefs, Buzzer(applicationContext)::buzz) }
    private var screen by mutableStateOf<ControllerScreen>(ControllerScreen.Searching())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        screen = startScreen(prefs.getString(BOX_URL, null))

        // Discovery runs while the app is in front, and starts afresh each time the app falls
        // back to waiting, so a box that is still announced is found again.
        val finder = BoxFinder(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshotFlow { screen is ControllerScreen.Searching }
                    .distinctUntilChanged()
                    .collectLatest { finder.found().collect { on(ControllerEvent.Found(it)) } }
            }
        }

        setContent {
            val s = screen
            val playing = s is ControllerScreen.Playing
            LaunchedEffect(playing) { playingChrome(playing) }
            when (s) {
                is ControllerScreen.Playing ->
                    GameWebView(s.url, bridge, onFailed = { on(ControllerEvent.PageFailed(it)) }, Modifier.fillMaxSize())
                is ControllerScreen.Searching -> Waiting(s.notice, onScan = ::scan)
            }
        }
    }

    private fun on(event: ControllerEvent) {
        val next = screen.after(event)
        if (next == screen) return
        screen = next
        if (next is ControllerScreen.Playing) prefs.edit { putString(BOX_URL, next.url) }
    }

    private fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning
            .getClient(this, options)
            .startScan()
            .addOnSuccessListener { on(ControllerEvent.Scanned(it.rawValue.orEmpty())) }
            .addOnFailureListener { screen = ControllerScreen.Searching(NO_SCANNER) }
    }

    /** While playing the page has the whole screen, as the browser page has in fullscreen. */
    private fun playingChrome(playing: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (playing) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private companion object {
        const val BOX_URL = "box.url"
        const val NO_SCANNER = "Der QR-Scanner ist auf diesem Gerät nicht verfügbar."
    }
}

@Composable
private fun Waiting(
    notice: String?,
    onScan: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Felt).safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Spieltisch", color = Card, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(24.dp))
        Text("Warte auf den Fernseher …", color = Card, fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Starte auf dem Fernseher den Spieleabend.", color = Muted, fontSize = 16.sp, textAlign = TextAlign.Center)
        if (notice != null) {
            Spacer(Modifier.height(24.dp))
            Text(notice, color = Yellow, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(40.dp))
        Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Ink)) {
            Text("QR-Code scannen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
