import Foundation
import UIKit
import Display
import AccountContext
import SGLarpCore
import SwiftSignalKit

public final class LarpFunctionsViewController: ViewController {
    private let context: AccountContext
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)

    private enum Row {
        case license
        case visualMenu
        case fakeGifts
        case fakeStars
        case fakeTon
        case fakeUsernames
        case fakePhone
        case fakeLevel
        case fakeHold
        case fakeMajor
        case fakeChannelEnable
        case fakeChannelLink
        case fakeChannelSubs
        case testCards
        case adminAdd
        case adminOverlay(LarpChannelAdminOverlay.Overlay)
    }

    private var rows: [Row] = []

    public init(context: AccountContext) {
        self.context = context
        super.init(navigationBarPresentationData: nil)
        self.title = "LarpGram Functions"
        let peerId = Int64(context.account.peerId.id._internalGetInt64Value())
        UserDefaults.standard.set(peerId, forKey: "mg_larp_self_user_id")
        LarpProfileRelay.setSelfUserId(peerId)
    }

    required init(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGroupedBackground
        tableView.dataSource = self
        tableView.delegate = self
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        rebuildRows()
        SGLarpLicenseManager.ensureActivated { [weak self] _, _ in
            DispatchQueue.main.async { self?.rebuildRows() }
        }
    }

    private func rebuildRows() {
        rows = [.license]
        guard SGLarpLicenseManager.isActive() else {
            tableView.reloadData()
            return
        }
        rows += [
            .visualMenu, .fakeGifts,
            .fakeStars, .fakeTon,
            .fakeUsernames, .fakePhone, .fakeLevel,
            .fakeHold, .fakeMajor,
            .fakeChannelEnable, .fakeChannelLink, .fakeChannelSubs,
            .adminAdd,
        ]
        rows += LarpChannelAdminOverlay.all().map { .adminOverlay($0) }
        rows.append(.testCards)
        tableView.reloadData()
    }

    private func prompt(title: String, value: String, handler: @escaping (String) -> Void) {
        let alert = UIAlertController(title: title, message: nil, preferredStyle: .alert)
        alert.addTextField { $0.text = value }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Save", style: .default) { _ in
            handler(alert.textFields?.first?.text ?? "")
        })
        present(alert, animated: true)
    }
}

extension LarpFunctionsViewController: UITableViewDataSource, UITableViewDelegate {
    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        rows.count
    }

    public func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let row = rows[indexPath.row]
        switch row {
        case .visualMenu, .fakeGifts, .fakeHold, .fakeMajor, .fakeChannelEnable:
            let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
            cell.selectionStyle = .none
            let toggle = UISwitch()
            cell.textLabel?.text = title(for: row)
            switch row {
            case .visualMenu: toggle.isOn = SGLarpSettings.visualMenu
            case .fakeGifts: toggle.isOn = SGLarpSettings.fakeGifts
            case .fakeHold: toggle.isOn = SGLarpSettings.fakeProfileHold
            case .fakeMajor: toggle.isOn = SGLarpSettings.fakeProfileMajor
            case .fakeChannelEnable: toggle.isOn = SGLarpSettings.fakePersonalChannelEnabled
            default: break
            }
            toggle.tag = indexPath.row
            toggle.addTarget(self, action: #selector(toggleChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle
            return cell
        default:
            let cell = UITableViewCell(style: .value1, reuseIdentifier: nil)
            cell.textLabel?.text = title(for: row)
            cell.detailTextLabel?.text = detail(for: row)
            cell.accessoryType = .disclosureIndicator
            return cell
        }
    }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        switch rows[indexPath.row] {
        case .license:
            prompt(title: "License key", value: SGLarpSettings.licenseKey) { text in
                SGLarpLicenseManager.activate(key: text) { ok, _ in
                    if ok { SGLarpLicenseManager.start() }
                    DispatchQueue.main.async { self.rebuildRows() }
                }
            }
        case .fakeStars:
            prompt(title: "Fake Stars", value: "\(SGLarpSettings.fakeStarsAmount)") { text in
                SGLarpSettings.fakeStarsAmount = Int64(text) ?? 0
                self.rebuildRows()
            }
        case .fakeTon:
            prompt(title: "Fake TON", value: "\(SGLarpSettings.fakeTonAmount)") { text in
                SGLarpSettings.fakeTonAmount = Int64(text) ?? 0
                self.rebuildRows()
            }
        case .fakeUsernames:
            prompt(title: "Fragment usernames", value: SGLarpSettings.fakeFragmentUsernames) { text in
                SGLarpSettings.fakeFragmentUsernames = text
                self.rebuildRows()
            }
        case .fakePhone:
            prompt(title: "Fake phone", value: SGLarpSettings.fakePhoneNumber) { text in
                SGLarpSettings.fakePhoneNumber = text
                self.rebuildRows()
            }
        case .fakeLevel:
            prompt(title: "Stars level", value: "\(SGLarpSettings.fakeProfileLevel)") { text in
                SGLarpSettings.fakeProfileLevel = min(100, max(0, Int(text) ?? 0))
                self.rebuildRows()
            }
        case .fakeChannelLink:
            prompt(title: "Channel link", value: SGLarpSettings.fakePersonalChannelLink) { text in
                SGLarpSettings.fakePersonalChannelLink = text
                self.rebuildRows()
            }
        case .fakeChannelSubs:
            prompt(title: "Subscribers", value: "\(SGLarpSettings.fakePersonalChannelSubscribers)") { text in
                SGLarpSettings.fakePersonalChannelSubscribers = Int(text) ?? 0
                self.rebuildRows()
            }
        case .adminAdd:
            prompt(title: "Channel link", value: "") { text in
                var overlay = LarpChannelAdminOverlay.Overlay()
                overlay.chatId = Int64(abs(text.hashValue))
                overlay.enabled = true
                overlay.link = text
                overlay.title = text
                LarpChannelAdminOverlay.save(overlay)
                self.rebuildRows()
            }
        case let .adminOverlay(overlay):
            var copy = overlay
            copy.enabled.toggle()
            LarpChannelAdminOverlay.save(copy)
            rebuildRows()
        case .testCards:
            let cards = LarpPaymentHelper.getExampleTestCards(randomCount: 5)
            let alert = UIAlertController(title: "Test cards", message: cards.joined(separator: "\n"), preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            present(alert, animated: true)
        default:
            break
        }
    }

    @objc private func toggleChanged(_ sender: UISwitch) {
        let row = rows[sender.tag]
        switch row {
        case .visualMenu: SGLarpSettings.visualMenu = sender.isOn
        case .fakeGifts: SGLarpSettings.fakeGifts = sender.isOn
        case .fakeHold: SGLarpSettings.fakeProfileHold = sender.isOn
        case .fakeMajor: SGLarpSettings.fakeProfileMajor = sender.isOn
        case .fakeChannelEnable: SGLarpSettings.fakePersonalChannelEnabled = sender.isOn
        default: break
        }
    }

    private func title(for row: Row) -> String {
        switch row {
        case .license: return "License"
        case .visualMenu: return "Visual menu"
        case .fakeGifts: return "Fake gifts"
        case .fakeStars: return "Fake Stars"
        case .fakeTon: return "Fake TON"
        case .fakeUsernames: return "Fragment usernames"
        case .fakePhone: return "Fake phone"
        case .fakeLevel: return "Stars level"
        case .fakeHold: return "HOLD badge"
        case .fakeMajor: return "Major badge"
        case .fakeChannelEnable: return "Fake personal channel"
        case .fakeChannelLink: return "Channel link"
        case .fakeChannelSubs: return "Subscribers"
        case .testCards: return "Stripe test cards"
        case .adminAdd: return "Add channel overlay"
        case let .adminOverlay(o):
            if !o.title.isEmpty { return o.title }
            if !o.link.isEmpty { return o.link }
            return "Channel \(o.chatId)"
        }
    }

    private func detail(for row: Row) -> String {
        switch row {
        case .license:
            return SGLarpLicenseManager.isActive() ? "Active" : (SGLarpSettings.licenseKey.isEmpty ? "Need key" : "Checking")
        case .fakeStars:
            return SGLarpSettings.fakeStarsAmount > 0 ? "\(SGLarpSettings.fakeStarsAmount)" : "Off"
        case .fakeTon:
            return SGLarpSettings.fakeTonAmount > 0 ? "\(SGLarpSettings.fakeTonAmount)" : "Off"
        case .fakeUsernames:
            return SGLarpSettings.fakeFragmentUsernames.isEmpty ? "Off" : SGLarpSettings.fakeFragmentUsernames
        case .fakePhone:
            return SGLarpSettings.fakePhoneNumber.isEmpty ? "Off" : SGLarpSettings.fakePhoneNumber
        case .fakeLevel:
            return SGLarpSettings.fakeProfileLevel > 0 ? "\(SGLarpSettings.fakeProfileLevel)" : "Off"
        case .fakeChannelLink:
            return SGLarpSettings.fakePersonalChannelLink.isEmpty ? "Off" : SGLarpSettings.fakePersonalChannelLink
        case .fakeChannelSubs:
            return SGLarpSettings.fakePersonalChannelSubscribers > 0 ? "\(SGLarpSettings.fakePersonalChannelSubscribers)" : "Off"
        case .testCards:
            return "\(LarpPaymentHelper.getExampleTestCards(randomCount: 0).count)+"
        case let .adminOverlay(o):
            return o.enabled ? "On" : "Off"
        default:
            return ""
        }
    }
}

public func sgLarpFunctionsController(context: AccountContext) -> ViewController {
    LarpFunctionsViewController(context: context)
}
