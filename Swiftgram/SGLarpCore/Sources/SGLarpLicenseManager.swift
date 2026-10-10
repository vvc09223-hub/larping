import Foundation
import CryptoKit
#if canImport(UIKit)
import UIKit
#endif

public enum SGLarpLicenseManager {
    public static let defaultServerHost = "2.26.48.141"
    public static let serverPort = 1048
    /// Same as Android `LicenseManager.LICENSE_DISABLED` — relay without per-user license key.
    public static let licenseDisabled = true
    private static let relaySharedKey = "LARP-RELAY-MOBILE"

    public typealias ResultCallback = (Bool, String) -> Void
    public typealias JsonCallback = ([String: Any]?, String?) -> Void

    private static let revalidateInterval: TimeInterval = 60 * 60
    private static var revalidateTimer: Timer?

    public static func getServerHost() -> String {
        let custom = SGLarpSettings.serverHost.trimmingCharacters(in: .whitespacesAndNewlines)
        if custom.isEmpty { return defaultServerHost }
        return normalizeServerHost(custom)
    }

    public static func getServerPort() -> Int {
        let custom = SGLarpSettings.serverHost
        if !custom.isEmpty, let port = parsePortFromHostField(custom), port > 0 {
            return port
        }
        return serverPort
    }

    public static func normalizeServerHost(_ raw: String) -> String {
        var host = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if host.isEmpty { return defaultServerHost }
        if host.hasPrefix("http://") { host = String(host.dropFirst(7)) }
        else if host.hasPrefix("https://") { host = String(host.dropFirst(8)) }
        if let slash = host.firstIndex(of: "/") { host = String(host[..<slash]) }
        if let colon = host.lastIndex(of: ":"), colon > host.startIndex {
            let maybePort = host[host.index(after: colon)...]
            if maybePort.allSatisfy(\.isNumber) {
                host = String(host[..<colon])
            }
        }
        return host.isEmpty ? defaultServerHost : host
    }

    public static func isActive() -> Bool {
        if licenseDisabled {
            return true
        }
        return SGLarpSettings.licenseActive && !SGLarpSettings.licenseKey.isEmpty
    }

    public static func getRelayKey() -> String {
        let trimmed = SGLarpSettings.licenseKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            return trimmed
        }
        return relaySharedKey
    }

    public static func giftRelayEnabled() -> Bool {
        guard isActive() else { return false }
        if licenseDisabled {
            return SGLarpSettings.visualMenu
        }
        return larpGiftsEnabled() || (SGLarpSettings.licenseActive && !SGLarpSettings.licenseKey.isEmpty)
    }

    public static func larpGiftsEnabled() -> Bool {
        isActive() && SGLarpSettings.visualMenu && SGLarpSettings.fakeGifts
    }

    public static func fakeBalanceEnabled() -> Bool {
        isActive() && SGLarpSettings.visualMenu &&
            (SGLarpSettings.fakeGifts || SGLarpSettings.fakeStarsAmount > 0 || SGLarpSettings.fakeTonAmount > 0)
    }

    public static func hardwareId() -> String {
        let suite = UserDefaults(suiteName: "com.swiftgram.larp.license") ?? .standard
        if let stored = suite.string(forKey: "hwid"), !stored.isEmpty { return stored }
        let vendor = UIDeviceHardwareId.current
        let computed = sha256Hex("mg-" + vendor)
        suite.set(computed, forKey: "hwid")
        return computed
    }

    public static func start() {
        DispatchQueue.main.async {
            startOnMainThread()
        }
    }

    private static func startOnMainThread() {
        if licenseDisabled {
            if !SGLarpSettings.licenseActive {
                SGLarpSettings.licenseActive = true
            }
        } else if !SGLarpSettings.licenseKey.isEmpty {
            if !SGLarpSettings.licenseActive { SGLarpSettings.licenseActive = true }
            ensureActivated(nil)
            scheduleRevalidate()
        }
        SGLarpGiftRelayManager.start()
        LarpTransactionHelper.mergeStored(ton: false)
        LarpTransactionHelper.mergeStored(ton: true)
        LarpGiftStore.fetchInventoryFromServer()
        LarpGiftsInjector.syncAll()
        LarpPersonalChannel.apply()
        LarpProfileRelay.applySelfUsernames()
        LarpBadgeIcons.preload()
    }

    public static func activate(key: String, callback: ResultCallback?) {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            callback?(false, "empty_key")
            return
        }
        request(path: "/api/activate", key: trimmed, updateActiveFromResult: false) { ok, message in
            if ok {
                SGLarpSettings.licenseKey = trimmed
                setActive(true)
            } else if ["invalid_key", "hwid_mismatch", "ip_mismatch"].contains(message) {
                setActive(false)
            }
            callback?(ok, message)
        }
    }

    public static func ensureActivated(_ callback: ResultCallback?) {
        guard !SGLarpSettings.licenseKey.isEmpty else {
            callback?(false, "empty_key")
            return
        }
        if !SGLarpSettings.licenseActive { SGLarpSettings.licenseActive = true }
        validateStored { ok, message in
            callback?(ok || SGLarpSettings.licenseActive, message)
        }
    }

    public static func validateStored(_ callback: ResultCallback?) {
        guard !SGLarpSettings.licenseKey.isEmpty else {
            callback?(false, "empty_key")
            return
        }
        request(path: "/api/validate", key: SGLarpSettings.licenseKey, updateActiveFromResult: true, callback: callback)
    }

    public static func postJson(path: String, body: [String: Any], callback: JsonCallback?) {
        DispatchQueue.global(qos: .utility).async {
            let response = postJsonSync(path: path, body: body)
            if let callback {
                DispatchQueue.main.async {
                    callback(response, response == nil ? "network_error" : nil)
                }
            }
        }
    }

    @discardableResult
    public static func postJsonSync(path: String, body: [String: Any]) -> [String: Any]? {
        guard let url = URL(string: serverBaseUrl(path)) else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        guard let (data, response) = performSyncRequest(request) else { return nil }
        guard (200 ..< 300).contains(response.statusCode) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    private static func performSyncRequest(_ request: URLRequest) -> (Data, HTTPURLResponse)? {
        var resultData: Data?
        var resultResponse: HTTPURLResponse?
        var resultError: Error?
        let semaphore = DispatchSemaphore(value: 0)
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            resultData = data
            resultResponse = response as? HTTPURLResponse
            resultError = error
            semaphore.signal()
        }
        task.resume()
        let timeout = request.timeoutInterval + 5
        if semaphore.wait(timeout: .now() + timeout) == .timedOut {
            task.cancel()
            return nil
        }
        guard resultError == nil, let data = resultData, let response = resultResponse else {
            return nil
        }
        return (data, response)
    }

    private static func request(
        path: String,
        key: String,
        updateActiveFromResult: Bool,
        callback: ResultCallback?
    ) {
        DispatchQueue.global(qos: .utility).async {
            var ok = false
            var message = "network_error"
            let body: [String: Any] = ["key": key, "hwid": hardwareId()]
            guard let url = URL(string: serverBaseUrl(path)) else {
                DispatchQueue.main.async { callback?(false, message) }
                return
            }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.timeoutInterval = 20
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
            if let (data, response) = performSyncRequest(request),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                ok = json["ok"] as? Bool ?? false
                message = json["message"] as? String ?? (ok ? "ok" : "bad_response")
                _ = response.statusCode
            }
            DispatchQueue.main.async {
                if updateActiveFromResult && path.contains("validate") {
                    if ok {
                        setActive(true)
                    } else if message == "not_activated", !SGLarpSettings.licenseKey.isEmpty {
                        activate(key: SGLarpSettings.licenseKey, callback: callback)
                        return
                    } else if ["ip_mismatch", "hwid_mismatch"].contains(message) {
                        setActive(false)
                    } else if message == "invalid_key" {
                        SGLarpSettings.clearLicense()
                    }
                }
                callback?(ok, message)
            }
        }
    }

    private static func setActive(_ active: Bool) {
        guard SGLarpSettings.licenseActive != active else { return }
        SGLarpSettings.licenseActive = active
        if active {
            SGLarpGiftRelayManager.pollNow()
            LarpProfileRelay.publishSelf()
        }
    }

    private static func scheduleRevalidate() {
        revalidateTimer?.invalidate()
        revalidateTimer = Timer.scheduledTimer(withTimeInterval: revalidateInterval, repeats: true) { _ in
            validateStored(nil)
        }
        if let revalidateTimer {
            RunLoop.main.add(revalidateTimer, forMode: .common)
        }
    }

    private static func serverBaseUrl(_ path: String) -> String {
        "http://\(getServerHost()):\(getServerPort())\(path)"
    }

    private static func parsePortFromHostField(_ raw: String) -> Int? {
        var host = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if host.hasPrefix("http://") { host = String(host.dropFirst(7)) }
        else if host.hasPrefix("https://") { host = String(host.dropFirst(8)) }
        guard let colon = host.lastIndex(of: ":") else { return nil }
        return Int(host[host.index(after: colon)...])
    }

    private static func sha256Hex(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}

private enum UIDeviceHardwareId {
    static var current: String {
        #if canImport(UIKit)
        return UIDevice.current.identifierForVendor?.uuidString ?? "mg-fallback-hwid"
        #else
        return "mg-fallback-hwid"
        #endif
    }
}
