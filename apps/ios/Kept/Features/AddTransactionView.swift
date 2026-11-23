import SwiftUI
import UIKit

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
    @FocusState private var amountFocused: Bool

    private var amount: Decimal? { Money.parse(amountText) }
    private var canSave: Bool {
        !merchant.trimmingCharacters(in: .whitespaces).isEmpty && amount != nil && categoryId != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("₫").font(.title.weight(.semibold)).foregroundStyle(.secondary)
                        TextField("0", text: $amountText)
                            .font(.system(size: 44, weight: .bold, design: .rounded))
                            .keyboardType(.numberPad)
                            .focused($amountFocused)
                            .onChange(of: amountText) { _, text in
                                let grouped = Money.grouped(text)
                                if grouped != text { amountText = grouped }
                            }
                    }
                    .padding(.vertical, 6)
                }

                Section {
                    TextField("Merchant", text: $merchant)
                        .textInputAutocapitalization(.words)
                    DatePicker("Date", selection: $date, in: ...Date(), displayedComponents: .date)
                }

                Section("Category") {
                    CategoryGrid(categories: categories, selection: $categoryId)
                        .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
                }

                if let error {
                    Section { Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red) }
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
            .task {
                amountFocused = true
                await loadCategories()
            }
        }
    }

    private func loadCategories() async {
        do {
            let all = try await session.api.categories().filter { !$0.archived && !$0.hidden && $0.kind != .transfer }
            // Spending categories first; that's what most entries are.
            categories = all.sorted { ($0.kind == .expense ? 0 : 1, $0.name) < ($1.kind == .expense ? 0 : 1, $1.name) }
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
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            await onSaved()
            dismiss()
        } catch {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            self.error = error.localizedDescription
        }
    }
}

private struct CategoryGrid: View {
    let categories: [Category]
    @Binding var selection: String?

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 8)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 8) {
            ForEach(categories) { category in
                let selected = selection == category.id
                Button {
                    selection = category.id
                } label: {
                    VStack(spacing: 6) {
                        CategoryIcon(name: category.name, kind: category.kind, colorHex: category.color, size: 34)
                        Text(category.name)
                            .font(.caption)
                            .lineLimit(1)
                            .foregroundStyle(.primary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(selected ? Color.accentColor.opacity(0.12) : Color.clear)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(selected ? Color.accentColor : Color.secondary.opacity(0.2), lineWidth: selected ? 2 : 1)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .sensoryFeedback(.selection, trigger: selection)
    }
}
