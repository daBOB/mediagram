package ui.profile

import androidx.lifecycle.ViewModel
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import data.HouseholdsRepository
import data.WatchStateRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import model.HOME_HOUSEHOLD
import model.Household
import model.Households

/**
 * The households' view models, real, over [repository] and [households]: the
 * gate resolves Join a household and the owner's part beside the picker on
 * every draw, so every harness that draws the gate hands them back.
 * [households] defaults to Home alone, read and joined, with nobody in it as
 * the households see them: neither Join a household nor This device shows,
 * a first profile is offered as before, and the owner's part stays closed.
 */
internal fun householdViewModels(
    repository: WatchStateRepository,
    households: HouseholdsRepository = homeAlone(),
): Map<Class<out ViewModel>, ViewModel> =
    mapOf(
        JoinHouseholdViewModel::class.java to JoinHouseholdViewModel(repository, households),
        HouseholdsOwnerViewModel::class.java to HouseholdsOwnerViewModel(repository, households, mockk(relaxed = true)),
    )

/** Home alone, read and joined, with nobody in it as the households see them — [householdViewModels]' default. */
internal fun homeAlone(): HouseholdsRepository =
    mockk(relaxed = true) { every { view } returns MutableStateFlow(Households(HOME_HOUSEHOLD, listOf(Household(HOME_HOUSEHOLD, "Home", null)))) }
