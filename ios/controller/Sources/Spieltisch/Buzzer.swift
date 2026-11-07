import AudioToolbox
import UIKit

/// One feel per game-night moment. "Your turn" uses the full vibration, which is felt through a
/// table; the others are haptic taps. iPads have no motor: every call simply does nothing there.
final class Buzzer {
    func buzz(_ kind: String) {
        switch kind {
        case "accepted":
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case "turn":
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        case "wrong":
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        case "won":
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        default:
            break
        }
    }
}
