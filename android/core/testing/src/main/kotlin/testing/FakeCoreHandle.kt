package testing

import uniffi.mediagram_core.CoreInterface

/**
 * What a `CoreProvider` build lambda must return to satisfy its close fence
 * — both halves of the generated `Core`'s own contract, bundled as one type
 * so a test class that needs to override specific calls can delegate both
 * with a single `by` clause instead of two.
 */
interface FakeCoreHandle : CoreInterface, AutoCloseable
