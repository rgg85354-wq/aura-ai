import Foundation
import Security

enum AppConfig {
    /// Server used by a fresh install. Change it here, or in the app: Settings → Server.
    /// The iOS Simulator can reach a server on the same Mac at http://localhost:8787.
    static let defaultServerURL = "http://localhost:8787"
}

/// Session token in the Keychain, everything else in UserDefaults.
final class SessionStore {
    private let defaults = UserDefaults.standard
    private let service = "com.aura.ai.session"

    var token: String? {
        get { readKeychain() }
        set { newValue == nil ? deleteKeychain() : writeKeychain(newValue!) }
    }

    var serverURL: String {
        get { defaults.string(forKey: "serverURL") ?? AppConfig.defaultServerURL }
        set {
            var value = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
            while value.hasSuffix("/") { value.removeLast() }
            defaults.set(value, forKey: "serverURL")
        }
    }

    var hapticsEnabled: Bool {
        get { defaults.object(forKey: "haptics") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "haptics") }
    }

    var lastSync: Date? {
        get { defaults.object(forKey: "lastSync") as? Date }
        set { defaults.set(newValue, forKey: "lastSync") }
    }

    var cachedAccount: Account? {
        get {
            guard let data = defaults.data(forKey: "account") else { return nil }
            return try? JSONDecoder().decode(Account.self, from: data)
        }
        set {
            if let value = newValue, let data = try? JSONEncoder().encode(value) {
                defaults.set(data, forKey: "account")
            } else {
                defaults.removeObject(forKey: "account")
            }
        }
    }

    func clear() {
        token = nil
        cachedAccount = nil
        lastSync = nil
    }

    // MARK: Keychain

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: "token"]
    }

    private func readKeychain() -> String? {
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func writeKeychain(_ value: String) {
        let data = Data(value.utf8)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var q = query
            q[kSecValueData as String] = data
            q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            SecItemAdd(q as CFDictionary, nil)
        }
    }

    private func deleteKeychain() {
        SecItemDelete(query as CFDictionary)
    }
}
