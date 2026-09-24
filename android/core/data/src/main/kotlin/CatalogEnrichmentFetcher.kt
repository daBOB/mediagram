package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import settings.TmdbSettings
import uniffi.mediagram_core.FetchReport
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Shared fetch progress and result; the stored key never leaves the settings store. */
data class CatalogEnrichmentState(
    val hasKey: Boolean = false,
    val running: Boolean = false,
    val report: FetchReport? = null,
    val error: String? = null,
)

/** Fetches missing posters and title descriptions against the current core, sharing progress across callers. */
@Singleton
class CatalogEnrichmentFetcher
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val settings: TmdbSettings,
    ) {
        private val fallbackLanguage = Locale.getDefault().toLanguageTag()
        private val fetching = Mutex()
        private val current = MutableStateFlow(CatalogEnrichmentState())
        val state = current.asStateFlow()

        suspend fun refreshKeyStatus() {
            try {
                val hasKey = settings.read() != null
                current.update { it.copy(hasKey = hasKey, error = it.error.takeUnless { error -> error == KEY_READ_ERROR }) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                current.update { it.copy(error = KEY_READ_ERROR) }
            }
        }

        suspend fun saveKey(key: String) {
            try {
                settings.write(key)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                current.update { it.copy(error = "Could not save the TMDB key. Please try again.") }
                return
            }
            current.update { it.copy(error = null) }
            refreshKeyStatus()
        }

        /**
         * Runs one fetch and awaits its report; an active fetch is skipped
         * immediately rather than joined. `null` means that skip, a missing
         * key, a key-read failure, or a failed core lookup/fetch.
         *
         * Quiet work preserves the displayed report/error and suppresses new
         * errors; state therefore does not distinguish every `null` outcome.
         * Cancellation propagates, clears running state, and releases admission.
         */
        suspend fun fetch(quiet: Boolean = false): FetchReport? {
            if (!fetching.tryLock()) return null
            try {
                current.update { it.copy(running = true) }
                val key =
                    try {
                        settings.read()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        if (!quiet) current.update { it.copy(error = KEY_READ_ERROR) }
                        return null
                    }
                if (key == null) {
                    current.update {
                        it.copy(hasKey = false, error = if (!quiet && it.hasKey) "No TMDB key is stored." else it.error)
                    }
                    return null
                }
                current.update { if (quiet) it.copy(hasKey = true) else it.copy(hasKey = true, report = null, error = null) }
                val report = coreProvider.awaitCore().fetchMissing(key, fallbackLanguage)
                if (!quiet) current.update { it.copy(report = report) }
                return report
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                if (!quiet) current.update { it.copy(error = e.coreSentence() ?: "The fetch failed.") }
                return null
            } finally {
                current.update { it.copy(running = false) }
                fetching.unlock()
            }
        }

        fun dismissResult() {
            current.update { it.copy(report = null, error = null) }
        }

        private companion object {
            // Storage exceptions can contain credentials or private paths; never render their message.
            const val KEY_READ_ERROR = "Could not read the saved TMDB key. Save it again on the TMDB key screen."
        }
    }
