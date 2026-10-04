package setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.PROFILE_SCOPE
import data.PlayerPreferences
import data.SUBTITLE_OFF
import data.SUBTITLE_PREFERENCE
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import model.Profile
import javax.inject.Inject

private const val OFF = SUBTITLE_OFF

/** The languages the profile default offers, as (stored value, label) — the web's own three. */
val ProfileSubtitleChoices: List<Pair<String, String>> = listOf(OFF to "Off", "de" to "German", "en" to "English")

/**
 * Settings › Profile: who is watching, and that profile's default subtitle
 * language. The language is per profile, so it is only offered once one is
 * chosen, and only ever written from the offered set.
 */
@HiltViewModel
class ProfileSettingsViewModel
    @Inject
    constructor(
        private val watchState: WatchStateRepository,
        private val preferences: PlayerPreferences,
    ) : ViewModel() {
        /** The chosen profile, `null` until one is. */
        val profile: StateFlow<Profile?> = watchState.chosenProfile

        private val _subtitle = MutableStateFlow(OFF)

        /** One of [ProfileSubtitleChoices]' values; `off` with no profile or nothing stored. */
        val subtitle: StateFlow<String> = _subtitle.asStateFlow()

        init {
            viewModelScope.launch {
                watchState.chosenProfileId.collectLatest { id ->
                    _subtitle.value = id?.let { storedSubtitle(it) } ?: OFF
                }
            }
        }

        /** Stores [value] as the chosen profile's default; a value outside the offered set, or no profile chosen, is refused. */
        fun chooseSubtitle(value: String) {
            val id = watchState.chosenProfileId.value ?: return
            if (ProfileSubtitleChoices.none { it.first == value }) return
            viewModelScope.launch {
                // Shown only once written: the row never claims a choice a refused write lost.
                val written = attempt { preferences.remember(id, PROFILE_SCOPE, SUBTITLE_PREFERENCE, value) } == true
                if (written && watchState.chosenProfileId.value == id) _subtitle.value = value
            }
        }

        private suspend fun storedSubtitle(id: String): String? =
            attempt { preferences.load(id, PROFILE_SCOPE)[SUBTITLE_PREFERENCE] }?.takeIf { v -> ProfileSubtitleChoices.any { it.first == v } }

        // A row that cannot be read or written falls back to its default; it never takes Settings down.
        private suspend fun <T> attempt(block: suspend () -> T): T? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                null
            }
    }

/** The index row's line under "Profile" — the web's own: a kid's limit is its own, so it is named. */
fun profileStatus(profile: Profile?): String = profile?.let { "${it.name}${if (it.kids) " · Kids · FSK ${it.kidsLimit}" else ""}" } ?: "Nobody chosen"
