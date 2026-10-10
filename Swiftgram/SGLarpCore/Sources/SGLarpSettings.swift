import Foundation

public enum SGLarpSettings {
    private static let defaults = UserDefaults.standard
    private static let licenseDefaults = UserDefaults(suiteName: "com.swiftgram.larp.license") ?? .standard

    public static var licenseKey: String {
        get { defaults.string(forKey: "mg_larpLicenseKey") ?? licenseDefaults.string(forKey: "key") ?? "" }
        set {
            let normalized = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
            defaults.set(normalized, forKey: "mg_larpLicenseKey")
            licenseDefaults.set(normalized, forKey: "key")
        }
    }

    public static var licenseActive: Bool {
        get { defaults.bool(forKey: "mg_larpLicenseActive") || licenseDefaults.bool(forKey: "active") }
        set {
            defaults.set(newValue, forKey: "mg_larpLicenseActive")
            licenseDefaults.set(newValue, forKey: "active")
        }
    }

    public static var visualMenu: Bool {
        get {
            if defaults.object(forKey: "mg_larpVisualMenu") == nil
                && licenseDefaults.object(forKey: "visual") == nil {
                return true
            }
            return defaults.bool(forKey: "mg_larpVisualMenu") || licenseDefaults.bool(forKey: "visual")
        }
        set {
            defaults.set(newValue, forKey: "mg_larpVisualMenu")
            licenseDefaults.set(newValue, forKey: "visual")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeGifts: Bool {
        get {
            if defaults.object(forKey: "mg_larpFakeGifts") == nil
                && licenseDefaults.object(forKey: "gifts") == nil {
                return true
            }
            return defaults.bool(forKey: "mg_larpFakeGifts") || licenseDefaults.bool(forKey: "gifts")
        }
        set {
            defaults.set(newValue, forKey: "mg_larpFakeGifts")
            licenseDefaults.set(newValue, forKey: "gifts")
        }
    }

    public static var serverHost: String {
        get { defaults.string(forKey: "mg_larpServerHost") ?? "" }
        set { defaults.set(newValue, forKey: "mg_larpServerHost") }
    }

    public static var fakeStarsAmount: Int64 {
        get { Int64(defaults.integer(forKey: "mg_fakeStarsAmount")) }
        set {
            defaults.set(Int(newValue), forKey: "mg_fakeStarsAmount")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeTonAmount: Int64 {
        get { Int64(defaults.integer(forKey: "mg_fakeTonAmount")) }
        set {
            defaults.set(Int(newValue), forKey: "mg_fakeTonAmount")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeNftSlug: String {
        get { defaults.string(forKey: "mg_fakeNftSlug") ?? "" }
        set {
            defaults.set(newValue, forKey: "mg_fakeNftSlug")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakePhoneNumber: String {
        get { defaults.string(forKey: "mg_fakePhoneNumber") ?? "" }
        set {
            defaults.set(newValue, forKey: "mg_fakePhoneNumber")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeFragmentUsernames: String {
        get { defaults.string(forKey: "mg_fakeFragmentUsernames") ?? "" }
        set {
            defaults.set(newValue, forKey: "mg_fakeFragmentUsernames")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeUsernamesShow: Bool {
        get { defaults.bool(forKey: "mg_fakeUsernamesShow") }
        set {
            defaults.set(newValue, forKey: "mg_fakeUsernamesShow")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeDisabledUsernames: String {
        get { defaults.string(forKey: "mg_fakeDisabledUsernames") ?? "" }
        set {
            defaults.set(newValue, forKey: "mg_fakeDisabledUsernames")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeProfileLevel: Int {
        get { defaults.integer(forKey: "mg_fakeProfileLevel") }
        set {
            defaults.set(newValue, forKey: "mg_fakeProfileLevel")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeProfileHold: Bool {
        get { defaults.bool(forKey: "mg_fakeProfileHold") }
        set {
            defaults.set(newValue, forKey: "mg_fakeProfileHold")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakeProfileMajor: Bool {
        get { defaults.bool(forKey: "mg_fakeProfileMajor") }
        set {
            defaults.set(newValue, forKey: "mg_fakeProfileMajor")
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakePersonalChannelEnabled: Bool {
        get { defaults.bool(forKey: "mg_fakePersonalChannelEnabled") }
        set {
            defaults.set(newValue, forKey: "mg_fakePersonalChannelEnabled")
            LarpPersonalChannel.apply()
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakePersonalChannelLink: String {
        get { defaults.string(forKey: "mg_fakePersonalChannelLink") ?? "" }
        set {
            defaults.set(newValue, forKey: "mg_fakePersonalChannelLink")
            LarpPersonalChannel.apply()
            LarpProfileRelay.publishSelf()
        }
    }

    public static var fakePersonalChannelId: Int64 {
        get { Int64(defaults.integer(forKey: "mg_fakePersonalChannelId")) }
        set { defaults.set(Int(newValue), forKey: "mg_fakePersonalChannelId") }
    }

    public static var fakePersonalChannelMessageId: Int {
        get { defaults.integer(forKey: "mg_fakePersonalChannelMessageId") }
        set { defaults.set(newValue, forKey: "mg_fakePersonalChannelMessageId") }
    }

    public static var fakePersonalChannelSubscribers: Int {
        get { defaults.integer(forKey: "mg_fakePersonalChannelSubscribers") }
        set {
            defaults.set(newValue, forKey: "mg_fakePersonalChannelSubscribers")
            LarpPersonalChannel.apply()
        }
    }

    public static func clearLicense() {
        licenseKey = ""
        licenseActive = false
    }
}
