import Foundation

// Assuming NGLogging and AppBundle are custom modules, they are imported here.
import SGLogging
import AppBundle


public let SGFallbackLocale = "en"

public class SGLocalizationManager {
    
    public static let shared = SGLocalizationManager()
    
    private let appBundle: Bundle
    private var localizations: [String: [String: String]] = [:]
    private var webLocalizations: [String: [String: String]] = [:]
    private let loadLock = NSLock()
    private let fallbackMappings: [String: String] = [
        // "from": "to"
        "zh-hant": "zh-hans",
        "be": "ru",
        "nb": "no",
        "ckb": "ku",
        "sdh": "ku"
    ]
    
    private init() {
        self.appBundle = getAppBundle()
    }
    
    public func localizedString(_ key: String, _ locale: String = SGFallbackLocale, args: CVarArg...) -> String {
        let sanitizedLocale = self.sanitizeLocale(locale)
        
        if let localizedString = findLocalizedString(forKey: key, inLocale: sanitizedLocale) {
            if args.isEmpty {
                return localizedString
            } else {
                return String(format: localizedString, arguments: args)
            }
        }
        
        SGLogger.shared.log("Strings", "Missing string for key: \(key) in locale: \(locale)")
        return key
    }
    
    private func cachedLocalDictionary(for locale: String) -> [String: String] {
        loadLock.lock()
        if let cached = localizations[locale] {
            loadLock.unlock()
            return cached
        }
        loadLock.unlock()

        let loaded = Self.loadStringsFile(from: appBundle, locale: locale)

        loadLock.lock()
        localizations[locale] = loaded
        loadLock.unlock()
        return loaded
    }

    private static func loadStringsFile(from bundle: Bundle, locale: String) -> [String: String] {
        guard let path = bundle.path(forResource: "SGLocalizable", ofType: "strings", inDirectory: nil, forLocalization: locale) else {
            return [:]
        }
        let url = URL(fileURLWithPath: path)
        guard let data = try? Data(contentsOf: url, options: [.mappedIfSafe]) else {
            return [:]
        }
        return parseStringsPlist(data) ?? [:]
    }
    
    public func downloadLocale(_ locale: String) {
        #if DEBUG
        SGLogger.shared.log("Strings", "DEBUG ignoring locale download: \(locale)")
        return
        #endif
        let sanitizedLocale = self.sanitizeLocale(locale)
        guard let url = URL(string: self.getStringsUrl(for: sanitizedLocale)) else {
            SGLogger.shared.log("Strings", "Invalid URL for locale: \(sanitizedLocale)")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 20

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self else { return }
            if error != nil {
                SGLogger.shared.log("Strings", "Failed to download \(sanitizedLocale)")
                return
            }
            guard let http = response as? HTTPURLResponse, (200 ..< 300).contains(http.statusCode),
                  let data, let localeDict = Self.parseStringsPlist(data)
            else {
                SGLogger.shared.log("Strings", "Failed to download \(sanitizedLocale)")
                return
            }
            DispatchQueue.main.async {
                self.loadLock.lock()
                self.webLocalizations[sanitizedLocale] = localeDict
                self.loadLock.unlock()
                SGLogger.shared.log("Strings", "Successfully downloaded locale \(sanitizedLocale)")
            }
        }.resume()
    }

    private static func parseStringsPlist(_ data: Data) -> [String: String]? {
        guard let plist = try? PropertyListSerialization.propertyList(from: data, options: [], format: nil) else {
            return nil
        }
        guard let raw = plist as? [String: Any] else { return nil }
        var result: [String: String] = [:]
        result.reserveCapacity(raw.count)
        for (key, value) in raw {
            guard let stringValue = value as? String else { continue }
            result[key] = stringValue
        }
        return result.isEmpty ? nil : result
    }
    
    private func sanitizeLocale(_ locale: String) -> String {
        var sanitizedLocale = locale
        let rawSuffix = "-raw"
        if locale.hasSuffix(rawSuffix) {
            sanitizedLocale = String(locale.dropLast(rawSuffix.count))
        }

        if sanitizedLocale == "pt-br" {
            sanitizedLocale = "pt"
        } else if sanitizedLocale == "nb" {
            sanitizedLocale = "no"
        }

        return sanitizedLocale
    }

    private func findLocalizedString(forKey key: String, inLocale locale: String) -> String? {
        loadLock.lock()
        let web = webLocalizations[locale]?[key]
        loadLock.unlock()
        if let web, !web.isEmpty {
            return web
        }

        let bundleStrings = cachedLocalDictionary(for: locale)
        if let string = bundleStrings[key], !string.isEmpty {
            return string
        }
        if let fallbackLocale = self.fallbackMappings[locale] {
            return self.findLocalizedString(forKey: key, inLocale: fallbackLocale)
        }
        return cachedLocalDictionary(for: SGFallbackLocale)[key]
    }

    private func getStringsUrl(for locale: String) -> String {
        return "https://raw.githubusercontent.com/Swiftgram/Telegram-iOS/master/Swiftgram/SGStrings/Strings/\(locale).lproj/SGLocalizable.strings"
    }

}

public let i18n = SGLocalizationManager.shared.localizedString


public extension String {
    func i18n(_ locale: String = SGFallbackLocale, args: CVarArg...) -> String {
        return SGLocalizationManager.shared.localizedString(self, locale, args: args)
    }
}
