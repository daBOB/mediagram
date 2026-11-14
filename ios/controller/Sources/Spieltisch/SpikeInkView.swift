import UIKit
import WebKit

/// Throwaway latency spike: draws touches locally and forwards batches to the page every 50 ms.
final class SpikeInkView: UIView {
    private weak var web: WKWebView?
    private var points: [Int] = []
    private var batchStart: Double?
    private var timer: Timer?
    private var active: UITouch?
    private var path = UIBezierPath()

    init(web: WKWebView) {
        self.web = web
        super.init(frame: .zero)
        backgroundColor = UIColor(white: 0, alpha: 0.3)
        isMultipleTouchEnabled = false
        let close = UIButton(type: .system)
        close.setTitle("Schließen", for: .normal)
        close.backgroundColor = .white
        close.frame = CGRect(x: 20, y: 20, width: 120, height: 44)
        close.addAction(UIAction { [weak self] _ in self?.timer?.invalidate(); self?.removeFromSuperview() }, for: .touchUpInside)
        addSubview(close)
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in self?.flush() }
    }

    required init?(coder: NSCoder) { fatalError() }

    /// Epoch ms of a touch, from the monotonic touch clock.
    private func epochMs(_ t: UITouch) -> Double {
        Date().timeIntervalSince1970 * 1000 - (ProcessInfo.processInfo.systemUptime - t.timestamp) * 1000
    }

    private func add(_ touch: UITouch, _ event: UIEvent?, began: Bool) {
        for t in event?.coalescedTouches(for: touch) ?? [touch] {
            let p = t.location(in: self)
            if batchStart == nil { batchStart = epochMs(t) }
            points.append(Int(max(0, min(10000, p.x / bounds.width * 10000))))
            points.append(Int(max(0, min(10000, p.y / bounds.height * 10000))))
            if began && t === touch { path.move(to: p) } else { path.addLine(to: p) }
        }
        setNeedsDisplay()
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard active == nil, let t = touches.first else { return }
        active = t
        path = UIBezierPath()
        add(t, event, began: true)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let t = active, touches.contains(t) { add(t, event, began: false) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let t = active, touches.contains(t) { active = nil }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { touchesEnded(touches, with: event) }

    private func flush() {
        guard let t = batchStart, !points.isEmpty else { return }
        let json = "{\"points\":\(points),\"t\":\(t)}"
        points = []
        batchStart = nil
        web?.evaluateJavaScript("window.__spikeInk('\(json)')")
    }

    override func draw(_ rect: CGRect) {
        UIColor.yellow.setStroke()
        path.lineWidth = 4
        path.stroke()
    }
}
