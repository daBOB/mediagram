/// The drawer's chosen colour and width. It outlives a switch to the who-got-it screen and back, and starts over
/// with a new turn.
public struct PenChoice: Equatable, Sendable {
    public var color = 0
    public var width = 1
    public private(set) var turn: Int?

    public init() {}

    /// A different turn than the last one resets the choice.
    public mutating func show(turn: Int) {
        guard turn != self.turn else { return }
        self = PenChoice()
        self.turn = turn
    }
}
