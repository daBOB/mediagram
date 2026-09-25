package catalog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import settings.ShelfView
import settings.ShelfViewSettings
import javax.inject.Inject

/**
 * Whether [shelf] offers the List/Grid choice — `viewCollections` and
 * `viewMovies` in the web's `app.js`: Films and Series do, a shelf of
 * courses does not. A course has no artwork, so its wall of plates is a
 * wall of initials, and a course runs to a hundred lessons, which is a list
 * anyway. An empty shelf offers nothing: there is nothing to lay out.
 */
fun offersViewChoice(shelf: Shelf): Boolean =
    shelf.entries.isNotEmpty() && shelf.entries.none { it is Entry.Collection && it.kind == CollectionKind.COURSE }

/** How [shelf] is drawn given this device's [chosen] view: courses always as a list. */
fun shelfViewFor(shelf: Shelf, chosen: ShelfView): ShelfView = if (offersViewChoice(shelf)) chosen else ShelfView.LIST

/** This device's shelf view, for the shelves that offer one. */
@HiltViewModel
class ShelfViewModel @Inject constructor(private val settings: ShelfViewSettings) : ViewModel() {
    val view: StateFlow<ShelfView> = settings.view

    fun choose(view: ShelfView) = settings.choose(view)
}
