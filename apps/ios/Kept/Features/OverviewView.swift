import Charts
import SwiftUI

struct OverviewView: View {
    @Environment(SessionStore.self) private var session
    @State private var month = Date()
    @State private var overview: Overview?
    @State private var error: String?

    private var isCurrentMonth: Bool {
        Calendar.current.isDate(month, equalTo: Date(), toGranularity: .month)
    }

    var body: some View {
        NavigationStack {
            List {
                if let error {
                    Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red)
                }
                if let overview {
                    Section { SummaryCard(overview: overview) }

                    let spending = overview.categories.filter { $0.spent > 0 }
                    if !spending.isEmpty {
                        Section("Where it went") {
                            SpendingDonut(categories: spending, total: overview.expenses)
                            ForEach(spending) { CategoryRow(category: $0) }
                        }
                    }
                    if !overview.topMerchants.isEmpty {
                        Section("Top merchants") {
                            ForEach(overview.topMerchants, id: \.merchant) { merchant in
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(merchant.merchant)
                                        Text("\(merchant.count) transactions").font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(Money.format(merchant.total)).monospacedDigit()
                                }
                            }
                        }
                    }
                }
            }
            .overlay { if overview == nil, error == nil { ProgressView() } }
            .navigationTitle(month.formatted(.dateTime.month(.wide).year()))
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { step(-1) } label: { Label("Previous month", systemImage: "chevron.left") }
                    Button { step(1) } label: { Label("Next month", systemImage: "chevron.right") }
                        .disabled(isCurrentMonth)
                }
            }
            .refreshable { await load() }
            .task(id: month) { await load() }
        }
    }

    private func step(_ delta: Int) {
        if let next = Calendar.current.date(byAdding: .month, value: delta, to: month) {
            overview = nil
            month = next
        }
    }

    private func load() async {
        do {
            overview = try await session.api.overview(month: DateText.monthString(month))
            error = nil
        } catch is CancellationError {
            // The month changed while loading; the new task takes over.
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct SummaryCard: View {
    let overview: Overview

    /// Spending change against last month; nil when there is nothing to compare with.
    private var change: Double? {
        guard overview.previousExpenses > 0 else { return nil }
        return ((overview.expenses - overview.previousExpenses) / overview.previousExpenses).double
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Spent").font(.subheadline).foregroundStyle(.secondary)
                Text(Money.format(overview.expenses))
                    .font(.system(.largeTitle, design: .rounded, weight: .bold))
                    .monospacedDigit()
                if let change {
                    Label(
                        "\(abs(change).formatted(.percent.precision(.fractionLength(0)))) vs last month",
                        systemImage: change > 0 ? "arrow.up.right" : "arrow.down.right"
                    )
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(change > 0 ? Color.orange : Color.green)
                }
            }
            Divider()
            HStack {
                stat("Income", Money.format(overview.income), .green)
                Spacer()
                stat("Net", (overview.net < 0 ? "−" : "") + Money.format(overview.net), overview.net < 0 ? .red : .primary)
            }
        }
        .padding(.vertical, 4)
    }

    private func stat(_ title: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.headline).monospacedDigit().foregroundStyle(color)
        }
    }
}

private struct SpendingDonut: View {
    let categories: [OverviewCategory]
    let total: Decimal

    var body: some View {
        ZStack {
            Chart(categories) { category in
                SectorMark(
                    angle: .value("Spent", category.spent.double),
                    innerRadius: .ratio(0.62),
                    angularInset: 1.5
                )
                .cornerRadius(5)
                .foregroundStyle(CategoryStyle.color(category.color))
            }
            .chartLegend(.hidden)
            VStack(spacing: 2) {
                Text("Total").font(.caption).foregroundStyle(.secondary)
                Text(Money.format(total)).font(.headline).monospacedDigit()
            }
        }
        .frame(height: 210)
        .padding(.vertical, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Spending chart, total \(Money.format(total))")
    }
}

private struct CategoryRow: View {
    let category: OverviewCategory

    private var tint: Color {
        switch category.status {
        case "OVER": return .red
        case "NEAR": return .orange
        default: return .green
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                CategoryIcon(name: category.name, kind: .expense, colorHex: category.color, size: 32)
                Text(category.name)
                Spacer()
                Text(Money.format(category.spent)).monospacedDigit()
            }
            if let budget = category.budget, budget > 0 {
                ProgressView(value: min(category.spent.double / budget.double, 1))
                    .tint(tint)
                Text("of \(Money.format(budget)) budget")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}
