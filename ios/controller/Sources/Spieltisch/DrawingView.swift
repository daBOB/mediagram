import Combine
import ControllerCore
import SwiftUI
import UIKit

/// The paper: draws with one finger at a time, batching points every 50 ms.
final class DrawingView: UIView {
    var color = 0, width = 1          // tool row state
    var onOps: ([DrawOp]) -> Void = { _ in }
    /// What is on the paper, own finished lines included; `paper` caches it as an image.
    private var strokes: [Stroke] = []
    private var paper: UIImage?
    private var pen = Pen<ObjectIdentifier>()
    /// The line in progress plus the finger's predicted points, over the cached paper; never sent.
    private let live = CAShapeLayer()
    private var timer: Timer?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(cgColor: inkColor(PaperStyle.eraserIndex))
        isMultipleTouchEnabled = true // the pen, not UIKit, picks the one finger that draws
        clipsToBounds = true
        live.fillColor = nil
        live.lineCap = .round
        live.lineJoin = .round
        live.actions = ["path": NSNull(), "strokeColor": NSNull(), "lineWidth": NSNull()] // no implicit animation
        layer.addSublayer(live)
    }

    required init?(coder: NSCoder) { fatalError("DrawingView is made in code") }

    /// After undo, clear, resync or a new turn: a finger still down starts a new line at its next point.
    func redraw(_ strokes: [Stroke]) {
        self.strokes = strokes
        pen.inkReplaced()
        showLive()
        paper = nil
        paint([])
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if paper?.size != bounds.size { paint([]) }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        timer?.invalidate()
        timer = nil
        guard window != nil else { return }
        // a target, not a block: the block is @Sendable, the view main-actor bound; leaving the window stops it
        let timer = Timer(timeInterval: 0.05, target: self, selector: #selector(flush), userInfo: nil, repeats: true)
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    @objc private func flush() { onOps(pen.flush()) }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let (x, y) = ink(touch.location(in: self))
            if pen.down(ObjectIdentifier(touch), color: color, width: width, x: x, y: y) { showLive() }
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches where pen.draws(ObjectIdentifier(touch)) {
            for sample in event?.coalescedTouches(for: touch) ?? [touch] {
                let (x, y) = ink(sample.location(in: self))
                pen.move(ObjectIdentifier(touch), x: x, y: y)
            }
            showLive(predicted: event?.predictedTouches(for: touch) ?? [])
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { lift(touches) }

    // an incoming call or Control Center ends the line like a lift
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { lift(touches) }

    private func lift(_ touches: Set<UITouch>) {
        for touch in touches {
            guard let line = pen.stroke, let ops = pen.up(ObjectIdentifier(touch)) else { continue }
            onOps(ops)
            if !line.points.isEmpty {
                strokes.append(line)
                paint([line])
            }
            showLive()
        }
    }

    private func ink(_ point: CGPoint) -> (Int, Int) {
        PaperStyle.quantize(x: point.x, y: point.y, width: bounds.width, height: bounds.height)
    }

    private func viewPoints(_ points: [Int]) -> [CGPoint] {
        let sx = bounds.width / CGFloat(PaperStyle.inkMax), sy = bounds.height / CGFloat(PaperStyle.inkMax)
        return stride(from: 0, to: points.count - 1, by: 2).map {
            CGPoint(x: CGFloat(points[$0]) * sx, y: CGFloat(points[$0 + 1]) * sy)
        }
    }

    private func lineWidth(_ line: Stroke) -> CGFloat {
        PaperStyle.lineWidth(color: line.color, width: line.width, paperWidth: bounds.width)
    }

    private func showLive(predicted: [UITouch] = []) {
        guard let line = pen.stroke, !line.points.isEmpty else {
            live.path = nil
            return
        }
        live.path = curve(viewPoints(line.points) + predicted.map { $0.location(in: self) })
        live.strokeColor = inkColor(line.color)
        live.lineWidth = lineWidth(line)
    }

    /// Paints `lines` over the cached paper; without a cache of this size, the whole drawing afresh.
    private func paint(_ lines: [Stroke]) {
        guard !bounds.isEmpty else { return }
        let base = paper?.size == bounds.size ? paper : nil
        let format = UIGraphicsImageRendererFormat.preferred()
        format.opaque = true
        paper = UIGraphicsImageRenderer(bounds: bounds, format: format).image { context in
            let cg = context.cgContext
            if let base {
                base.draw(at: .zero)
            } else {
                cg.setFillColor(inkColor(PaperStyle.eraserIndex))
                cg.fill(bounds)
            }
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            for line in base == nil ? strokes : lines {
                cg.setStrokeColor(inkColor(line.color))
                cg.setLineWidth(lineWidth(line))
                cg.addPath(curve(viewPoints(line.points)))
                cg.strokePath()
            }
        }
        layer.contents = paper?.cgImage
    }
}

/// The page's curve (`paint-ink.ts`): through the midpoints, with the points as control points.
private func curve(_ points: [CGPoint]) -> CGPath {
    let path = CGMutablePath()
    guard let first = points.first, let last = points.last else { return path }
    path.move(to: first)
    for i in stride(from: 1, to: points.count - 1, by: 1) {
        let mid = CGPoint(x: (points[i].x + points[i + 1].x) / 2, y: (points[i].y + points[i + 1].y) / 2)
        path.addQuadCurve(to: mid, control: points[i])
    }
    path.addLine(to: last) // for a single point a zero-length line: a dot, via the round cap
    return path
}

/// An ink colour by index; an unknown index draws in the first, as on the page.
private func inkColor(_ index: Int) -> CGColor {
    let hex = PaperStyle.colors.indices.contains(index) ? PaperStyle.colors[index] : PaperStyle.colors[0]
    let rgb = Int(hex.dropFirst(), radix: 16) ?? 0
    return CGColor(srgbRed: CGFloat(rgb >> 16 & 0xFF) / 255, green: CGFloat(rgb >> 8 & 0xFF) / 255,
                   blue: CGFloat(rgb & 0xFF) / 255, alpha: 1)
}

/// The paper on the board: 16:9, as large as fits its frame. Redrawn when the board's ink is replaced, undone or
/// cleared; the drawer's own lines only ever paint onto it.
struct PaperView: UIViewRepresentable {
    @ObservedObject var model: BoardModel
    let color: Int
    let width: Int

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> DrawingView {
        let view = DrawingView()
        view.onOps = { [model] ops in ops.forEach { model.act(.draw($0)) } }
        // a sink, not updateUIView: it runs inside the show that replaced the ink, before any later point is sent
        context.coordinator.ink = model.$inkEpoch.removeDuplicates().sink { [weak view, model] _ in
            view?.redraw(model.ink.strokes)
        }
        return view
    }

    func updateUIView(_ view: DrawingView, context: Context) {
        view.color = color
        view.width = width
    }

    // what `.aspectRatio(16/9, contentMode: .fit)` would do, which a representable cannot apply to itself
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: DrawingView, context: Context) -> CGSize? {
        let width = min(proposal.width ?? .infinity, (proposal.height ?? .infinity) * PaperStyle.aspect)
        return width.isFinite ? CGSize(width: width, height: width / PaperStyle.aspect) : nil
    }

    final class Coordinator { var ink: AnyCancellable? }
}
