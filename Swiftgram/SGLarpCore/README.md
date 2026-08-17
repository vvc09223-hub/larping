# SGLarpCore / SGLarpUI

Swift port of Android **LarpGram** helpers from `Telegram-master` (Nekogram fork).

## Android → iOS file map

| Android (Java) | iOS (Swift) |
|---|---|
| `LarpFunctionsActivity.java` | `SGLarpUI/Sources/LarpFunctionsController.swift` |
| `LarpBalanceHelper.java` | `SGLarpCore/Sources/LarpBalanceHelper.swift` |
| `LarpPaymentHelper.java` | `SGLarpCore/Sources/LarpPaymentHelper.swift` |
| `LarpTransactionHelper.java` | `SGLarpCore/Sources/LarpTransactionHelper.swift` |
| `LarpGiftStore.java` | `SGLarpCore/Sources/LarpGiftStore.swift` |
| `LarpGiftsInjector.java` | `SGLarpCore/Sources/LarpGiftsInjector.swift` |
| `LarpGiftActionsHelper.java` | `SGLarpCore/Sources/LarpGiftActionsHelper.swift` |
| `LarpSenderNames.java` | `SGLarpCore/Sources/LarpSenderNames.swift` |
| `LarpProfileRelay.java` | `SGLarpCore/Sources/LarpProfileRelay.swift` |
| `LarpPersonalChannel.java` | `SGLarpCore/Sources/LarpPersonalChannel.swift` |
| `LarpChannelAdminOverlay.java` | `SGLarpCore/Sources/LarpChannelAdminOverlay.swift` |
| `LarpChannelRelay.java` | `SGLarpCore/Sources/LarpChannelRelay.swift` |
| `LarpBadgeIcons.java` | `SGLarpCore/Sources/LarpBadgeIcons.swift` |
| `LicenseManager.java` | `SGLarpCore/Sources/SGLarpLicenseManager.swift` |
| `GiftRelayManager.java` | `SGLarpCore/Sources/SGLarpGiftRelayManager.swift` |

Original Java sources are kept under `AndroidReference/` for diffing.

## Relay server

Default: `http://2.26.48.141:1048` — same as Android LarpGram.

## Settings entry

Profile → Settings → **LarpGram Functions** (Swiftgram section).

## Next integration steps (TelegramUI hooks)

Wire `LarpProfileRelay`, `LarpBalanceHelper`, and Stars/Gifts UI overrides the same way Android hooks `ProfileActivity`, `StarsController`, and `StarGiftSheet`. Core storage + relay APIs are ready in this module.
