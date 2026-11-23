import Foundation

// Mirrors packages/shared/src/types/index.ts and the backend DTOs.
// Dates stay as "YYYY-MM-DD" strings (the backend's LocalDate) so no time zone can shift them.

enum CategoryKind: String, Codable {
    case expense = "EXPENSE"
    case income = "INCOME"
    case transfer = "TRANSFER"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = CategoryKind(rawValue: raw) ?? .unknown
    }
}

enum SourceType: String, Codable {
    case csv = "CSV"
    case ocr = "OCR"
    case manual = "MANUAL"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = SourceType(rawValue: raw) ?? .unknown
    }
}

struct Credentials: Encodable {
    let email: String
    let password: String
}

struct AuthResponse: Codable, Equatable {
    let token: String
    let refreshToken: String
    let id: String
    let email: String
    let role: String
    let permissions: [String]
}

struct ReceiptItem: Codable, Hashable, Identifiable {
    let id: String
    let itemDescription: String
    let price: Decimal
}

struct Transaction: Codable, Hashable, Identifiable {
    let id: String
    let date: String
    let rawDescription: String
    let cleanMerchant: String
    let amount: Decimal
    let category: String
    let categoryId: String
    let categoryKind: CategoryKind
    let categoryColor: String?
    let sourceType: SourceType
    let clientId: String?
    let isDuplicate: Bool
    let createdAt: String?
    let receiptItems: [ReceiptItem]
}

struct TransactionRequest: Encodable {
    let date: String
    let rawDescription: String
    let cleanMerchant: String
    let amount: Decimal
    var categoryId: String?
    var category: String?
    var sourceType: SourceType = .manual
}

struct Page<T: Decodable>: Decodable {
    let content: [T]
    let totalPages: Int
    let totalElements: Int
    let number: Int
    let size: Int
}

struct Category: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let kind: CategoryKind
    let color: String?
    let icon: String?
    let system: Bool
    let archived: Bool
    let hidden: Bool
}

struct MerchantTotal: Codable, Hashable {
    let merchant: String
    let total: Decimal
    let count: Int
}

struct OverviewCategory: Codable, Hashable, Identifiable {
    let categoryId: String
    let name: String
    let color: String?
    let spent: Decimal
    let previousSpent: Decimal
    let budget: Decimal?
    let status: String?

    var id: String { categoryId }
}

struct Overview: Codable {
    let month: String
    let income: Decimal
    let expenses: Decimal
    let net: Decimal
    let previousIncome: Decimal
    let previousExpenses: Decimal
    let budgetsEnabled: Bool
    let categories: [OverviewCategory]
    let topMerchants: [MerchantTotal]
}

/// Spring's ProblemDetail error body.
struct ProblemDetail: Decodable {
    let title: String?
    let detail: String?
    let errors: [String]?

    var message: String? {
        if let errors, !errors.isEmpty {
            return "\(detail ?? "Validation failed"): \(errors.joined(separator: "; "))"
        }
        return detail ?? title
    }
}
