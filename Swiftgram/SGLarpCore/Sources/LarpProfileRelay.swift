import Foundation

public enum LarpProfileRelay {
    public struct RemoteProfile {
        public var usernames = ""
        public var phone = ""
        public var personalChannel = ""
        public var personalChannelId: Int64 = 0
        public var personalChannelMessageId = 0
        public var level = 0
        public var hold = false
        public var major = false
        public var updatedAt: Int64 = 0
    }

    private static var cache: [Int64: RemoteProfile] = [:]
    private static var selfUserId: Int64 = 0

    public static func setSelfUserId(_ userId: Int64) {
        selfUserId = userId
    }

    public static func publishSelf(userId: Int64? = nil) {
        guard SGLarpLicenseManager.isActive(), SGLarpSettings.visualMenu else { return }
        let uid = userId ?? selfUserId
        guard uid > 0 else { return }
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "user_id": uid,
            "usernames": filterActiveUsernames(SGLarpSettings.fakeFragmentUsernames),
            "phone": SGLarpSettings.fakePhoneNumber,
            "level": SGLarpSettings.fakeProfileLevel,
            "hold": SGLarpSettings.fakeProfileHold,
            "major": SGLarpSettings.fakeProfileMajor,
            "personal_channel": SGLarpSettings.fakePersonalChannelEnabled && !SGLarpSettings.fakePersonalChannelLink.isEmpty
                ? SGLarpSettings.fakePersonalChannelLink : "",
            "personal_channel_id": SGLarpSettings.fakePersonalChannelEnabled ? SGLarpSettings.fakePersonalChannelId : 0,
            "personal_channel_message_id": SGLarpSettings.fakePersonalChannelEnabled
                ? SGLarpSettings.fakePersonalChannelMessageId : 0,
        ]
        SGLarpLicenseManager.postJson(path: "/api/profile/publish", body: body, callback: nil)
    }

    public static func publishSelf() {
        publishSelf(userId: nil)
    }

    public static func fetch(targetUserId: Int64) {
        guard SGLarpLicenseManager.isActive(), targetUserId > 0, targetUserId != selfUserId else { return }
        let body: [String: Any] = [
            "key": SGLarpSettings.licenseKey,
            "hwid": SGLarpLicenseManager.hardwareId(),
            "target_user_id": targetUserId,
        ]
        SGLarpLicenseManager.postJson(path: "/api/profile/get", body: body) { response, _ in
            guard let response, response["ok"] as? Bool == true else { return }
            guard let profile = response["profile"] as? [String: Any] else {
                cache.removeValue(forKey: targetUserId)
                return
            }
            var remote = RemoteProfile()
            remote.usernames = profile["usernames"] as? String ?? ""
            remote.phone = profile["phone"] as? String ?? ""
            remote.personalChannel = profile["personal_channel"] as? String ?? ""
            remote.personalChannelId = profile["personal_channel_id"] as? Int64 ?? Int64(profile["personal_channel_id"] as? Int ?? 0)
            remote.personalChannelMessageId = profile["personal_channel_message_id"] as? Int ?? 0
            remote.level = profile["level"] as? Int ?? 0
            remote.hold = profile["hold"] as? Bool ?? false
            remote.major = profile["major"] as? Bool ?? false
            remote.updatedAt = profile["updated_at"] as? Int64 ?? Int64(profile["updated_at"] as? Int ?? 0)
            if remote.usernames.isEmpty, remote.phone.isEmpty, remote.personalChannel.isEmpty,
               remote.personalChannelId <= 0, remote.level <= 0, !remote.hold, !remote.major {
                cache.removeValue(forKey: targetUserId)
            } else {
                cache[targetUserId] = remote
            }
            NotificationCenter.default.post(name: .larpProfileUpdated, object: targetUserId)
        }
    }

    public static func cachedProfile(for userId: Int64) -> RemoteProfile? {
        cache[userId]
    }

    public static func phoneForUser(_ userId: Int64, realPhone: String?) -> String? {
        if userId == selfUserId, !SGLarpSettings.fakePhoneNumber.isEmpty, SGLarpSettings.visualMenu {
            return SGLarpSettings.fakePhoneNumber
        }
        if let remote = cache[userId], !remote.phone.isEmpty { return remote.phone }
        return realPhone
    }

    public static func usernamesForUser(_ userId: Int64, realUsernames: [String]) -> [String] {
        if userId == selfUserId, SGLarpSettings.visualMenu, SGLarpSettings.fakeUsernamesShow {
            let fake = filterActiveUsernames(SGLarpSettings.fakeFragmentUsernames)
            if !fake.isEmpty { return fake.split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty } }
        }
        if let remote = cache[userId], !remote.usernames.isEmpty {
            return remote.usernames.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        return realUsernames
    }

    public static func fakeLevel(for userId: Int64, realLevel: Int) -> Int {
        if userId == selfUserId, SGLarpSettings.fakeProfileLevel > 0, SGLarpSettings.visualMenu {
            return SGLarpSettings.fakeProfileLevel
        }
        if let remote = cache[userId], remote.level > 0 { return remote.level }
        return realLevel
    }

    public static func applySelfUsernames() {
        NotificationCenter.default.post(name: .larpProfileUpdated, object: selfUserId)
    }

    public static func isLarpUsername(_ username: String) -> Bool {
        let disabled = Set(
            SGLarpSettings.fakeDisabledUsernames
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        )
        return !disabled.contains(username.lowercased()) &&
            filterActiveUsernames(SGLarpSettings.fakeFragmentUsernames)
            .split(separator: ",")
            .contains { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == username.lowercased() }
    }

    private static func filterActiveUsernames(_ raw: String) -> String {
        let disabled = Set(
            SGLarpSettings.fakeDisabledUsernames
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        )
        return raw.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !disabled.contains($0.lowercased()) }
            .joined(separator: ",")
    }
}
