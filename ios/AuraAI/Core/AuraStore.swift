import Foundation
import SwiftUI

/// Single source of truth for the app. The server keeps the history, so every device
/// signed in to the same account sees the same chats; a small cache keeps the last copy offline.
@MainActor
final class AuraStore: ObservableObject {
    let session: SessionStore
    let api: APIClient

    @Published private(set) var loggedIn: Bool
    @Published private(set) var account: Account?
    @Published private(set) var chats: [Chat] = []
    @Published private(set) var images: [GeneratedImage] = []
    @Published private(set) var syncing = false
    @Published private(set) var lastSync: Date?

    var pendingDraft: Draft?

    private let cacheDir: URL

    init() {
        let session = SessionStore()
        self.session = session
        api = APIClient(session: session)
        loggedIn = session.token != nil
        account = session.cachedAccount
        lastSync = session.lastSync
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        cacheDir = base.appendingPathComponent("AuraCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        chats = readCache("chats.json") ?? []
        images = readCache("images.json") ?? []
        api.onUnauthorized = { [weak self] in
            Task { @MainActor in self?.signOutLocally() }
        }
    }

    var isPro: Bool { account?.user.isPro == true }

    // MARK: Auth

    func login(email: String, password: String) async throws {
        try await finishAuth(api.login(email: email.trimmingCharacters(in: .whitespaces), password: password))
    }

    func register(name: String, email: String, password: String) async throws {
        try await finishAuth(api.register(
            name: name.trimmingCharacters(in: .whitespaces),
            email: email.trimmingCharacters(in: .whitespaces),
            password: password
        ))
    }

    private func finishAuth(_ res: AuthResponse) async throws {
        session.token = res.token
        setAccount(res.account)
        loggedIn = true
        try? await sync()
    }

    func logout() async {
        try? await api.logout()
        signOutLocally()
    }

    func deleteAccount() async throws {
        try await api.deleteAccount()
        signOutLocally()
    }

    func signOutLocally() {
        session.clear()
        account = nil
        chats = []
        images = []
        lastSync = nil
        try? FileManager.default.removeItem(at: cacheDir)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        loggedIn = false
    }

    func applyAccount(_ account: Account) { setAccount(account) }

    private func setAccount(_ account: Account) {
        self.account = account
        session.cachedAccount = account
    }

    // MARK: Sync

    /// Pulls profile, chat list and images from the server.
    func sync() async throws {
        guard session.token != nil else { return }
        syncing = true
        defer { syncing = false }
        let me = try await api.me()
        let chatList = try await api.chats()
        let imageList = try await api.images()
        setAccount(me)
        setChats(chatList)
        setImages(imageList)
        let now = Date()
        session.lastSync = now
        lastSync = now
    }

    // MARK: Profile

    func updateProfile(name: String? = nil, responseStyle: String? = nil, language: String? = nil) async throws {
        let updated = try await api.updateProfile(name: name, responseStyle: responseStyle, language: language)
        setAccount(updated)
    }

    func changePassword(current: String, new: String) async throws {
        try await api.changePassword(current: current, new: new)
    }

    func bumpUsage(messages: Int = 0, images: Int = 0) {
        guard var acc = account else { return }
        acc.usage.messagesToday += messages
        acc.usage.imagesToday += images
        setAccount(acc)
    }

    var messageLimitReached: Bool {
        guard let acc = account, acc.limits.dailyMessages > 0 else { return false }
        return acc.usage.messagesToday >= acc.limits.dailyMessages
    }

    // MARK: Chats

    private func setChats(_ list: [Chat]) {
        chats = list.sorted { $0.updatedAt > $1.updatedAt }
        writeCache("chats.json", chats)
    }

    func upsert(_ chat: Chat) {
        setChats([chat] + chats.filter { $0.id != chat.id })
    }

    func searchChats(_ query: String) async throws -> [Chat] {
        try await api.chats(query: query)
    }

    func createChat(mode: String) async throws -> Chat {
        let chat = try await api.createChat(mode: mode)
        upsert(chat)
        return chat
    }

    func renameChat(id: String, title: String) async throws {
        let chat = try await api.renameChat(id: id, title: title)
        upsert(chat)
    }

    func deleteChat(id: String) async throws {
        try await api.deleteChat(id: id)
        setChats(chats.filter { $0.id != id })
        try? FileManager.default.removeItem(at: cacheDir.appendingPathComponent("messages_\(id).json"))
    }

    func clearHistory() async throws {
        try await api.deleteAllChats()
        setChats([])
        let files = (try? FileManager.default.contentsOfDirectory(at: cacheDir, includingPropertiesForKeys: nil)) ?? []
        for file in files where file.lastPathComponent.hasPrefix("messages_") {
            try? FileManager.default.removeItem(at: file)
        }
    }

    func cachedMessages(chatId: String) -> [Message] {
        readCache("messages_\(chatId).json") ?? []
    }

    func loadMessages(chatId: String) async throws -> MessagesResponse {
        let res = try await api.messages(chatId: chatId)
        saveMessages(chatId: chatId, res.messages)
        upsert(res.chat)
        return res
    }

    func saveMessages(chatId: String, _ messages: [Message]) {
        writeCache("messages_\(chatId).json", messages)
    }

    // MARK: Images

    private func setImages(_ list: [GeneratedImage]) {
        images = list
        writeCache("images.json", list)
    }

    func generateImage(prompt: String, style: String, size: String) async throws -> GeneratedImage {
        let image = try await api.generateImage(prompt: prompt, style: style, size: size)
        setImages([image] + images)
        bumpUsage(images: 1)
        return image
    }

    func deleteImage(id: String) async throws {
        try await api.deleteImage(id: id)
        setImages(images.filter { $0.id != id })
    }

    func url(_ path: String) -> URL? { api.absoluteURL(path) }

    // MARK: Cache

    private func readCache<T: Decodable>(_ name: String) -> T? {
        guard let data = try? Data(contentsOf: cacheDir.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private func writeCache<T: Encodable>(_ name: String, _ value: T) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: cacheDir.appendingPathComponent(name), options: .atomic)
    }
}
