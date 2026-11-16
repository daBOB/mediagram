package catalog.profile

import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

/**
 * The owner's PIN, held once for the whole Profiles page: asked at the first
 * change by either view model, then sent with every change of both. Dropped
 * when the page is left or a change is answered with a wrong PIN.
 */
@ActivityRetainedScoped
class OwnerPin @Inject constructor() {
    var held: String? = null

    fun drop() {
        held = null
    }
}
