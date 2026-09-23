import Foundation
import Security
import HarmonicKit

final class KeychainVault {
    private let service: String
    private let lock = NSLock()

    init(service: String) {
        self.service = service
    }

    private func selector(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    func read(account: String) -> Foundation.Data? {
        lock.lock()
        defer { lock.unlock() }
        var query = selector(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else {
            return nil
        }
        return result as? Foundation.Data
    }

    @discardableResult
    func write(_ data: Foundation.Data, account: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let updateSelector = selector(account: account)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let update = SecItemUpdate(updateSelector as CFDictionary, attributes as CFDictionary)
        if update == errSecSuccess {
            return true
        }
        guard update == errSecItemNotFound else {
            return false
        }
        var insertion = updateSelector
        attributes.forEach { insertion[$0.key] = $0.value }
        return SecItemAdd(insertion as CFDictionary, nil) == errSecSuccess
    }

    @discardableResult
    func remove(account: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let query = selector(account: account)
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}

final class IosCredentialStore: CredentialStore {
    private let vault: KeychainVault

    init(vault: KeychainVault) {
        self.vault = vault
    }

    func read(id: String) -> String? {
        vault.read(account: "credential.\(id)").flatMap {
            String(data: $0, encoding: .utf8)
        }
    }

    func write(id: String, value: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }
        return vault.write(data, account: "credential.\(id)")
    }

    func remove(id: String) -> Bool {
        vault.remove(account: "credential.\(id)")
    }
}

private struct StoredHackerNewsAccount: Codable {
    let username: String
    let password: String
}

final class IosAccountRepository: HackerNewsAccountRepository {
    private let vault: KeychainVault
    private let accountKey = "hacker-news-account"

    init(vault: KeychainVault) {
        self.vault = vault
    }

    func load() -> HackerNewsAccount? {
        guard
            let data = vault.read(account: accountKey),
            let stored = try? JSONDecoder().decode(StoredHackerNewsAccount.self, from: data),
            !stored.username.isEmpty,
            !stored.password.isEmpty
        else {
            return nil
        }
        return HackerNewsAccount(username: stored.username, password: stored.password)
    }

    func save(account: HackerNewsAccount) -> Bool {
        let stored = StoredHackerNewsAccount(
            username: account.username,
            password: account.password
        )
        guard let data = try? JSONEncoder().encode(stored) else { return false }
        return vault.write(data, account: accountKey)
    }

    func clear__() -> Bool {
        vault.remove(account: accountKey)
    }
}
