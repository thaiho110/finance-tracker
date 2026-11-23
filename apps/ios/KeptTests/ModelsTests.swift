import SwiftUI
import XCTest
@testable import Kept

final class ModelsTests: XCTestCase {
    func testDecodesTransactionAndToleratesUnknownEnums() throws {
        let json = """
        {"id":"1","date":"2026-11-01","rawDescription":"STARBUCKS #1","cleanMerchant":"Starbucks",
         "amount":45000,"category":"Coffee","categoryId":"c1","categoryKind":"SOMETHING_NEW",
         "categoryColor":null,"sourceType":"MANUAL","clientId":null,"isDuplicate":false,
         "createdAt":null,"receiptItems":[]}
        """
        let item = try JSONDecoder().decode(Transaction.self, from: Data(json.utf8))
        XCTAssertEqual(item.amount, 45000)
        XCTAssertEqual(item.categoryKind, .unknown)
        XCTAssertEqual(item.sourceType, .manual)
    }

    func testDecodesPage() throws {
        let json = #"{"content":[],"totalPages":0,"totalElements":0,"number":0,"size":30}"#
        let page = try JSONDecoder().decode(Page<Transaction>.self, from: Data(json.utf8))
        XCTAssertTrue(page.content.isEmpty)
    }

    func testProblemDetailMessage() throws {
        let json = #"{"title":"Bad Request","detail":"Validation failed","errors":["amount: must not be zero"]}"#
        let problem = try JSONDecoder().decode(ProblemDetail.self, from: Data(json.utf8))
        XCTAssertEqual(problem.message, "Validation failed: amount: must not be zero")
    }

    func testRequestEncodesAmountAndSource() throws {
        let request = TransactionRequest(
            date: "2026-11-01", rawDescription: "x", cleanMerchant: "x", amount: 45000, categoryId: "c1"
        )
        let data = try JSONEncoder().encode(request)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(object["sourceType"] as? String, "MANUAL")
        XCTAssertEqual((object["amount"] as? NSNumber)?.intValue, 45000)
    }

    func testApiURL() {
        XCTAssertEqual(APIClient.apiURL(for: "http://192.168.1.20:8080/")?.absoluteString, "http://192.168.1.20:8080/api/v1")
        XCTAssertNil(APIClient.apiURL(for: "  "))
    }

    func testServerAddress() {
        XCTAssertFalse(ServerAddress.isInsecureRemote("https://kept.example.com"))
        XCTAssertFalse(ServerAddress.isInsecureRemote("http://192.168.1.20:8080"))
        XCTAssertFalse(ServerAddress.isInsecureRemote("http://localhost:8080"))
        XCTAssertFalse(ServerAddress.isInsecureRemote("http://172.20.0.5:8080"))
        XCTAssertTrue(ServerAddress.isInsecureRemote("http://kept.example.com"))
        XCTAssertTrue(ServerAddress.isInsecureRemote("http://172.200.0.5"))
        XCTAssertEqual(APIClient.healthURL(for: "https://kept.example.com/")?.absoluteString, "https://kept.example.com/actuator/health")
    }

    func testMoney() {
        XCTAssertEqual(Money.format(1_240_000), "₫1,240,000")
        XCTAssertEqual(Money.format(-500), "₫500")
        XCTAssertEqual(Money.parse("45.000"), 45000)
        XCTAssertEqual(Money.parse("45,000"), 45000)
        XCTAssertNil(Money.parse("0"))
        XCTAssertNil(Money.parse("abc"))
        XCTAssertEqual(Money.grouped("45000"), "45,000")
        XCTAssertEqual(Money.grouped("abc"), "")
    }

    func testColorHex() {
        XCTAssertNotNil(Color(hex: "#8B5E3C"))
        XCTAssertNil(Color(hex: "nope"))
        XCTAssertNil(Color(hex: nil))
    }

    func testDayGroupsAreNewestFirst() throws {
        func make(_ id: String, _ date: String) throws -> Transaction {
            let json = """
            {"id":"\(id)","date":"\(date)","rawDescription":"x","cleanMerchant":"x","amount":10,
             "category":"c","categoryId":"c","categoryKind":"EXPENSE","categoryColor":null,
             "sourceType":"MANUAL","clientId":null,"isDuplicate":false,"createdAt":null,"receiptItems":[]}
            """
            return try JSONDecoder().decode(Transaction.self, from: Data(json.utf8))
        }
        let groups = DayGroup.group([try make("1", "2026-11-01"), try make("2", "2026-11-03"), try make("3", "2026-11-03")])
        XCTAssertEqual(groups.map(\.date), ["2026-11-03", "2026-11-01"])
        XCTAssertEqual(groups.first?.spent, 20)
    }
}
