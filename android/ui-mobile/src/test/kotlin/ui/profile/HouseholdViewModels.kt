package ui.profile

import androidx.lifecycle.ViewModel
import catalog.profile.JoinHouseholdViewModel
import data.HouseholdsRepository
import data.WatchStateRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import model.Households

/**
 * The households' view models, real, over [repository] and [households]: the
 * gate resolves Join a household beside the picker on every draw, so every
 * harness that draws the gate hands them back. [households] defaults to a
 * list never read, which shows neither Join a household nor This device.
 */
internal fun householdViewModels(
    repository: WatchStateRepository,
    households: HouseholdsRepository = mockk(relaxed = true) { every { view } returns MutableStateFlow(Households()) },
): Map<Class<out ViewModel>, ViewModel> = mapOf(JoinHouseholdViewModel::class.java to JoinHouseholdViewModel(repository, households))
