import Foundation

public enum LarpPaymentHelper {
    public static let testPaymentJson = "{\"type\":\"card\", \"id\":\"mg-larp-test\"}"

    private static let testBins = [
        "424242", "400000", "400005", "403550", "411111", "401288",
        "555555", "520082", "510510", "222300", "222100", "222240",
        "601111", "601100", "601198",
        "371449", "378282", "378734",
        "305693", "362272", "356600",
    ]

    public static func isLarpPaymentEnabled() -> Bool { true }

    public static func isLarpTestPaymentJson(_ paymentJson: String?) -> Bool {
        paymentJson?.contains("mg-larp-test") == true
    }

    public static func matchesTestCardInput(cardNumber: String, expiry: String, cvv: String) -> Bool {
        guard isLarpPaymentEnabled(), isTestCardNumber(cardNumber), matchesCvv(cvv) else { return false }
        return matchesExpiry(expiry, loose: false)
    }

    public static func isTestCardNumber(_ cardNumber: String) -> Bool {
        let n = normalizeCardNumber(cardNumber)
        guard !n.isEmpty, n.allSatisfy(\.isNumber) else { return false }
        guard n.count == 15 || n.count == 16 else { return false }
        guard passesLuhn(n) else { return false }
        if testBins.contains(where: { n.hasPrefix($0) }) { return true }
        return n.count == 16 && n.hasSuffix("1111")
    }

    public static func generateRandomTestCard() -> String {
        let bin = testBins.randomElement() ?? "424242"
        let targetLen = (bin.hasPrefix("34") || bin.hasPrefix("37")) ? 15 : 16
        var digits = bin
        while digits.count < targetLen - 1 {
            digits.append(String(Int.random(in: 0 ... 9)))
        }
        digits.append(String(computeLuhnCheckDigit(digits)))
        return digits
    }

    public static func getExampleTestCards(randomCount: Int) -> [String] {
        var cards = [
            "4242424242424242",
            "5555555555554444",
            "4111111111111111",
            "4000056655665556",
            "2223000048400011",
            "5200828282828210",
            "6011111111111117",
            "378282246310005",
        ]
        for _ in 0 ..< randomCount {
            cards.append(generateRandomTestCard())
        }
        return cards
    }

    public static func formatCardNumber(_ raw: String) -> String {
        let n = normalizeCardNumber(raw)
        var result = ""
        for (index, char) in n.enumerated() {
            if index > 0, index % 4 == 0 { result.append(" ") }
            result.append(char)
        }
        return result
    }

    public static func creditStarsPurchase(_ stars: Int64) {
        guard stars > 0 else { return }
        SGLarpSettings.fakeStarsAmount = LarpBalanceHelper.getStarsBalance() + stars
        LarpBalanceHelper.notifyBalanceChanged(ton: false)
        LarpTransactionHelper.addStarsTopup(stars)
    }

    private static func normalizeCardNumber(_ raw: String?) -> String {
        raw?.replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "-", with: "") ?? ""
    }

    private static func matchesCvv(_ cvv: String?) -> Bool {
        let code = cvv?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if code.isEmpty { return true }
        return code.range(of: #"^\d{3,4}$"#, options: .regularExpression) != nil
    }

    private static func matchesExpiry(_ expiry: String?, loose: Bool) -> Bool {
        guard let expiry, !expiry.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return loose
        }
        let month = parseExpiryMonth(expiry)
        let year = parseExpiryYear(expiry)
        if month < 1 || month > 12 { return loose && year >= 2025 }
        return year >= 2025
    }

    private static func computeLuhnCheckDigit(_ partial: String) -> Int {
        var sum = 0
        var alt = true
        for char in partial.reversed() {
            var n = Int(String(char)) ?? 0
            if alt {
                n *= 2
                if n > 9 { n -= 9 }
            }
            sum += n
            alt.toggle()
        }
        return (10 - (sum % 10)) % 10
    }

    private static func passesLuhn(_ number: String) -> Bool {
        var sum = 0
        var alt = false
        for char in number.reversed() {
            var n = Int(String(char)) ?? 0
            if alt {
                n *= 2
                if n > 9 { n -= 9 }
            }
            sum += n
            alt.toggle()
        }
        return sum % 10 == 0
    }

    private static func parseExpiryMonth(_ expiry: String) -> Int {
        guard let parts = parseExpiryParts(expiry) else { return -1 }
        return Int(parts[0].trimmingCharacters(in: .whitespacesAndNewlines)) ?? -1
    }

    private static func parseExpiryYear(_ expiry: String) -> Int {
        guard let parts = parseExpiryParts(expiry), parts.count == 2 else { return -1 }
        var year = Int(parts[1].trimmingCharacters(in: .whitespacesAndNewlines)) ?? -1
        if year >= 0, year < 100 { year += 2000 }
        return year
    }

    private static func parseExpiryParts(_ expiry: String) -> [String]? {
        let normalized = expiry.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: " ", with: "")
        let parts = normalized.split { $0 == "/" || $0 == "." || $0 == "-" }.map(String.init)
        if parts.count == 2 { return parts }
        if normalized.count == 4, normalized.allSatisfy(\.isNumber) {
            return [String(normalized.prefix(2)), String(normalized.suffix(2))]
        }
        return nil
    }
}
