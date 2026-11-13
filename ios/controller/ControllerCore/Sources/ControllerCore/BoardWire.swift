import Foundation

/// One drawing step, as the box and the page spell it.
public enum DrawOp: Codable, Equatable, Sendable {
    case line(color: Int, width: Int, points: [Int])
    case more(points: [Int])
    case undo
    case clear

    private enum Key: String, CodingKey { case t, color, width, points }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Key.self)
        switch try c.decode(String.self, forKey: .t) {
        case "line":
            self = .line(color: try c.decode(Int.self, forKey: .color), width: try c.decode(Int.self, forKey: .width),
                         points: try c.decode([Int].self, forKey: .points))
        case "more": self = .more(points: try c.decode([Int].self, forKey: .points))
        case "undo": self = .undo
        case "clear": self = .clear
        case let t: throw DecodingError.dataCorruptedError(forKey: .t, in: c, debugDescription: "unknown op \(t)")
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: Key.self)
        switch self {
        case let .line(color, width, points):
            try c.encode("line", forKey: .t); try c.encode(color, forKey: .color)
            try c.encode(width, forKey: .width); try c.encode(points, forKey: .points)
        case let .more(points): try c.encode("more", forKey: .t); try c.encode(points, forKey: .points)
        case .undo: try c.encode("undo", forKey: .t)
        case .clear: try c.encode("clear", forKey: .t)
        }
    }
}

/// What the board tells the page, which forwards it to the box.
public enum BoardAct: Encodable, Equatable, Sendable {
    case ready, skip, guessed, keepDrawing
    case pick(player: Int?)
    case draw(DrawOp)

    private enum Key: String, CodingKey { case t, player, op }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: Key.self)
        switch self {
        case .ready: try c.encode("ready", forKey: .t)
        case .skip: try c.encode("skip", forKey: .t)
        case .guessed: try c.encode("guessed", forKey: .t)
        case .keepDrawing: try c.encode("keep_drawing", forKey: .t)
        case let .pick(player):
            try c.encode("pick", forKey: .t)
            try c.encode(player, forKey: .player) // nil stays an explicit null: nobody
        case let .draw(op): try c.encode("draw", forKey: .t); try c.encode(op, forKey: .op)
        }
    }
}

public struct BoardPlayer: Codable, Equatable, Sendable {
    public let id: Int
    public let name: String
    public let color: String
    public let avatar: String?

    public init(id: Int, name: String, color: String, avatar: String?) {
        self.id = id; self.name = name; self.color = color; self.avatar = avatar
    }
}

public struct NativeClock: Codable, Equatable, Sendable {
    /// The device's wall clock in ms when the clock runs; nil when it is stopped at `leftMs`.
    public let endsAtEpochMs: Double?
    public let leftMs: Double
    public let totalMs: Double

    public init(endsAtEpochMs: Double?, leftMs: Double, totalMs: Double) {
        self.endsAtEpochMs = endsAtEpochMs; self.leftMs = leftMs; self.totalMs = totalMs
    }

    private enum CodingKeys: String, CodingKey {
        case endsAtEpochMs = "ends_at_epoch_ms", leftMs = "left_ms", totalMs = "total_ms"
    }
}

public enum NativeScreen: Decodable, Equatable, Sendable {
    case handover(turn: Int, drawer: BoardPlayer)
    case drawing(turn: Int, drawer: BoardPlayer, word: String, clock: NativeClock)
    case picking(turn: Int, drawer: BoardPlayer, word: String, clock: NativeClock, guessers: [BoardPlayer], canGoBack: Bool)

    public var turn: Int {
        switch self {
        case let .handover(turn, _), let .drawing(turn, _, _, _), let .picking(turn, _, _, _, _, _): return turn
        }
    }

    private enum Key: String, CodingKey { case kind, turn, drawer, word, clock, guessers, canGoBack = "can_go_back" }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Key.self)
        let turn = try c.decode(Int.self, forKey: .turn)
        let drawer = try c.decode(BoardPlayer.self, forKey: .drawer)
        switch try c.decode(String.self, forKey: .kind) {
        case "handover": self = .handover(turn: turn, drawer: drawer)
        case "drawing":
            self = .drawing(turn: turn, drawer: drawer, word: try c.decode(String.self, forKey: .word),
                            clock: try c.decode(NativeClock.self, forKey: .clock))
        case "picking":
            self = .picking(turn: turn, drawer: drawer, word: try c.decode(String.self, forKey: .word),
                            clock: try c.decode(NativeClock.self, forKey: .clock),
                            guessers: try c.decode([BoardPlayer].self, forKey: .guessers),
                            canGoBack: try c.decode(Bool.self, forKey: .canGoBack))
        case let k: throw DecodingError.dataCorruptedError(forKey: .kind, in: c, debugDescription: "unknown screen \(k)")
        }
    }
}

/// What the page hands `showBoard`: the screen, the turn's whole drawing, and whether to redraw from it.
public struct NativeBoardShow: Decodable, Equatable, Sendable {
    public let screen: NativeScreen
    public let ops: [DrawOp]
    public let resync: Bool
}
