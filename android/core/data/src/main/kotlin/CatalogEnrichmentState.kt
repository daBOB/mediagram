package data

import uniffi.mediagram_core.FetchProgress
import uniffi.mediagram_core.FetchReport

/** Shared fetch progress and result; the stored key never leaves the settings store. */
data class CatalogEnrichmentState(
    val hasKey: Boolean = false,
    /** No key is stored, and the core has a home cache server to read from instead. */
    val viaHomeCache: Boolean = false,
    val running: Boolean = false,
    /** How far the run under way has got, once its first step has begun; `null` otherwise. */
    val progress: FetchProgress? = null,
    val report: FetchReport? = null,
    val error: String? = null,
)
