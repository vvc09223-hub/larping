import Foundation

public enum LarpTransactionHelper {
    private static let prefsKey = "mg_larp_transactions"

    public static func mergeStored(ton: Bool) {
        guard SGLarpLicenseManager.larpGiftsEnabled() else { return }
        _ = loadTransactions(ton: ton)
        NotificationCenter.default.post(name: .larpStarBalanceUpdated, object: ton)
    }

    public static func addStarsTopup(_ stars: Int64) {
        guard stars > 0 else { return }
        var list = loadTransactions(ton: false)
        list.insert([
            "id": "mg-larp-topup-\(Int(Date().timeIntervalSince1970 * 1000))",
            "date": Int(Date().timeIntervalSince1970),
            "amount": stars,
            "type": "topup",
        ], at: 0)
        saveTransactions(list, ton: false)
    }

    public static func addGiftSpend(ton: Bool, price: Int64, description: String) {
        guard SGLarpLicenseManager.larpGiftsEnabled(), price > 0 else { return }
        var list = loadTransactions(ton: ton)
        list.insert([
            "id": "mg-larp-\(Int(Date().timeIntervalSince1970 * 1000))",
            "date": Int(Date().timeIntervalSince1970),
            "amount": -price,
            "type": "gift",
            "description": description,
        ], at: 0)
        if list.count > 200 { list = Array(list.prefix(200)) }
        saveTransactions(list, ton: ton)
    }

    public static func storedTransactions(ton: Bool) -> [[String: Any]] {
        loadTransactions(ton: ton)
    }

    private static func loadTransactions(ton: Bool) -> [[String: Any]] {
        let key = ton ? "tx_ton" : "tx_stars"
        guard let raw = UserDefaults.standard.string(forKey: "\(prefsKey)_\(key)"),
              let data = raw.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        return json
    }

    private static func saveTransactions(_ list: [[String: Any]], ton: Bool) {
        let key = ton ? "tx_ton" : "tx_stars"
        if let data = try? JSONSerialization.data(withJSONObject: Array(list.prefix(200))),
           let raw = String(data: data, encoding: .utf8) {
            UserDefaults.standard.set(raw, forKey: "\(prefsKey)_\(key)")
        }
    }
}
