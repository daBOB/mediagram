package testing

/**
 * [CoreContract] against [FakeCore] — a fresh instance per test, the same
 * isolation a fresh data directory gives the real core in
 * `RealCoreContractTest`. A plain, entirely unconfigured `FakeCore()` is the
 * right fixture for it: its default already has no known set and no
 * profile, matching the real core's own fresh, empty catalog — see
 * [FakeCore.knownSetIds].
 */
class FakeCoreContractTest : CoreContract() {
    override fun core(): FakeCore = FakeCore()
}
