import Foundation

enum APIError: LocalizedError {
    case notSignedIn
    case invalidURL
    case server(status: Int, message: String?)
    case transport(Error)

    var errorDescription: String? {
        switch self {
        case .notSignedIn: return "You are signed out. Sign in again."
        case .invalidURL: return "The server address is not valid."
        case let .server(status, message): return message ?? "The server returned an error (\(status))."
        case let .transport(error): return error.localizedDescription
        }
    }
}

/// HTTP client for the Kept API: adds X-Client-Id and the bearer token, and on a 401 refreshes the
/// token once (concurrent 401s share the same refresh) before retrying — same behaviour as the
/// shared TypeScript client in packages/shared.
actor APIClient {
    static let clientId = "finance-tracker-mobile"

    private var baseURL: URL?
    private let tokens: TokenStore
    private let session: URLSession
    private var refreshTask: Task<String?, Never>?
    private var sessionExpired: (@Sendable () -> Void)?

    init(server: String, tokens: TokenStore, session: URLSession = .shared) {
        self.baseURL = APIClient.apiURL(for: server)
        self.tokens = tokens
        self.session = session
    }

    /// `http://host:8080` -> `http://host:8080/api/v1`
    static func apiURL(for server: String) -> URL? {
        var trimmed = server.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        guard !trimmed.isEmpty, let url = URL(string: trimmed + "/api/v1"), url.host != nil else { return nil }
        return url
    }

    /// `http://host:8080` -> `http://host:8080/actuator/health`
    static func healthURL(for server: String) -> URL? {
        apiURL(for: server)?.deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("actuator/health")
    }

    /// True when the server at `server` answers its health endpoint.
    func checkHealth(server: String) async -> Bool {
        guard let url = APIClient.healthURL(for: server) else { return false }
        var request = URLRequest(url: url)
        request.timeoutInterval = 8
        request.setValue(Self.clientId, forHTTPHeaderField: "X-Client-Id")
        guard let (_, response) = try? await session.data(for: request) else { return false }
        return (response as? HTTPURLResponse)?.statusCode == 200
    }

    func setServer(_ server: String) { baseURL = APIClient.apiURL(for: server) }
    func onSessionExpired(_ handler: @escaping @Sendable () -> Void) { sessionExpired = handler }

    // MARK: - Endpoints

    func login(_ credentials: Credentials) async throws -> AuthResponse {
        let auth: AuthResponse = try await send("/auth/login", method: "POST", body: credentials, authenticated: false)
        tokens.save(token: auth.token, refreshToken: auth.refreshToken)
        return auth
    }

    func logout() { tokens.clear() }

    var hasSession: Bool { tokens.token() != nil }

    func transactions(page: Int = 0, size: Int = 30) async throws -> Page<Transaction> {
        try await send("/transactions", query: ["page": "\(page)", "size": "\(size)", "sort": "date,desc"])
    }

    func saveTransactions(_ items: [TransactionRequest]) async throws -> [Transaction] {
        try await send("/transactions/batch", method: "POST", body: items)
    }

    func deleteTransaction(id: String) async throws {
        _ = try await perform("/transactions/\(id)", method: "DELETE")
    }

    func categories() async throws -> [Category] {
        try await send("/categories")
    }

    func overview(month: String? = nil) async throws -> Overview {
        try await send("/overview", query: month.map { ["month": $0] } ?? [:])
    }

    // MARK: - Plumbing

    private func send<T: Decodable>(
        _ path: String, method: String = "GET", query: [String: String] = [:],
        body: (any Encodable)? = nil, authenticated: Bool = true
    ) async throws -> T {
        let data = try await perform(path, method: method, query: query, body: body, authenticated: authenticated)
        do {
            return try Self.decoder.decode(T.self, from: data)
        } catch {
            throw APIError.server(status: 200, message: "Unexpected response from the server.")
        }
    }

    private func perform(
        _ path: String, method: String = "GET", query: [String: String] = [:],
        body: (any Encodable)? = nil, authenticated: Bool = true
    ) async throws -> Data {
        var token: String?
        if authenticated {
            guard let saved = tokens.token() else { throw APIError.notSignedIn }
            token = saved
        }
        var (data, response) = try await execute(path, method, query, body, token)
        if response.statusCode == 401, authenticated {
            guard let fresh = await refreshOnce() else { throw APIError.notSignedIn }
            (data, response) = try await execute(path, method, query, body, fresh)
        }
        guard (200 ..< 300).contains(response.statusCode) else {
            let problem = try? Self.decoder.decode(ProblemDetail.self, from: data)
            throw APIError.server(status: response.statusCode, message: problem?.message)
        }
        return data
    }

    private func execute(
        _ path: String, _ method: String, _ query: [String: String], _ body: (any Encodable)?, _ token: String?
    ) async throws -> (Data, HTTPURLResponse) {
        guard let baseURL, var components = URLComponents(url: baseURL.appendingPathComponent(String(path.drop(while: { $0 == "/" }))), resolvingAgainstBaseURL: false)
        else { throw APIError.invalidURL }
        if !query.isEmpty {
            components.queryItems = query.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 20
        request.setValue(Self.clientId, forHTTPHeaderField: "X-Client-Id")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try Self.encoder.encode(body)
        }
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw APIError.invalidURL }
            return (data, http)
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.transport(error)
        }
    }

    private func refreshOnce() async -> String? {
        if let refreshTask { return await refreshTask.value }
        let task = Task { await self.refresh() }
        refreshTask = task
        let result = await task.value
        refreshTask = nil
        return result
    }

    private func refresh() async -> String? {
        guard let refreshToken = tokens.refreshToken(), let baseURL else {
            expireSession()
            return nil
        }
        var request = URLRequest(url: baseURL.appendingPathComponent("auth/refresh"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(refreshToken)", forHTTPHeaderField: "Authorization")
        request.setValue(Self.clientId, forHTTPHeaderField: "X-Client-Id")
        guard let (data, response) = try? await session.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let auth = try? Self.decoder.decode(AuthResponse.self, from: data)
        else {
            expireSession()
            return nil
        }
        tokens.save(token: auth.token, refreshToken: auth.refreshToken)
        return auth.token
    }

    private func expireSession() {
        tokens.clear()
        sessionExpired?()
    }

    private static let decoder = JSONDecoder()
    private static let encoder = JSONEncoder()
}

enum ServerAddress {
    /// Plain http:// to a host that isn't on the local network. iOS blocks these requests, so warn early.
    static func isInsecureRemote(_ text: String) -> Bool {
        guard let url = URL(string: text.trimmingCharacters(in: .whitespaces)),
              url.scheme?.lowercased() == "http", let host = url.host?.lowercased() else { return false }
        if host == "localhost" || host == "127.0.0.1" || host.hasSuffix(".local") { return false }
        if host.hasPrefix("192.168.") || host.hasPrefix("10.") { return false }
        if host.hasPrefix("172."), let second = Int(host.split(separator: ".").dropFirst().first ?? ""),
           (16 ... 31).contains(second) { return false }
        return true
    }
}
