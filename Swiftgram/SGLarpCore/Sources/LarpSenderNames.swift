import Foundation

public enum LarpSenderNames {
    public static func fromProfile(_ profile: [String: Any]?) -> String {
        guard let profile else { return "" }
        let first = (profile["first_name"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let last = (profile["last_name"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let name = "\(first) \(last)".trimmingCharacters(in: .whitespacesAndNewlines)
        if !name.isEmpty { return name }
        if let usernames = profile["usernames"] as? [String], let u = usernames.first?.trimmingCharacters(in: .whitespacesAndNewlines), !u.isEmpty {
            return u.hasPrefix("@") ? String(u.dropFirst()) : u
        }
        let username = (profile["username"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if username.isEmpty { return "" }
        return username.hasPrefix("@") ? String(username.dropFirst()) : username
    }

    public static func giftNoteFromProfile(_ profile: [String: Any]?, userMessage: String?) -> String {
        let trimmed = userMessage?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed
    }
}
