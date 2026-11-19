/// One tap per board screen: a double tap on „Überspringen“ must not skip two drawers.
/// Draws always go through and spend nothing. A new screen starts with a new latch.
public struct ActLatch: Sendable {
    private var spent = false

    public init() {}

    /// Whether `act` goes to the page.
    public mutating func pass(_ act: BoardAct) -> Bool {
        if case .draw = act { return true }
        defer { spent = true }
        return !spent
    }
}
