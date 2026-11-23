import SwiftUI

@main
struct KeptApp: App {
    @State private var session = SessionStore()
    @State private var lock = AppLock()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(lock)
        }
    }
}

struct RootView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppLock.self) private var lock
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            if session.isSignedIn {
                TabView {
                    OverviewView()
                        .tabItem { Label("Overview", systemImage: "chart.pie.fill") }
                    TransactionsView()
                        .tabItem { Label("Transactions", systemImage: "list.bullet.rectangle.fill") }
                    SettingsView()
                        .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                }
            } else {
                LoginView()
            }

            // Hides balances in the app switcher while the lock is on.
            if lock.enabled, scenePhase != .active {
                Rectangle().fill(.regularMaterial).ignoresSafeArea()
            }
            if lock.locked, session.isSignedIn {
                LockView()
            }
        }
        .task { await lock.unlock() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                lock.lockIfNeeded()
            } else if phase == .active {
                Task { await lock.unlock() }
            }
        }
    }
}

struct LockView: View {
    @Environment(AppLock.self) private var lock

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.fill")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)
            Text("Kept is locked").font(.title3.weight(.semibold))
            Button("Unlock") { Task { await lock.unlock() } }
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.background)
    }
}
