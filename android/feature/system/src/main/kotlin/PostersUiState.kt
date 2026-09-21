package system

import uniffi.mediagram_core.PosterReport

/**
 * What [PostersViewModel] has to say about the TMDB key and the last
 * fetch. Left unformatted on purpose, the same split [SystemUiState] makes:
 * this module holds the facts, ui-mobile turns [report]'s counts into the
 * sentence a viewer reads.
 *
 * [hasKey] is whether one is stored, never the key itself — a stored
 * secret is confirmed, not carried around in state a screen rotation could
 * write into a saved-instance bundle.
 */
data class PostersUiState(
    val hasKey: Boolean = false,
    val running: Boolean = false,
    val report: PosterReport? = null,
    val error: String? = null,
)
