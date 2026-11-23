import SwiftUI

struct OverviewView: View {
    @Environment(SessionStore.self) private var session
    @State private var overview: Overview?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                if let error {
                    Text(error).foregroundStyle(.red)
                }
                if let overview {
                    Section(overview.month) {
                        LabeledContent("Income", value: Money.format(overview.income))
                        LabeledContent("Spent", value: Money.format(overview.expenses))
                        LabeledContent("Net", value: Money.format(overview.net))
                    }
                    Section("Spending by category") {
                        ForEach(overview.categories.filter { $0.spent > 0 }) { category in
                            LabeledContent(category.name, value: Money.format(category.spent))
                        }
                    }
                    if !overview.topMerchants.isEmpty {
                        Section("Top merchants") {
                            ForEach(overview.topMerchants, id: \.merchant) { merchant in
                                LabeledContent("\(merchant.merchant) (\(merchant.count))", value: Money.format(merchant.total))
                            }
                        }
                    }
                }
            }
            .overlay { if overview == nil, error == nil { ProgressView() } }
            .navigationTitle("Overview")
            .refreshable { await load() }
            .task { await load() }
        }
    }

    private func load() async {
        do {
            overview = try await session.api.overview(month: DateText.monthString(Date()))
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
