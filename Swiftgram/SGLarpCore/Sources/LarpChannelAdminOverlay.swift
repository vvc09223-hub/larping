import Foundation

public enum LarpChannelAdminOverlay {
    public struct Overlay: Codable, Equatable {
        public var chatId: Int64 = 0
        public var enabled = false
        public var title = ""
        public var username = ""
        public var link = ""
        public var photoPath = ""
        public var participantsCount = 0
        public var fakeAdmins: [FakeAdmin] = []

        public init(
            chatId: Int64 = 0,
            enabled: Bool = false,
            title: String = "",
            username: String = "",
            link: String = "",
            photoPath: String = "",
            participantsCount: Int = 0,
            fakeAdmins: [FakeAdmin] = []
        ) {
            self.chatId = chatId
            self.enabled = enabled
            self.title = title
            self.username = username
            self.link = link
            self.photoPath = photoPath
            self.participantsCount = participantsCount
            self.fakeAdmins = fakeAdmins
        }
    }

    public struct FakeAdmin: Codable, Equatable {
        public var userId: Int64 = 0
        public var username = ""
        public var firstName = ""
        public var lastName = ""
        public var rank = "LARP"

        public init(
            userId: Int64 = 0,
            username: String = "",
            firstName: String = "",
            lastName: String = "",
            rank: String = "LARP"
        ) {
            self.userId = userId
            self.username = username
            self.firstName = firstName
            self.lastName = lastName
            self.rank = rank
        }
    }

    private static let storageKey = "mg_larpChannelOverlays"

    public static func all() -> [Overlay] {
        loadAll().values.sorted { $0.chatId < $1.chatId }
    }

    public static func get(chatId: Int64) -> Overlay? {
        loadAll()[chatId]
    }

    public static func save(_ overlay: Overlay) {
        var map = loadAll()
        map[overlay.chatId] = overlay
        persist(map)
        LarpChannelRelay.publish(chatId: overlay.chatId)
    }

    public static func remove(chatId: Int64) {
        var map = loadAll()
        map.removeValue(forKey: chatId)
        persist(map)
    }

    public static func canLarpManage(chatId: Int64, isCreator: Bool) -> Bool {
        if isCreator { return true }
        guard let overlay = get(chatId: chatId), overlay.enabled else { return false }
        let selfId = SGLarpSelfUserId.get()
        return overlay.fakeAdmins.contains { $0.userId == selfId }
    }

    public static func displayTitle(chatId: Int64, realTitle: String) -> String {
        guard let overlay = get(chatId: chatId), overlay.enabled, !overlay.title.isEmpty else { return realTitle }
        return overlay.title
    }

    public static func displayUsername(chatId: Int64, realUsername: String?) -> String? {
        guard let overlay = get(chatId: chatId), overlay.enabled, !overlay.username.isEmpty else { return realUsername }
        return overlay.username
    }

    public static func displayParticipantsCount(chatId: Int64, realCount: Int) -> Int {
        guard let overlay = get(chatId: chatId), overlay.enabled, overlay.participantsCount > 0 else { return realCount }
        return overlay.participantsCount
    }

    private static func loadAll() -> [Int64: Overlay] {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let list = try? JSONDecoder().decode([Overlay].self, from: data) else { return [:] }
        return Dictionary(uniqueKeysWithValues: list.map { ($0.chatId, $0) })
    }

    private static func persist(_ map: [Int64: Overlay]) {
        let list = Array(map.values)
        if let data = try? JSONEncoder().encode(list) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
        NotificationCenter.default.post(name: .larpChannelUpdated, object: nil)
    }
}
