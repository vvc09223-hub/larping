import Foundation

public enum LarpGiftsInjector {
    public static func syncAll() {
        guard SGLarpLicenseManager.larpGiftsEnabled() else { return }
        NotificationCenter.default.post(name: .larpGiftsUpdated, object: nil)
    }

    public static func entriesForProfile(ownerDialogId: Int64) -> [LarpGiftStore.Entry] {
        LarpGiftStore.forOwner(ownerDialogId)
    }
}
