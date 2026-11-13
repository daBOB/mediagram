import ControllerCore
import SwiftUI

/// The native board over the page while the page asks for one.
struct BoardOverlay: View {
    @ObservedObject var model: BoardModel

    var body: some View {
        ZStack {
            BoardColor.felt.ignoresSafeArea()
            switch model.screen {
            case let .handover(_, drawer):
                BoardHandoverView(drawer: drawer, act: model.act)
            case let .drawing(_, drawer, word, clock):
                BoardDrawingView(model: model, drawer: drawer, word: word, clock: clock)
            case let .picking(_, _, word, _, guessers, canGoBack):
                BoardPickingView(word: word, guessers: guessers, canGoBack: canGoBack, act: model.act)
            case nil:
                EmptyView()
            }
        }
    }
}

/// The look's colours.
enum BoardColor {
    static let felt = Color(hex: "#0f5a43"), deep = Color(hex: "#0a4433"), line = Color(hex: "#1c7258")
    static let card = Color(hex: "#fbf8f1"), cardEdge = Color(hex: "#ddd3bd"), ink = Color(hex: "#0c2a22")
    static let muted = Color(hex: "#a9cdbf"), yellow = Color(hex: "#f6c731"), yellowEdge = Color(hex: "#b98f0d")
}

extension Color {
    /// `#rrggbb`; anything else reads as the card colour.
    init(hex: String) {
        let rgb = Int(hex.dropFirst(), radix: 16) ?? 0xFBF8F1
        self.init(red: Double(rgb >> 16 & 0xFF) / 255, green: Double(rgb >> 8 & 0xFF) / 255, blue: Double(rgb & 0xFF) / 255)
    }
}

/// A player: their initial in their colour.
struct BoardToken: View {
    let player: BoardPlayer
    let size: CGFloat

    var body: some View {
        Text(String(player.name.prefix(1)).uppercased())
            .font(BoardFonts.display(size * 0.48))
            .foregroundColor(BoardColor.ink)
            .frame(width: size, height: size)
            .background(Color(hex: player.color), in: Circle())
    }
}

/// A key of the look: a rounded slab with a ledge beneath it.
struct BoardKey: View {
    let title: String
    var quiet = false
    let size: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(BoardFonts.display(size))
                .foregroundColor(quiet ? BoardColor.card : BoardColor.ink)
                .padding(.horizontal, size * 0.9).padding(.vertical, size * 0.55)
                .frame(minHeight: 56)
                .background(quiet ? BoardColor.line : BoardColor.yellow, in: RoundedRectangle(cornerRadius: 14))
                .background(
                    RoundedRectangle(cornerRadius: 14).fill(quiet ? BoardColor.deep : BoardColor.yellowEdge).offset(y: 5))
        }
        .buttonStyle(.plain)
    }
}
