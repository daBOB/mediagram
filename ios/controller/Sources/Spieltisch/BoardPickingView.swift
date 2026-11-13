import ControllerCore
import SwiftUI

/// Who got it: one big button per guesser, then nobody, or back to drawing.
struct BoardPickingView: View {
    let word: String
    let guessers: [BoardPlayer]
    let canGoBack: Bool
    let act: (BoardAct) -> Void

    var body: some View {
        VStack(spacing: 22) {
            Text(word).font(BoardFonts.display(34)).tracking(1.4).foregroundColor(BoardColor.ink)
                .padding(.horizontal, 24).padding(.vertical, 8)
                .background(BoardColor.card, in: RoundedRectangle(cornerRadius: 10))
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 20), count: 3), spacing: 20) {
                ForEach(guessers, id: \.id) { player in
                    Button { act(.pick(player: player.id)) } label: {
                        HStack(spacing: 14) {
                            BoardToken(player: player, size: 53)
                            Text(player.name).font(BoardFonts.display(26)).foregroundColor(BoardColor.card).lineLimit(1)
                            Spacer(minLength: 0)
                        }
                        .padding(16).frame(minHeight: 84)
                        .background(BoardColor.deep, in: RoundedRectangle(cornerRadius: 16))
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color(hex: "#073326")).offset(y: 5))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 40)
            BoardKey(title: "Niemand", quiet: true, size: 24) { act(.pick(player: nil)) }
            if canGoBack {
                Button { act(.keepDrawing) } label: {
                    Text("Doch nicht – weiterzeichnen").font(BoardFonts.text(22)).foregroundColor(BoardColor.muted)
                        .underline().padding(16)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
