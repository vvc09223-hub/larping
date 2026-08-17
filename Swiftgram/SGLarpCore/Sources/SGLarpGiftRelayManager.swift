import Foundation

public enum SGLarpGiftRelayManager {
    private static var pollTimer: Timer?

    public static func start() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { _ in
            pollNow()
        }
        pollNow()
    }

    public static func pollNow() {
        guard SGLarpLicenseManager.larpGiftsEnabled() else { return }
        let userId = Int64(UserDefaults.standard.integer(forKey: "mg_larp_self_user_id"))
        guard userId > 0 else { return }
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "user_id": userId,
        ]
        SGLarpLicenseManager.postJson(path: "/api/gift/inbox", body: body) { response, _ in
            guard let response, response["ok"] as? Bool == true,
                  let gifts = response["gifts"] as? [[String: Any]] else { return }
            for gift in gifts {
                var entry = LarpGiftStore.Entry()
                entry.ownerDialogId = userId
                entry.savedId = LarpGiftStore.parseLongValue(gift["saved_id"])
                entry.giftId = LarpGiftStore.parseLongValue(gift["gift_id"])
                entry.slug = gift["slug"] as? String ?? ""
                entry.giftTitle = gift["title"] as? String ?? "Gift"
                entry.giftStars = LarpGiftStore.parseLongValue(gift["stars"])
                entry.senderId = LarpGiftStore.parseLongValue(gift["sender_id"])
                entry.senderName = LarpSenderNames.fromProfile(gift["sender_profile"] as? [String: Any])
                if entry.savedId != 0 { LarpGiftStore.register(entry) }
            }
        }
    }

    public static func sendGift(toUserId: Int64, savedId: Int64) {
        guard toUserId > 0, savedId != 0 else { return }
        let fromUserId = Int64(UserDefaults.standard.integer(forKey: "mg_larp_self_user_id"))
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "from_user_id": fromUserId,
            "to_user_id": toUserId,
            "saved_id": String(savedId),
        ]
        SGLarpLicenseManager.postJson(path: "/api/gift/send", body: body, callback: nil)
    }
}
