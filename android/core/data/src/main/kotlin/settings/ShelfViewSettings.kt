package settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether a Films or Series shelf is a wall of posters or a list of rows. */
enum class ShelfView { GRID, LIST }

/**
 * What a stored value means — `modeFrom` in the web's `shelf-mode.js`, with
 * the default turned round. The web opens on the list, the view that
 * catalogue was designed as; the phone opens on posters, which a thumb finds
 * faster than a line of text. Anything unrecognised, a value from some later
 * version included, is the default, so nobody is ever left without a shelf.
 */
fun shelfViewFrom(stored: String?): ShelfView = if (stored == LIST_VALUE) ShelfView.LIST else ShelfView.GRID

/**
 * This device's shelf view. A property of the device, not of the library or
 * the profile, as on the web: the tablet that shows posters keeps showing
 * them whoever is watching.
 */
interface ShelfViewSettings {
    val view: StateFlow<ShelfView>

    fun choose(view: ShelfView)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryShelfViewSettings(initial: ShelfView = ShelfView.GRID) : ShelfViewSettings {
    private val _view = MutableStateFlow(initial)
    override val view: StateFlow<ShelfView> = _view.asStateFlow()

    override fun choose(view: ShelfView) {
        _view.value = view
    }
}

/**
 * Plain preferences, not the encrypted ones the setup flow uses: how a shelf
 * is laid out is not a secret, and a keystore failure should never be able
 * to cost a viewer their shelf.
 */
class SharedPreferencesShelfViewSettings(context: Context) : ShelfViewSettings {
    private val preferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    private val _view = MutableStateFlow(shelfViewFrom(preferences.getString(KEY_VIEW, null)))
    override val view: StateFlow<ShelfView> = _view.asStateFlow()

    override fun choose(view: ShelfView) {
        _view.value = view
        // The default is stored as its absence, as on the web, so a viewer who
        // never chose and one who chose posters are the same viewer.
        preferences.edit().apply {
            if (view == ShelfView.GRID) remove(KEY_VIEW) else putString(KEY_VIEW, LIST_VALUE)
        }.apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "shelf_view_settings"
        const val KEY_VIEW = "shelf_view"
    }
}

private const val LIST_VALUE = "list"
