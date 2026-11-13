import XCTest
@testable import ControllerCore

final class BoardWireTests: XCTestCase {
    private func parsed(_ data: Data) throws -> NSDictionary {
        try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? NSDictionary)
    }
    private func parsed(_ s: String) throws -> NSDictionary { try parsed(Data(s.utf8)) }
    private func json(_ act: BoardAct) throws -> NSDictionary { try parsed(JSONEncoder().encode(act)) }

    func testActsEncodeAsTheBoxReadsThem() throws {
        let cases: [(BoardAct, String)] = [
            (.ready, #"{"t":"ready"}"#),
            (.skip, #"{"t":"skip"}"#),
            (.guessed, #"{"t":"guessed"}"#),
            (.keepDrawing, #"{"t":"keep_drawing"}"#),
            (.pick(player: 3), #"{"t":"pick","player":3}"#),
            (.pick(player: nil), #"{"t":"pick","player":null}"#),
            (.draw(.line(color: 0, width: 1, points: [10, 20, 30, 40])),
             #"{"t":"draw","op":{"t":"line","color":0,"width":1,"points":[10,20,30,40]}}"#),
            (.draw(.more(points: [1, 2])), #"{"t":"draw","op":{"t":"more","points":[1,2]}}"#),
            (.draw(.undo), #"{"t":"draw","op":{"t":"undo"}}"#),
            (.draw(.clear), #"{"t":"draw","op":{"t":"clear"}}"#),
        ]
        for (act, text) in cases {
            XCTAssertEqual(try json(act), try parsed(text), text)
        }
    }

    func testAPickOfNobodySendsNull() throws {
        XCTAssertEqual(try json(.pick(player: nil))["player"] as? NSNull, NSNull())
    }

    func testTheShowDecodesFromThePage() throws {
        let anna = ##"{"id":1,"name":"Anna","color":"#2f6fde","avatar":"fox"}"##
        let bo = ##"{"id":2,"name":"Bo","color":"#d8342b","avatar":null}"##
        let ops = ##"[{"t":"line","color":0,"width":1,"points":[1200,3400]},{"t":"more","points":[1230,3450]},{"t":"undo"},{"t":"clear"}]"##
        func show(_ screen: String) throws -> NativeBoardShow {
            let text = ##"{"screen":\##(screen),"ops":\##(ops),"resync":true}"##
            return try JSONDecoder().decode(NativeBoardShow.self, from: Data(text.utf8))
        }
        let a = BoardPlayer(id: 1, name: "Anna", color: "#2f6fde", avatar: "fox")
        let b = BoardPlayer(id: 2, name: "Bo", color: "#d8342b", avatar: nil)
        let expectedOps: [DrawOp] = [.line(color: 0, width: 1, points: [1200, 3400]), .more(points: [1230, 3450]), .undo, .clear]

        let handover = try show(##"{"kind":"handover","turn":2,"drawer":\##(anna)}"##)
        XCTAssertEqual(handover.screen, .handover(turn: 2, drawer: a))
        XCTAssertEqual(handover.ops, expectedOps)
        XCTAssertTrue(handover.resync)

        let running = try show(##"{"kind":"drawing","turn":2,"drawer":\##(anna),"word":"Hund","clock":{"ends_at_epoch_ms":1700000030000,"left_ms":90000,"total_ms":90000}}"##)
        XCTAssertEqual(running.screen, .drawing(turn: 2, drawer: a, word: "Hund",
            clock: NativeClock(endsAtEpochMs: 1700000030000, leftMs: 90000, totalMs: 90000)))

        let picking = try show(##"{"kind":"picking","turn":3,"drawer":\##(anna),"word":"Hund","clock":{"ends_at_epoch_ms":null,"left_ms":31500.5,"total_ms":90000},"guessers":[\##(bo)],"can_go_back":true}"##)
        XCTAssertEqual(picking.screen, .picking(turn: 3, drawer: a, word: "Hund",
            clock: NativeClock(endsAtEpochMs: nil, leftMs: 31500.5, totalMs: 90000), guessers: [b], canGoBack: true))
        XCTAssertEqual(picking.screen.turn, 3)
    }

    func testAnUnknownScreenKindFails() {
        let text = #"{"screen":{"kind":"lobby","turn":1},"ops":[],"resync":false}"#
        XCTAssertThrowsError(try JSONDecoder().decode(NativeBoardShow.self, from: Data(text.utf8)))
    }
}
