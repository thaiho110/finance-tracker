import Foundation
import LocalAuthentication
import Observation

/// Optional Face ID / Touch ID / passcode lock. Locks when the app goes to the background.
@MainActor
@Observable
final class AppLock {
    private static let key = "kept.requireBiometrics"

    var enabled: Bool {
        didSet { UserDefaults.standard.set(enabled, forKey: Self.key) }
    }

    private(set) var locked: Bool

    init() {
        let enabled = UserDefaults.standard.bool(forKey: Self.key)
        self.enabled = enabled
        self.locked = enabled
    }

    func lockIfNeeded() {
        if enabled { locked = true }
    }

    func unlock() async {
        guard locked else { return }
        let context = LAContext()
        var error: NSError?
        // With no passcode set there is nothing to authenticate with: don't lock the user out.
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            locked = false
            return
        }
        do {
            if try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Unlock Kept") {
                locked = false
            }
        } catch {
            // Cancelled or failed: stay locked; the lock screen has an Unlock button.
        }
    }
}
