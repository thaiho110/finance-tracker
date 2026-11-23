import SwiftUI

struct SettingsView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppLock.self) private var lock

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "–"
    }

    var body: some View {
        @Bindable var session = session
        @Bindable var lock = lock
        NavigationStack {
            Form {
                if let email = session.email {
                    Section("Account") {
                        Label(email, systemImage: "person.crop.circle.fill")
                    }
                }
                Section {
                    Toggle(isOn: $lock.enabled) {
                        Label("Require Face ID", systemImage: "faceid")
                    }
                } header: {
                    Text("Security")
                } footer: {
                    Text("Locks Kept whenever you leave the app.")
                }
                Section {
                    TextField("http://192.168.1.20:8080", text: $session.server)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                } header: {
                    Text("Server")
                } footer: {
                    Text("Your own Kept backend. Use https:// for anything outside your home network.")
                }
                Section {
                    Button("Sign out", role: .destructive) { Task { await session.logout() } }
                }
                Section {
                    LabeledContent("Version", value: version)
                }
            }
            .navigationTitle("Settings")
        }
    }
}
