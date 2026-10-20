package ui.tv

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import model.Profile
import setup.ProfileSettingsViewModel

/** The Profiles page's model reading [profile] — nobody chosen unless given — for owners that hand back every Settings model TvSettingsScreen resolves. */
fun profileSettingsModel(profile: StateFlow<Profile?> = MutableStateFlow(null)): ProfileSettingsViewModel =
    mockk<ProfileSettingsViewModel>(relaxed = true).also {
        every { it.profile } returns profile
        every { it.subtitle } returns MutableStateFlow("off")
    }
