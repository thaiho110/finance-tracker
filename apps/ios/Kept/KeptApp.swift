import SwiftUI

@main
struct KeptApp: App {
    @State private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
        }
    }
}

struct RootView: View {
    @Environment(SessionStore.self) private var session

    var body: some View {
        if session.isSignedIn {
            TabView {
                OverviewView()
                    .tabItem { Label("Overview", systemImage: "chart.pie") }
                TransactionsView()
                    .tabItem { Label("Transactions", systemImage: "list.bullet") }
                SettingsView()
                    .tabItem { Label("Settings", systemImage: "gearshape") }
            }
        } else {
            LoginView()
        }
    }
}
