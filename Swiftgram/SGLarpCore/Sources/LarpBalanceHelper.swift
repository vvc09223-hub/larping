import Foundation

public enum LarpBalanceHelper {
    private static let defaultStars: Int64 = 10_000_000
    private static let defaultTon: Int64 = 100

    public static func getStarsBalance() -> Int64 {
        if !SGLarpLicenseManager.fakeBalanceEnabled() && !SGLarpLicenseManager.larpGiftsEnabled() {
            return 0
        }
        var amount = SGLarpSettings.fakeStarsAmount
        if amount <= 0, SGLarpLicenseManager.larpGiftsEnabled() {
            amount = defaultStars
        }
        return max(0, amount)
    }

    public static func getTonBalance() -> Int64 {
        if !SGLarpLicenseManager.fakeBalanceEnabled() && !SGLarpLicenseManager.larpGiftsEnabled() {
            return 0
        }
        var amount = SGLarpSettings.fakeTonAmount
        if amount <= 0, SGLarpLicenseManager.larpGiftsEnabled() {
            amount = defaultTon
        }
        return max(0, amount)
    }

    @discardableResult
    public static func spendStars(_ amount: Int64) -> Bool {
        guard amount > 0 else { return true }
        guard SGLarpLicenseManager.larpGiftsEnabled() else { return false }
        let balance = getStarsBalance()
        guard balance >= amount else { return false }
        SGLarpSettings.fakeStarsAmount = balance - amount
        notifyBalanceChanged(ton: false)
        return true
    }

    @discardableResult
    public static func spendTon(_ amount: Int64) -> Bool {
        guard amount > 0 else { return true }
        guard SGLarpLicenseManager.larpGiftsEnabled() else { return false }
        let balance = getTonBalance()
        guard balance >= amount else { return false }
        SGLarpSettings.fakeTonAmount = balance - amount
        notifyBalanceChanged(ton: true)
        return true
    }

    public static func notifyBalanceChanged(ton: Bool) {
        NotificationCenter.default.post(name: .larpStarBalanceUpdated, object: ton)
    }
}

public extension Notification.Name {
    static let larpStarBalanceUpdated = Notification.Name("SGLarpStarBalanceUpdated")
    static let larpProfileUpdated = Notification.Name("SGLarpProfileUpdated")
    static let larpGiftsUpdated = Notification.Name("SGLarpGiftsUpdated")
    static let larpChannelUpdated = Notification.Name("SGLarpChannelUpdated")
}
