package ui.common.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ProfileViewModel

/**
 * Manage profiles, and a PIN half typed into the picker, belong to whoever
 * is at the screen now. Both view models outlive the screen, so on their own
 * they would wait for the next person: a parent's Manage, opened from a
 * kid's library and left when the tablet timed out, came back as the kid's
 * library — and the kid's next tap on its own name opened the parent's
 * Manage, PIN held. So once the library shows again ([showsLibrary]), or the
 * app is left at all (Home, the screen going off, another app), Manage
 * closes and drops its PIN, and the picker's prompt is given up.
 *
 * Both gates call this, so a phone and a television forget the same way.
 */
@Composable
fun ForgetManageWhenAway(
    showsLibrary: Boolean,
    picker: ProfileViewModel,
    manage: ManageProfilesViewModel,
) {
    LaunchedEffect(showsLibrary) { if (showsLibrary) manage.close() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        manage.close()
        picker.cancelPin()
    }
}
