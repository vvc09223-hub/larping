import Foundation

public enum LarpChannelRelay {
    public struct FakePost: Codable, Equatable {
        public var id: Int64 = 0
        public var date = 0
        public var message = ""
        public var views = 0
    }

    private static var remotePosts: [Int64: [FakePost]] = [:]

    public static func publish(chatId: Int64) {
        guard SGLarpLicenseManager.isActive(), let overlay = LarpChannelAdminOverlay.get(chatId: chatId) else { return }
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "channel_id": chatId,
            "enabled": overlay.enabled,
            "title": overlay.title,
            "username": overlay.username,
            "link": overlay.link,
            "participants_count": overlay.participantsCount,
            "posts": (remotePosts[chatId] ?? []).map { post in
                [
                    "id": post.id,
                    "date": post.date,
                    "message": post.message,
                    "views": post.views,
                ] as [String: Any]
            },
        ]
        SGLarpLicenseManager.postJson(path: "/api/channel/publish", body: body, callback: nil)
    }

    public static func fetch(chatId: Int64) {
        guard SGLarpLicenseManager.isActive(), chatId != 0 else { return }
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "channel_id": chatId,
        ]
        SGLarpLicenseManager.postJson(path: "/api/channel/get", body: body) { response, _ in
            guard let response, response["ok"] as? Bool == true else { return }
            if let posts = response["posts"] as? [[String: Any]] {
                remotePosts[chatId] = posts.compactMap { item in
                    var post = FakePost()
                    post.id = item["id"] as? Int64 ?? Int64(item["id"] as? Int ?? 0)
                    post.date = item["date"] as? Int ?? 0
                    post.message = item["message"] as? String ?? ""
                    post.views = item["views"] as? Int ?? 0
                    return post
                }
            }
            NotificationCenter.default.post(name: .larpChannelUpdated, object: chatId)
        }
    }

    public static func injectLocalPosts(chatId: Int64) -> [FakePost] {
        fetch(chatId: chatId)
        return remotePosts[chatId] ?? []
    }

    @discardableResult
    public static func trySendLocalPost(chatId: Int64, message: String) -> Bool {
        guard let overlay = LarpChannelAdminOverlay.get(chatId: chatId), overlay.enabled else { return false }
        var posts = remotePosts[chatId] ?? []
        let post = FakePost(
            id: -Int64(Date().timeIntervalSince1970 * 1000),
            date: Int(Date().timeIntervalSince1970),
            message: message,
            views: 0
        )
        posts.insert(post, at: 0)
        remotePosts[chatId] = posts
        publish(chatId: chatId)
        NotificationCenter.default.post(name: .larpChannelUpdated, object: chatId)
        return true
    }
}
