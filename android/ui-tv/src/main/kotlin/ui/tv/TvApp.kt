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
import designsystem.Overscan
import setup.AppearanceViewModel
import setup.SetupUiState
import setup.SetupViewModel
import ui.tv.profile.TvProfileGate
import ui.tv.setup.TvSetupStep

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
 * content composes in the catalogue theme's own [TvTheme] instead of
 * `MediagramTheme`. Every outstanding step draws through [TvSetupStep] now,
 * the way `MobileApp`'s own steps draw through its private `SetupStep`;
 * `Ready` gates on [TvProfileGate] before the catalogue, the way
 * `ui.LibraryFlow` gates the phone's own library behind
 * `ui.profile.ProfileGate` — a viewer, not this device's setup answers,
 * decides whose shelves come next, and [TvLibrary] is what those shelves
 * and everything they open are.
 */
@Composable
fun TvApp() {
    val appearanceViewModel: AppearanceViewModel = hiltViewModel()
    val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()
    TvTheme(accent = appearance.accent) {
        val setupViewModel: SetupViewModel = hiltViewModel()
        val setupState by setupViewModel.state.collectAsStateWithLifecycle()

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setupViewModel.recheck() }

        TvShell {
            if (setupState is SetupUiState.Ready) {
                TvProfileGate { profile ->
                    TvLibrary(profile, onStartOver = setupViewModel::startOver, onSignedOut = setupViewModel::recheck)
                }
            } else {
                TvSetupStep(state = setupState, viewModel = setupViewModel)
            }
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
 *
 * This is colour only: no padding, no centring. Each screen owns its own
 * safe area, because the right one depends on what the screen draws — a
 * lazy catalogue wall applies [Overscan] as `contentPadding` on the list
 * itself rather than a wrapping `Modifier.padding`, so a focused card's
 * growth under [TvFocus.Scale] lands inside the scrollable viewport
 * instead of being clipped by a fixed inset; full-bleed content such as
 * the video player ignores [Overscan] entirely, since filling the frame is
 * the point. A screen with no layout of its own yet reaches for
 * [TvSafeArea] instead.
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
        content()
    }
}

/**
 * The safe area a screen reaches for when it has nothing of its own to
 * lay out: padded by [Overscan] and centred, the way a message standing in
 * for a wall needs to be drawn. A real lazy wall or a full-bleed player does not use
 * this — see [TvShell] for why each of those owns a different safe area.
 */
@Composable
internal fun TvSafeArea(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
