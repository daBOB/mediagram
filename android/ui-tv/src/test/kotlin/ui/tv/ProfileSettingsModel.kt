package ui.tv

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import setup.ProfileSettingsViewModel

/** The Profile section's model with nobody chosen, for owners that hand back every Settings model TvSettingsScreen resolves. */
fun profileSettingsModel(): ProfileSettingsViewModel =
    mockk<ProfileSettingsViewModel>(relaxed = true).also {
        every { it.profile } returns MutableStateFlow(null)
        every { it.subtitle } returns MutableStateFlow("off")
    }
