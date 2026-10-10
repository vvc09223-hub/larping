import Foundation

/// Telegram user ids exceed Int32; never use UserDefaults.integer(forKey:) for them.
public enum SGLarpSelfUserId {
    private static let key = "mg_larp_self_user_id"

    public static func get() -> Int64 {
        if let raw = UserDefaults.standard.string(forKey: key), let value = Int64(raw) {
            return value
        }
        if let number = UserDefaults.standard.object(forKey: key) as? NSNumber {
            return number.int64Value
        }
        return Int64(UserDefaults.standard.integer(forKey: key))
    }

    public static func set(_ userId: Int64) {
        guard userId > 0 else { return }
        UserDefaults.standard.set(String(userId), forKey: key)
    }
}
