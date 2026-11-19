import Foundation

/// The paper's look and geometry; the same values as the page's ink style.
public enum PaperStyle {
    public static let aspect = 16.0 / 9.0
    public static let inkMax = 10000
    /// Index 5 is the eraser: ink in the paper colour.
    public static let colors = ["#1b1b1b", "#d8342b", "#2c6bed", "#2f9e44", "#f2a20e", "#fbf8f1"]
    public static let eraserIndex = 5
    /// Fractions of the paper's width.
    public static let widths = [0.005, 0.01, 0.017]
    public static let eraserScale = 3.0

    public static func lineWidth(color: Int, width: Int, paperWidth: Double) -> Double {
        widths[min(max(width, 0), widths.count - 1)] * paperWidth * (color == eraserIndex ? eraserScale : 1)
    }

    /// A point on a paper of `width`×`height` as 0...inkMax each, clamped to the paper.
    public static func quantize(x: Double, y: Double, width: Double, height: Double) -> (Int, Int) {
        func q(_ v: Double, _ extent: Double) -> Int {
            guard extent > 0, v.isFinite else { return 0 }
            return Int((min(max(v / extent, 0), 1) * Double(inkMax)).rounded())
        }
        return (q(x, width), q(y, height))
    }
}
