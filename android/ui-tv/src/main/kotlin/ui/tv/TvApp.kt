package ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import designsystem.Overscan
import setup.SetupUiState
import setup.SetupViewModel

/**
 * The television counterpart to `ui.MobileApp`: the same [SetupViewModel]
 * decides the same thing it decides on a phone — whether this device has
 * finished the three first-run questions — and a television asks it the
 * same way, re-checking on every return to the foreground rather than
 * trusting whatever it last drew. A session invalidated from elsewhere
 * while the television was asleep is caught here exactly as it is on the
 * phone, not through a separate poll this surface would have to invent.
 *
 * What differs from the phone is only how the answer is drawn: a
 * television is read across a room rather than held in the hand, so the
 * content sits inside [Overscan] instead of behind window insets, in the
 * catalogue theme's own [TvTheme] instead of `MediagramTheme`. The two
 * branches below are placeholders — the real library and setup screens are
 * later phases of this same surface.
 */
@Composable
fun TvApp() {
    TvTheme {
        val setupViewModel: SetupViewModel = hiltViewModel()
        val setupState by setupViewModel.state.collectAsStateWithLifecycle()

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setupViewModel.recheck() }

        TvShell {
            Text(text = stubLabel(setupState))
        }
    }
}

/**
 * The root every TV screen composes inside, not just this one: a plain
 * `Box` painted with a background colour never sets tv-material's
 * `LocalContentColor`, so text dropped straight into one falls back to
 * tv-material's own default (black) regardless of how dark the ground
 * under it is drawn — unreadable on [designsystem.Palette.Ground] rather
 * than merely mistthemed. `Surface` is what tv-material uses to publish a
 * content colour alongside its container colour, mirroring `MobileApp`'s
 * root M3 `Surface`, so `content` and everything it composes inherit
 * [designsystem.Palette.Text] the same way a phone screen inherits it from
 * `MediagramTheme`'s `Surface` without asking for a colour itself.
 */
@Composable
internal fun TvShell(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors =
            SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * Names the state on screen until the real screens exist. `Ready` reads as
 * "library" rather than its own type name because that is the screen it
 * stands in for; every other state is still exactly the setup step
 * [SetupUiState] says is outstanding, which is what a stub for that step
 * should say.
 */
private fun stubLabel(state: SetupUiState): String =
    if (state is SetupUiState.Ready) "library" else state::class.simpleName ?: "setup"
