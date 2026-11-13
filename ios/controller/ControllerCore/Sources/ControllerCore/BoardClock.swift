import Foundation

public enum BoardClock {
    /// Running: time to the end on the device's wall clock; paused: the time it stands at.
    public static func leftMs(_ clock: NativeClock, nowEpochMs: Double) -> Double {
        guard let ends = clock.endsAtEpochMs else { return clock.leftMs }
        return max(0, ends - nowEpochMs)
    }

    /// Whole seconds rounded up, as "m:ss".
    public static func label(leftMs: Double) -> String {
        let s = Int((max(0, leftMs) / 1000).rounded(.up))
        return "\(s / 60):" + (s % 60 < 10 ? "0" : "") + "\(s % 60)"
    }
}
