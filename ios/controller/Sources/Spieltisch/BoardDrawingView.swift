import ControllerCore
import SwiftUI

/// The drawer's screen: top bar, the paper, the tool row.
struct BoardDrawingView: View {
    @ObservedObject var model: BoardModel
    let drawer: BoardPlayer
    let word: String
    let clock: NativeClock
    @State private var color = 0
    @State private var width = 1

    var body: some View {
        VStack(spacing: 0) {
            topBar
            PaperView(model: model, color: color, width: width)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            BoardToolRow(color: $color, width: $width, act: model.act)
                .padding(.top, 10).padding(.bottom, 14)
        }
        .padding(.horizontal, 20)
    }

    private var topBar: some View {
        HStack(spacing: 16) {
            BoardToken(player: drawer, size: 51)
            (Text(drawer.name).font(BoardFonts.display(21)).foregroundColor(BoardColor.card)
                + Text(" zeichnet").font(BoardFonts.display(21, weight: 600)).foregroundColor(BoardColor.muted))
            Text(word).font(BoardFonts.display(35)).tracking(1.4).foregroundColor(BoardColor.ink)
                .lineLimit(1).minimumScaleFactor(0.6)
                .padding(.horizontal, 22).padding(.vertical, 8)
                .background(BoardColor.card, in: RoundedRectangle(cornerRadius: 10))
                .background(RoundedRectangle(cornerRadius: 10).fill(BoardColor.cardEdge).offset(y: 4))
                .padding(.leading, 6)
            Spacer(minLength: 8)
            TimelineView(.periodic(from: .now, by: 0.25)) { context in
                let left = BoardClock.leftMs(clock, nowEpochMs: context.date.timeIntervalSince1970 * 1000)
                HStack(spacing: 10) {
                    ClockRing(fraction: clock.totalMs > 0 ? left / clock.totalMs : 0)
                    Text(BoardClock.label(leftMs: left)).font(BoardFonts.display(35)).monospacedDigit()
                        .foregroundColor(BoardColor.card)
                }
            }
        }
        .frame(height: 92)
    }
}

private struct ClockRing: View {
    let fraction: Double

    var body: some View {
        ZStack {
            Circle().stroke(BoardColor.deep, lineWidth: 9)
            Circle().trim(from: 0, to: min(max(fraction, 0), 1))
                .stroke(BoardColor.yellow, lineWidth: 9).rotationEffect(.degrees(-90))
        }
        .frame(width: 41, height: 41)
    }
}
