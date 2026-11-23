import Foundation
import Observation

/// Settings shared by the app and its App Intents (both run in the app's process).
enum AppConfig {
    static let serverKey = "kept.server"
    static let defaultServer = ""

    static var server: String {
        get { UserDefaults.standard.string(forKey: serverKey) ?? defaultServer }
        set { UserDefaults.standard.set(newValue, forKey: serverKey) }
    }
}

/// The signed-in state of the app and the one API client every screen shares.
@MainActor
@Observable
final class SessionStore {
    @ObservationIgnored let api: APIClient

    var server: String {
        didSet {
            AppConfig.server = server
            let value = server
            Task { await api.setServer(value) }
        }
    }

    private(set) var isSignedIn: Bool
    private(set) var email: String?

    init(tokens: TokenStore = KeychainTokenStore()) {
        let server = AppConfig.server
        self.server = server
        self.api = APIClient(server: server, tokens: tokens)
        self.isSignedIn = tokens.token() != nil
        Task { [weak self] in
            await self?.api.onSessionExpired { [weak self] in
                Task { @MainActor in self?.isSignedIn = false }
            }
        }
    }

    func login(email: String, password: String) async throws {
        let auth = try await api.login(Credentials(email: email, password: password))
        self.email = auth.email
        isSignedIn = true
    }

    func logout() async {
        await api.logout()
        email = nil
        isSignedIn = false
    }
}
