import SwiftUI

struct SettingsView: View {
    @Environment(SessionStore.self) private var session

    var body: some View {
        @Bindable var session = session
        NavigationStack {
            Form {
                Section("Server") {
                    TextField("Server address", text: $session.server)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                }
                if let email = session.email {
                    Section("Account") { Text(email) }
                }
                Section {
                    Button("Sign out", role: .destructive) { Task { await session.logout() } }
                }
            }
            .navigationTitle("Settings")
        }
    }
}
