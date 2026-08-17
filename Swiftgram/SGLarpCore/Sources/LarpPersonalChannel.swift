import Foundation

public enum LarpPersonalChannel {
    public static func apply() {
        guard SGLarpLicenseManager.isActive(), SGLarpSettings.visualMenu else { return }
        NotificationCenter.default.post(name: .larpProfileUpdated, object: nil)
    }

    public static func fakeSubscriberCount(realCount: Int) -> Int {
        guard SGLarpSettings.fakePersonalChannelEnabled,
              SGLarpSettings.fakePersonalChannelSubscribers > 0 else { return realCount }
        return SGLarpSettings.fakePersonalChannelSubscribers
    }

    public static func fakeChannelLink() -> String? {
        guard SGLarpSettings.fakePersonalChannelEnabled else { return nil }
        let link = SGLarpSettings.fakePersonalChannelLink.trimmingCharacters(in: .whitespacesAndNewlines)
        return link.isEmpty ? nil : link
    }
}
