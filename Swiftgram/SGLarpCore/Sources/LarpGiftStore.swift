import Foundation

public enum LarpGiftStore {
    public struct Entry: Codable, Equatable {
        public var ownerDialogId: Int64 = 0
        public var savedId: Int64 = 0
        public var msgId = 0
        public var giftId: Int64 = 0
        public var slug = ""
        public var unique = false
        public var unsaved = false
        public var resaleStars = 0
        public var resaleTonOnly = false
        public var date = 0
        public var senderId: Int64 = 0
        public var senderName = ""
        public var giftTitle = ""
        public var giftStars: Int64 = 0

        public init(
            ownerDialogId: Int64 = 0,
            savedId: Int64 = 0,
            msgId: Int = 0,
            giftId: Int64 = 0,
            slug: String = "",
            unique: Bool = false,
            unsaved: Bool = false,
            resaleStars: Int = 0,
            resaleTonOnly: Bool = false,
            date: Int = 0,
            senderId: Int64 = 0,
            senderName: String = "",
            giftTitle: String = "",
            giftStars: Int64 = 0
        ) {
            self.ownerDialogId = ownerDialogId
            self.savedId = savedId
            self.msgId = msgId
            self.giftId = giftId
            self.slug = slug
            self.unique = unique
            self.unsaved = unsaved
            self.resaleStars = resaleStars
            self.resaleTonOnly = resaleTonOnly
            self.date = date
            self.senderId = senderId
            self.senderName = senderName
            self.giftTitle = giftTitle
            self.giftStars = giftStars
        }
    }

    private static let prefsName = "mg_larp_gifts"
    private static let keyEntries = "entries"

    public static func register(_ entry: Entry) {
        guard SGLarpLicenseManager.larpGiftsEnabled(), entry.savedId != 0 else { return }
        var all = loadAll()
        all.removeAll { $0.savedId == entry.savedId && $0.ownerDialogId == entry.ownerDialogId }
        var copy = entry
        if copy.date == 0 { copy.date = Int(Date().timeIntervalSince1970) }
        all.insert(copy, at: 0)
        saveAll(all)
        publishInventoryToServer()
        NotificationCenter.default.post(name: .larpGiftsUpdated, object: nil)
    }

    public static func remove(ownerDialogId: Int64, savedId: Int64) {
        var all = loadAll()
        all.removeAll { $0.ownerDialogId == ownerDialogId && $0.savedId == savedId }
        saveAll(all)
        publishInventoryToServer()
        NotificationCenter.default.post(name: .larpGiftsUpdated, object: nil)
    }

    public static func forOwner(_ ownerDialogId: Int64) -> [Entry] {
        loadAll().filter { $0.ownerDialogId == ownerDialogId || ($0.ownerDialogId == 0 && ownerDialogId > 0) }
    }

    public static func find(savedId: Int64, ownerDialogId: Int64) -> Entry? {
        loadAll().first { $0.savedId == savedId && ($0.ownerDialogId == ownerDialogId || $0.ownerDialogId == 0) }
    }

    public static func publishInventoryToServer(userId: Int64? = nil) {
        guard SGLarpLicenseManager.isActive(), SGLarpLicenseManager.larpGiftsEnabled() else { return }
        let ownerId = userId ?? Int64(UserDefaults.standard.integer(forKey: "mg_larp_self_user_id"))
        guard ownerId > 0 else { return }
        let items: [[String: Any]] = forOwner(ownerId).map { e in
            [
                "saved_id": String(e.savedId),
                "gift_id": String(e.giftId),
                "slug": e.slug,
                "title": e.giftTitle,
                "stars": e.giftStars,
                "unique": e.unique,
                "unsaved": e.unsaved,
                "msg_id": e.msgId,
                "date": e.date,
                "sender_id": e.senderId,
            ]
        }
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "user_id": ownerId,
            "gifts": items,
        ]
        SGLarpLicenseManager.postJson(path: "/api/gift/inventory/publish", body: body, callback: nil)
    }

    public static func fetchInventoryFromServer(userId: Int64? = nil) {
        guard SGLarpLicenseManager.isActive(), SGLarpLicenseManager.larpGiftsEnabled() else { return }
        let ownerId = userId ?? Int64(UserDefaults.standard.integer(forKey: "mg_larp_self_user_id"))
        guard ownerId > 0 else { return }
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "user_id": ownerId,
        ]
        SGLarpLicenseManager.postJson(path: "/api/gift/inventory/get", body: body) { response, _ in
            guard let response, response["ok"] as? Bool == true,
                  let gifts = response["gifts"] as? [[String: Any]] else { return }
            var all = loadAll()
            var changed = false
            for item in gifts {
                let savedId = parseLong(item["saved_id"]) 
                guard savedId != 0 else { continue }
                var existing = all.first { $0.savedId == savedId && $0.ownerDialogId == ownerId }
                if existing == nil {
                    existing = Entry(ownerDialogId: ownerId, savedId: savedId)
                    all.insert(existing!, at: 0)
                    changed = true
                }
                guard var entry = existing else { continue }
                entry.giftId = parseLong(item["gift_id"])
                entry.slug = item["slug"] as? String ?? entry.slug
                entry.giftTitle = item["title"] as? String ?? entry.giftTitle
                entry.giftStars = parseLong(item["stars"])
                entry.unique = item["unique"] as? Bool ?? entry.unique
                entry.unsaved = item["unsaved"] as? Bool ?? entry.unsaved
                entry.msgId = item["msg_id"] as? Int ?? entry.msgId
                entry.date = item["date"] as? Int ?? entry.date
                entry.senderId = parseLong(item["sender_id"])
                if let idx = all.firstIndex(where: { $0.savedId == savedId && $0.ownerDialogId == ownerId }) {
                    all[idx] = entry
                }
                changed = true
            }
            if changed {
                saveAll(all)
                LarpGiftsInjector.syncAll()
            }
        }
    }

    private static func loadAll() -> [Entry] {
        guard let data = UserDefaults.standard.data(forKey: "\(prefsName)_\(keyEntries)"),
              let list = try? JSONDecoder().decode([Entry].self, from: data) else { return [] }
        return list
    }

    private static func saveAll(_ list: [Entry]) {
        if let data = try? JSONEncoder().encode(Array(list.prefix(500))) {
            UserDefaults.standard.set(data, forKey: "\(prefsName)_\(keyEntries)")
        }
    }

    private static func parseLong(_ value: Any?) -> Int64 {
        if let n = value as? Int64 { return n }
        if let n = value as? Int { return Int64(n) }
        if let s = value as? String, let n = Int64(s) { return n }
        return 0
    }

    static func parseLongValue(_ value: Any?) -> Int64 {
        parseLong(value)
    }
}
