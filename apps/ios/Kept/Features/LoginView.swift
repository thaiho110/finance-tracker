import SwiftUI

struct LoginView: View {
    @Environment(SessionStore.self) private var session
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @State private var testing = false
    @State private var connection: Bool?

    var body: some View {
        @Bindable var session = session
        NavigationStack {
            Form {
                Section {
                    TextField("https://kept.example.com", text: $session.server)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .onChange(of: session.server) { _, _ in connection = nil }
                    Button {
                        Task { await testConnection() }
                    } label: {
                        HStack {
                            Text("Test connection")
                            Spacer()
                            if testing {
                                ProgressView()
                            } else if let connection {
                                Image(systemName: connection ? "checkmark.circle.fill" : "xmark.circle.fill")
                                    .foregroundStyle(connection ? Color.green : Color.red)
                            }
                        }
                    }
                    .disabled(session.server.trimmingCharacters(in: .whitespaces).isEmpty || testing)
                } header: {
                    Text("Server")
                } footer: {
                    if ServerAddress.isInsecureRemote(session.server) {
                        Text("iOS blocks plain http:// to servers outside your home network. Use an https:// address.")
                            .foregroundStyle(.orange)
                    } else if connection == false {
                        Text("Couldn't reach the server. Check the address and that the backend is running.")
                            .foregroundStyle(.red)
                    } else {
                        Text("The address of your deployed Kept backend, for example https://kept.yourdomain.com.")
                    }
                }
                Section("Account") {
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
                Section {
                    Button {
                        Task { await signIn() }
                    } label: {
                        if busy { ProgressView() } else { Text("Sign in") }
                    }
                    .disabled(busy || email.isEmpty || password.isEmpty || session.server.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .navigationTitle("Kept")
        }
    }

    private func testConnection() async {
        testing = true
        defer { testing = false }
        connection = await session.api.checkHealth(server: session.server)
    }

    private func signIn() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            try await session.login(email: email, password: password)
        } catch {
            self.error = error.localizedDescription
        }
    }
}
