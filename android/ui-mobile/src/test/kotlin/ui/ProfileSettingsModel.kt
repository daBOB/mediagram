package ui

import catalog.profile.ManageProfilesViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import setup.ProfileSettingsViewModel

/** Who is watching's model with nobody chosen, for owners that hand back every Settings model SettingsScreen resolves. */
fun profileSettingsModel(): ProfileSettingsViewModel =
    mockk<ProfileSettingsViewModel>(relaxed = true).also {
        every { it.profile } returns MutableStateFlow(null)
        every { it.subtitle } returns MutableStateFlow("off")
    }

/** Manage's model with nobody in the household, for the same owners: Settings' index counts the profiles. */
fun householdModel(): ManageProfilesViewModel =
    mockk<ManageProfilesViewModel>(relaxed = true).also {
        every { it.profiles } returns MutableStateFlow(emptyList())
    }
