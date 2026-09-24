package ui.tv

import androidx.compose.foundation.background
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

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stubLabel(setupState))
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
