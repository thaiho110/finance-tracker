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

struct TransactionsView: View {
    @Environment(SessionStore.self) private var session
    @State private var model = TransactionsModel()
    @State private var adding = false

    var body: some View {
        NavigationStack {
            List {
                if let error = model.error {
                    Text(error).foregroundStyle(.red)
                }
                ForEach(model.items) { item in
                    TransactionRow(item: item)
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                Task { await model.delete(item, api: session.api) }
                            }
                        }
                        .task {
                            if item.id == model.items.last?.id { await model.loadMore(api: session.api) }
                        }
                }
                if model.loading { ProgressView().frame(maxWidth: .infinity) }
            }
            .overlay {
                if model.items.isEmpty, !model.loading, model.error == nil {
                    ContentUnavailableView("No transactions yet", systemImage: "tray")
                }
            }
            .navigationTitle("Transactions")
            .toolbar {
                Button { adding = true } label: { Label("Add", systemImage: "plus") }
            }
            .refreshable { await model.reload(api: session.api) }
            .task { await model.reload(api: session.api) }
            .sheet(isPresented: $adding) {
                AddTransactionView { await model.reload(api: session.api) }
            }
        }
    }
}

struct TransactionRow: View {
    let item: Transaction

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.cleanMerchant).font(.body)
                Text("\(item.category) · \(DateText.display(item.date))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(Money.format(item.amount))
                .monospacedDigit()
                .foregroundStyle(item.categoryKind == .income ? Color.green : Color.primary)
        }
    }
}
