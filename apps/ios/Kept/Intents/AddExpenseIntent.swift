import AppIntents
import Foundation

/// "Add expense" for Shortcuts and Siri: quick capture without opening the app.
/// Runs in the app's process, so it reuses the saved server address and the Keychain tokens.
struct AddExpenseIntent: AppIntent {
    static var title: LocalizedStringResource = "Add expense to Kept"
    static var description = IntentDescription("Logs an expense in your Kept account.")

    @Parameter(title: "Amount (₫)")
    var amount: Int

    @Parameter(title: "Merchant")
    var merchant: String

    @Parameter(title: "Category name", description: "Leave empty to use \"Other\" (or your first expense category).")
    var category: String?

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let api = APIClient(server: AppConfig.server, tokens: KeychainTokenStore())
        guard await api.hasSession else { throw APIError.notSignedIn }

        let categories = try await api.categories().filter { $0.kind == .expense && !$0.archived && !$0.hidden }
        let wanted = (category ?? "").trimmingCharacters(in: .whitespaces).lowercased()
        let chosen = categories.first { !wanted.isEmpty && $0.name.lowercased() == wanted }
            ?? categories.first { $0.name.lowercased() == "other" }
            ?? categories.first
        guard let chosen else { throw APIError.server(status: 0, message: "No expense category to file this under.") }

        let name = merchant.trimmingCharacters(in: .whitespaces)
        let request = TransactionRequest(
            date: DateText.wireString(Date()), rawDescription: name, cleanMerchant: name,
            amount: Decimal(amount), categoryId: chosen.id
        )
        _ = try await api.saveTransactions([request])
        return .result(dialog: "Added \(Money.format(Decimal(amount))) at \(name) under \(chosen.name).")
    }
}

struct KeptShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddExpenseIntent(),
            phrases: ["Add an expense in \(.applicationName)"],
            shortTitle: "Add expense",
            systemImageName: "plus.circle"
        )
    }
}
