package testing

import data.HouseholdsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import model.HOME_HOUSEHOLD
import model.Household
import model.Households

/** The households as read once, with this device in [joined]; `null` has it in none yet, waiting or about to ask. */
class FakeHouseholdsRepository(
    joined: String? = HOME_HOUSEHOLD,
) : HouseholdsRepository {
    override val view = MutableStateFlow(Households(joined, listOf(Household(HOME_HOUSEHOLD, "Home", null))))

    override suspend fun reload() = Unit
}
