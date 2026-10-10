import Foundation

public enum LarpGiftActionsHelper {
    public static func shouldHandleLocally(savedId: Int64, slug: String?) -> Bool {
        guard SGLarpLicenseManager.larpGiftsEnabled() else { return false }
        if savedId != 0, LarpGiftStore.find(savedId: savedId, ownerDialogId: currentOwnerId()) != nil {
            return true
        }
        if let slug, !slug.isEmpty {
            return LarpGiftStore.forOwner(currentOwnerId()).contains { $0.slug == slug }
        }
        return false
    }

    @discardableResult
    public static func transferGift(savedId: Int64, toUserId: Int64, starsPrice: Int64) -> Bool {
        guard shouldHandleLocally(savedId: savedId, slug: nil) else { return false }
        guard LarpBalanceHelper.spendStars(starsPrice) else { return false }
        LarpGiftStore.remove(ownerDialogId: currentOwnerId(), savedId: savedId)
        LarpTransactionHelper.addGiftSpend(ton: false, price: starsPrice, description: "Gift transfer")
        SGLarpGiftRelayManager.sendGift(toUserId: toUserId, savedId: savedId)
        return true
    }

    public static func toggleProfileVisible(savedId: Int64, unsaved: Bool) {
        guard var entry = LarpGiftStore.find(savedId: savedId, ownerDialogId: currentOwnerId()) else { return }
        entry.unsaved = unsaved
        LarpGiftStore.register(entry)
    }

    private static func currentOwnerId() -> Int64 {
        SGLarpSelfUserId.get()
    }
}
