import SwiftUI

struct AddTransactionView: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss

    /// Called after a successful save so the list can refresh.
    var onSaved: () async -> Void

    @State private var merchant = ""
    @State private var amountText = ""
    @State private var date = Date()
    @State private var categories: [Category] = []
    @State private var categoryId: String?
    @State private var busy = false
    @State private var error: String?

    private var amount: Decimal? { Money.parse(amountText) }
    private var canSave: Bool { !merchant.trimmingCharacters(in: .whitespaces).isEmpty && amount != nil && categoryId != nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Merchant", text: $merchant)
                    TextField("Amount (₫)", text: $amountText).keyboardType(.numberPad)
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                }
                Section("Category") {
                    Picker("Category", selection: $categoryId) {
                        Text("Choose…").tag(String?.none)
                        ForEach(categories) { category in
                            Text(category.name).tag(String?.some(category.id))
                        }
                    }
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
            }
            .navigationTitle("New transaction")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }.disabled(!canSave || busy)
                }
            }
            .task { await loadCategories() }
        }
    }

    private func loadCategories() async {
        do {
            categories = try await session.api.categories().filter { !$0.archived && !$0.hidden }
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func save() async {
        guard let amount, let categoryId else { return }
        busy = true
        error = nil
        defer { busy = false }
        let name = merchant.trimmingCharacters(in: .whitespaces)
        let request = TransactionRequest(
            date: DateText.wireString(date), rawDescription: name, cleanMerchant: name,
            amount: amount, categoryId: categoryId
        )
        do {
            _ = try await session.api.saveTransactions([request])
            await onSaved()
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
