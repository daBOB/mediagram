package testing

import data.HouseholdsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import model.HOME_HOUSEHOLD
import model.Household
import model.Households

/**
 * The households as read once a sync round was heard, with this device in
 * [joined]; `null` has it in none, about to ask. A test sets [view] to
 * `Households()` for households not read yet.
 */
class FakeHouseholdsRepository(
    joined: String? = HOME_HOUSEHOLD,
) : HouseholdsRepository {
    override val view = MutableStateFlow(Households(joined, listOf(Household(HOME_HOUSEHOLD, "Home", null)), synced = true))

    override suspend fun reload() = Unit
}
