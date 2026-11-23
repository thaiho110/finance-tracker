import Observation
import SwiftUI

@MainActor
@Observable
final class TransactionsModel {
    private(set) var items: [Transaction] = []
    private(set) var loading = false
    private(set) var error: String?
    private var page = 0
    private var lastPage = false

    func reload(api: APIClient) async {
        page = 0
        lastPage = false
        items = []
        await loadMore(api: api)
    }

    func loadMore(api: APIClient) async {
        guard !loading, !lastPage else { return }
        loading = true
        defer { loading = false }
        do {
            let result = try await api.transactions(page: page)
            items += result.content
            page += 1
            lastPage = page >= result.totalPages
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    func delete(_ item: Transaction, api: APIClient) async {
        do {
            try await api.deleteTransaction(id: item.id)
            items.removeAll { $0.id == item.id }
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct DayGroup: Identifiable {
    let date: String
    let items: [Transaction]
    var id: String { date }

    /// What went out that day (expense categories only, refunds net off).
    var spent: Decimal {
        items.filter { $0.categoryKind == .expense }.reduce(0) { $0 + $1.amount }
    }

    static func group(_ items: [Transaction]) -> [DayGroup] {
        Dictionary(grouping: items, by: \.date)
            .map { DayGroup(date: $0.key, items: $0.value) }
            .sorted { $0.date > $1.date }
    }
}

struct TransactionsView: View {
    @Environment(SessionStore.self) private var session
    @State private var model = TransactionsModel()
    @State private var adding = false
    @State private var query = ""

    private var visible: [Transaction] {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return model.items }
        return model.items.filter {
            $0.cleanMerchant.localizedCaseInsensitiveContains(text) || $0.category.localizedCaseInsensitiveContains(text)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                if let error = model.error {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                }
                ForEach(DayGroup.group(visible)) { group in
                    Section {
                        ForEach(group.items) { item in
                            TransactionRow(item: item)
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        Task { await model.delete(item, api: session.api) }
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                                .contextMenu {
                                    Button {
                                        UIPasteboard.general.string = item.cleanMerchant
                                    } label: {
                                        Label("Copy merchant", systemImage: "doc.on.doc")
                                    }
                                    Button(role: .destructive) {
                                        Task { await model.delete(item, api: session.api) }
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                                .task {
                                    if item.id == model.items.last?.id { await model.loadMore(api: session.api) }
                                }
                        }
                    } header: {
                        HStack {
                            Text(DateText.dayTitle(group.date))
                            Spacer()
                            if group.spent > 0 {
                                Text(Money.format(group.spent)).monospacedDigit()
                            }
                        }
                    }
                }
                if model.loading {
                    ProgressView().frame(maxWidth: .infinity).listRowBackground(Color.clear)
                }
            }
            .overlay {
                if model.items.isEmpty, !model.loading, model.error == nil {
                    ContentUnavailableView(
                        "No transactions yet",
                        systemImage: "tray",
                        description: Text("Tap + to add your first one.")
                    )
                } else if visible.isEmpty, !query.isEmpty {
                    ContentUnavailableView.search(text: query)
                }
            }
            .navigationTitle("Transactions")
            .searchable(text: $query, prompt: "Merchant or category")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { adding = true } label: { Label("Add transaction", systemImage: "plus") }
                }
            }
            .refreshable { await model.reload(api: session.api) }
            .task { await model.reload(api: session.api) }
            .sheet(isPresented: $adding) {
                AddTransactionView { await model.reload(api: session.api) }
                    .presentationDragIndicator(.visible)
            }
        }
    }
}

struct TransactionRow: View {
    let item: Transaction

    var body: some View {
        HStack(spacing: 12) {
            CategoryIcon(name: item.category, kind: item.categoryKind, colorHex: item.categoryColor)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.cleanMerchant).lineLimit(1)
                Text(item.category).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Text((item.categoryKind == .income ? "+" : "") + Money.format(item.amount))
                .monospacedDigit()
                .fontWeight(.medium)
                .foregroundStyle(item.categoryKind == .income ? Color.green : Color.primary)
        }
        .accessibilityElement(children: .combine)
    }
}
