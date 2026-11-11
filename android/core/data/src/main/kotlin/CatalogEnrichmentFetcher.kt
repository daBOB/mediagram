package data

import android.util.Log
import data.settings.TmdbSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import uniffi.mediagram_core.FetchReport
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Fetches missing posters and title descriptions against the current core, sharing progress across callers. */
@Singleton
class CatalogEnrichmentFetcher
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val settings: TmdbSettings,
        // Hilt always supplies the real, device-read width in production
        // (see `DataModule.provideBackdropWidth`); the default here only
        // spares every existing test constructor a third argument.
        private val backdropWidth: BackdropWidth = BackdropWidth { 780 },
        // The core does its database and disk work inside each poll of
        // `fetchMissing`; on the caller's main thread that froze the screen
        // for seconds and starved the progress poll beside it.
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
        private val homeCache: HomeCacheServer = HomeCacheServer(),
    ) {
        private val fallbackLanguage = Locale.getDefault().toLanguageTag()
        private val fetching = Mutex()
        private val current = MutableStateFlow(CatalogEnrichmentState())
        val state = current.asStateFlow()

        suspend fun refreshKeyStatus() {
            try {
                val hasKey = settings.read() != null
                val viaHomeCache = !hasKey && homeCache.connected.value
                current.update {
                    it.copy(hasKey = hasKey, viaHomeCache = viaHomeCache, error = it.error.takeUnless { error -> error == KEY_READ_ERROR })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "TMDB key status failed: ${e.javaClass.simpleName}")
                current.update { it.copy(error = KEY_READ_ERROR) }
            }
        }

        /** Keeps [CatalogEnrichmentState.viaHomeCache] following the server as it comes and goes, with no fetch needed; never returns. */
        suspend fun followHomeCache() {
            homeCache.connected.collect { refreshKeyStatus() }
        }

        suspend fun saveKey(key: String) {
            try {
                settings.write(key)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "TMDB key save failed: ${e.javaClass.simpleName}")
                current.update { it.copy(error = "Could not save the TMDB key. Please try again.") }
                return
            }
            current.update { it.copy(error = null) }
            refreshKeyStatus()
        }

        /**
         * Runs one fetch and awaits its report; an active fetch is skipped
         * immediately rather than joined. `null` means that skip, a missing
         * key with no home cache server, a key-read failure, or a failed core
         * lookup/fetch.
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
                        Log.w(TAG, "TMDB key read failed: ${e.javaClass.simpleName}")
                        if (!quiet) current.update { it.copy(error = KEY_READ_ERROR) }
                        return null
                    }
                if (key == null) return fetchKeyless(quiet)
                current.update {
                    val known = it.copy(hasKey = true, viaHomeCache = false)
                    if (quiet) known else known.copy(report = null, error = null)
                }
                val report = fetchMissing(key)
                if (!quiet) current.update { it.copy(report = report) }
                return report
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "TMDB fetch failed: ${e.javaClass.simpleName}")
                if (!quiet) current.update { it.copy(error = e.coreSentence() ?: "The fetch failed.") }
                return null
            } finally {
                current.update { it.copy(running = false, progress = null) }
                fetching.unlock()
            }
        }

        /**
         * With no key stored: what the home cache server holds, shown only
         * when it brought something. A run with no server, or one the server
         * could not answer — not filled yet, or asleep — ends exactly as a
         * keyless update always has, with no report and no failure: a title
         * the server lacks needs a key, and nothing broke.
         */
        private suspend fun fetchKeyless(quiet: Boolean): FetchReport? {
            val linked = homeCache.connected.value
            current.update { it.copy(viaHomeCache = linked) }
            val report =
                try {
                    if (linked) fetchMissing("") else null
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Log.w(TAG, "Keyless fetch failed: ${e.javaClass.simpleName}")
                    null
                }
            if (report != null && report.broughtSomething()) {
                current.update { if (quiet) it.copy(hasKey = false) else it.copy(hasKey = false, report = report, error = null) }
                return report
            }
            current.update { it.copy(hasKey = false, error = if (!quiet && it.hasKey) "No TMDB key is stored." else it.error) }
            return null
        }

        /** The core's fetch, its progress published as it walks; an empty [key] asks only the home cache server. */
        private suspend fun fetchMissing(key: String): FetchReport {
            val core = coreProvider.awaitCore()
            return coroutineScope {
                // The core counts as it walks; a run is minutes long on a
                // first fetch, and "running" alone cannot say how many.
                val watching =
                    launch {
                        while (true) {
                            current.update { it.copy(progress = core.fetchProgress()) }
                            delay(PROGRESS_POLL_MS)
                        }
                    }
                try {
                    withContext(dispatcher) { core.fetchMissing(key, fallbackLanguage, backdropWidth.pixels().toUInt()) }
                } finally {
                    watching.cancel()
                }
            }
        }

        private fun FetchReport.broughtSomething(): Boolean = postersFetched + backdropsFetched + detailsRecorded + failed > 0u

        fun dismissResult() {
            current.update { it.copy(report = null, error = null) }
        }

        private companion object {
            // Only an exception's class is logged here, never its message or
            // stack: a storage failure can name credentials or private paths,
            // and a fetch's transport error repeats the request URL, which
            // carries the TMDB key as a query parameter.
            const val TAG = "enrichment"

            const val PROGRESS_POLL_MS = 500L

            // Storage exceptions can contain credentials or private paths; never render their message.
            const val KEY_READ_ERROR = "Could not read the saved TMDB key. Save it again on the TMDB key screen."
        }
    }
