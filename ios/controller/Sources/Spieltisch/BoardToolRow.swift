import ControllerCore
import SwiftUI

/// Under the paper: colours, widths, eraser, undo, clear, and the two keys under the drawing hand.
struct BoardToolRow: View {
    @Binding var color: Int
    @Binding var width: Int
    let act: (BoardAct) -> Void

    var body: some View {
        HStack(spacing: 12) {
            ForEach(0..<PaperStyle.eraserIndex, id: \.self) { index in
                Button { color = index } label: {
                    Circle().fill(Color(hex: PaperStyle.colors[index])).frame(width: 45, height: 45)
                        .overlay(Circle().stroke(BoardColor.card, lineWidth: color == index ? 5 : 0).padding(-7))
                        .frame(width: 60, height: 60)
                }
            }
            separator
            ForEach(0..<PaperStyle.widths.count, id: \.self) { index in
                slot(selected: width == index, action: { width = index }) {
                    Capsule().fill(BoardColor.card).frame(width: 27, height: [4.5, 9, 16][index])
                }
            }
            separator
            slot(selected: color == PaperStyle.eraserIndex, action: { color = PaperStyle.eraserIndex }) {
                icon("eraser")
            }
            slot(selected: false, action: { act(.draw(.undo)) }) { icon("arrow.uturn.backward") }
            slot(selected: false, action: { act(.draw(.clear)) }) { icon("trash") }
            Spacer(minLength: 8)
            BoardKey(title: "Niemand", quiet: true, size: 22) { act(.pick(player: nil)) }
            BoardKey(title: "Erraten!", size: 22) { act(.guessed) }
        }
        .buttonStyle(.plain)
        .frame(height: 66)
    }

    private var separator: some View {
        Capsule().fill(BoardColor.line).frame(width: 3, height: 41)
    }

    private func icon(_ name: String) -> some View {
        Image(systemName: name).font(.system(size: 24, weight: .semibold)).foregroundColor(BoardColor.card)
    }

    private func slot<Content: View>(selected: Bool, action: @escaping () -> Void,
                                     @ViewBuilder _ content: () -> Content) -> some View {
        Button(action: action) {
            content().frame(width: 60, height: 60)
                .background(selected ? BoardColor.line : BoardColor.deep, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(BoardColor.card, lineWidth: selected ? 3.5 : 0))
        }
    }
}
