package playback

/** `GET /v1/status`'s body, for the Settings row that reports how much a paired server is holding. */
data class LanServerStatus(val heldBytes: Long, val budgetBytes: Long, val chunks: Long)

/**
 * `GET /v1/sets/{id}`'s body, for a film page's "home server: x of y GB"
 * line. [total] can be `null` even while [chunksHeld] is nonzero — the
 * server reads its recorded total and its chunk count as two separate
 * steps, so a PUT landing between them, or a `total` file a restart's scan
 * found missing or unreadable while still trusting the chunks around it,
 * both leave it unset without meaning "holds nothing". Decide whether the
 * server holds anything from [bytesHeld] `> 0`, and take the film's own
 * size — the "y" in "x of y GB" — from the catalogue, not from [total].
 */
data class LanSetStatus(val total: Long?, val chunksHeld: Long, val bytesHeld: Long)

/** How a PUT to the LAN server came back. */
sealed interface LanPutResult {
    /** 201 (newly stored) or 200 (already held) — either way the server now has it. */
    data object Stored : LanPutResult

    /** The token this device holds does not match the server's; further writes must stop. */
    data object Unauthorized : LanPutResult

    /** A 400/409/413 — a shape or length the server refuses. Not a reason to stop writing altogether. */
    data object Rejected : LanPutResult
}

/**
 * The wire protocol [LanFirstChunkSource], [LanWriteQueue] and
 * [LanServerLocator] actually depend on — narrow enough that each of their
 * tests fakes it directly, rather than running a real HTTP server for
 * every branch. [LanChunkClient] is the one real implementation, and the
 * one place any of it is proven against an actual server
 * ([LanChunkClientTest]'s `MockWebServer`).
 */
interface LanChunkProtocol {
    suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray?

    suspend fun put(
        baseUrl: String,
        token: String,
        setId: String,
        index: Long,
        total: Long,
        body: ByteArray,
    ): LanPutResult

    suspend fun verify(baseUrl: String): Boolean

    /** `null` for anything but a clean 200 with a well-formed body — same leniency as [verify]. */
    suspend fun status(baseUrl: String): LanServerStatus?

    /**
     * `GET /v1/sets/{id}`. `null` for a 404 (a malformed id, or a server
     * too old to know this route at all), a network error, or a malformed
     * body — never for an id the server simply holds nothing of, which is
     * a `200` with zero chunks and a `null` total instead. Defaults to
     * `null` here so every existing fake keeps compiling as "server too
     * old"; [LanChunkClient] is the one implementation that actually asks.
     */
    suspend fun setStatus(
        baseUrl: String,
        setId: String,
    ): LanSetStatus? = null
}
