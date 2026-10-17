package data

/** Sends a report's text through the core. Throws while there is no core or sign-in, or Telegram can't be reached. */
interface ReportChannel {
    suspend fun send(text: String)
}

// No library handle: the report goes to the account itself, not to the channel.
class CoreReportChannel(
    private val coreProvider: CoreProvider,
) : ReportChannel {
    override suspend fun send(text: String) {
        // coreOrNull, not awaitCore: a device not signed in yet fails at once rather than waiting for ever.
        val core = coreProvider.coreOrNull() ?: error("no core: not signed in")
        check(core.isAuthorized()) { "not signed in" }
        core.reportSend(text)
    }
}
