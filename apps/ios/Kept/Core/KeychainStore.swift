import Foundation
import Security

/// Where the API client keeps the access and refresh tokens.
protocol TokenStore: Sendable {
    func token() -> String?
    func refreshToken() -> String?
    func save(token: String, refreshToken: String)
    func clear()
}

/// Tokens in the iOS Keychain, readable after the first unlock so Shortcuts and intents work
/// while the phone is locked but has been unlocked once since boot.
struct KeychainTokenStore: TokenStore {
    private let service = "com.financetracker.kept.tokens"

    func token() -> String? { read(account: "access") }
    func refreshToken() -> String? { read(account: "refresh") }

    func save(token: String, refreshToken: String) {
        write(token, account: "access")
        write(refreshToken, account: "refresh")
    }

    func clear() {
        delete(account: "access")
        delete(account: "refresh")
    }

    private func query(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private func read(account: String) -> String? {
        var q = query(account: account)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ value: String, account: String) {
        delete(account: account)
        var q = query(account: account)
        q[kSecValueData as String] = Data(value.utf8)
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }

    private func delete(account: String) {
        SecItemDelete(query(account: account) as CFDictionary)
    }
}
