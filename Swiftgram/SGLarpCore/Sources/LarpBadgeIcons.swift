import Foundation

public enum LarpBadgeIcons {
    public static let packShortName = "MajorVerification"
    public static let packId: Int64 = 3_967_798_175_112_101_917
    public static let holdDocumentId: Int64 = 5_229_027_828_527_309_057
    public static let majorDocumentId: Int64 = 5_440_670_060_093_922_400

    public static func preload() {
        _ = holdDocumentId
        _ = majorDocumentId
    }
}
