package ui

import catalog.CatalogTab
import catalog.KeptKind
import com.mediagram.android.core.designsystem.R

/**
 * One row the rail draws — the web's `rail-nav`, System held apart the way
 * `.apart` sits a little away from the rest. Shared by the tablet's own
 * [ui.chrome.LibraryRail] and the television's rail, so the two surfaces'
 * rail cannot list the seven rows in a different order or point them at
 * different icons without both call sites changing together.
 */
enum class RailItem(val label: String, val icon: Int) {
    MY_LIST("My List", R.drawable.core_designsystem_ic_rail_my_list),
    CONTINUE_WATCHING("Continue", R.drawable.core_designsystem_ic_rail_continue),
    LATEST("Latest", R.drawable.core_designsystem_ic_rail_latest),
    GENRES("Genres", R.drawable.core_designsystem_ic_rail_genres),
    STATS("Stats", R.drawable.core_designsystem_ic_rail_stats),
    SETTINGS("Settings", R.drawable.core_designsystem_ic_rail_settings),
    SYSTEM("System", R.drawable.core_designsystem_ic_settings_system),
}

/** The rail row [tab] is, on both surfaces: Continue and My List are rail rows rather than pills, and every other tab has none. */
fun railItemOf(tab: CatalogTab): RailItem? =
    when (tab) {
        is CatalogTab.Kept ->
            when (tab.kind) {
                KeptKind.CONTINUE -> RailItem.CONTINUE_WATCHING
                KeptKind.WATCHLIST -> RailItem.MY_LIST
                KeptKind.COLLECTIONS -> null
            }
        CatalogTab.Home, is CatalogTab.Dept -> null
    }
