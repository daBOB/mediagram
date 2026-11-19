import ControllerCore
import SwiftUI

/// The iPad changes hands: the word stays hidden until the drawer taps.
struct BoardHandoverView: View {
    let drawer: BoardPlayer
    let act: (BoardAct) -> Void

    var body: some View {
        VStack(spacing: 20) {
            BoardToken(player: drawer, size: 150)
            Text("\(drawer.name) ist dran")
                .font(BoardFonts.display(55)).foregroundColor(BoardColor.card)
            BoardKey(title: "Ich bin dran", size: 33) { act(.ready) }.padding(.top, 15)
            Button { act(.skip) } label: {
                Text("Überspringen").font(BoardFonts.text(22)).foregroundColor(BoardColor.muted)
                    .padding(.horizontal, 28).padding(.vertical, 16)
            }
            .buttonStyle(.plain)
        }
        .multilineTextAlignment(.center)
    }
}
