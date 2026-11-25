import Foundation
import SwiftUI

struct PendingFile: Identifiable, Equatable {
    let id = UUID()
    var name: String
    var mime = ""
    var size: Int64 = 0
    var preview: Data?
    var uploading = true
    var attachment: Attachment?
    var error: String?
}

@MainActor
final class ChatViewModel: ObservableObject {
    static let maxFiles = 5

    @Published private(set) var chat: Chat?
    @Published var messages: [Message] = []
    @Published var pending: [PendingFile] = []
    @Published var input: String
    @Published private(set) var streaming = false
    @Published private(set) var loading = false
    @Published var error: String?
    @Published var limitReached = false

    let mode: String
    private var store: AuraStore?
    private var streamingId: String?
    private var task: Task<Void, Never>?
    private let initialChatId: String?

    init(chatId: String?, mode: String, initialText: String) {
        initialChatId = chatId
        self.mode = mode
        input = initialText
    }

    var currentMode: String { chat?.mode ?? mode }

    var canSend: Bool {
        !streaming && !pending.contains(where: \.uploading) &&
            (!input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || pending.contains { $0.attachment != nil })
    }

    func attach(to store: AuraStore) {
        guard self.store == nil else { return }
        self.store = store
        if let id = initialChatId {
            chat = store.chats.first { $0.id == id }
            messages = store.cachedMessages(chatId: id)
            reload(id)
        }
        if let draft = store.pendingDraft {
            store.pendingDraft = nil
            if !draft.text.isEmpty { input = draft.text }
            if draft.autoSend && !draft.text.isEmpty { send() }
        }
    }

    private func reload(_ id: String) {
        guard let store else { return }
        loading = messages.isEmpty
        Task {
            defer { loading = false }
            do {
                let res = try await store.loadMessages(chatId: id)
                chat = res.chat
                if !streaming { messages = res.messages }
            } catch {
                if messages.isEmpty { self.error = error.localizedDescription }
            }
        }
    }

    func send(_ textOverride: String? = nil) {
        if let textOverride { input = textOverride }
        guard canSend, let store else { return }
        if store.messageLimitReached {
            limitReached = true
            return
        }
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let files = pending.compactMap(\.attachment)
        input = ""
        pending = []
        error = nil
        limitReached = false
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let tempUser = Message(id: "local-u-\(now)", role: "user", content: text, createdAt: now, attachments: files)
        messages.append(tempUser)
        runTurn(text: text, files: files, tempUserId: tempUser.id)
    }

    /// Asks Aura to answer the last message again (after an error or a stop).
    func retry() {
        guard !streaming, let last = messages.last else { return }
        if !last.isUser { messages.removeLast() }
        error = nil
        runTurn(text: nil, files: [], tempUserId: nil)
    }

    func stop() {
        task?.cancel()
    }

    private func runTurn(text: String?, files: [Attachment], tempUserId: String?) {
        guard let store else { return }
        streaming = true
        let placeholder = Message(id: "local-a-\(UUID().uuidString)", role: "assistant", content: "", status: MessageStatus.streaming)
        messages.append(placeholder)
        streamingId = placeholder.id

        task = Task {
            do {
                var current = chat
                if current == nil {
                    current = try await store.createChat(mode: mode)
                    chat = current
                }
                guard let chatId = current?.id else { return }
                for try await event in store.api.streamReply(chatId: chatId, text: text, attachmentIds: files.map(\.id)) {
                    switch event {
                    case .userSaved(let message):
                        replace(tempUserId, with: message)
                    case .assistantStarted(let id):
                        update(streamingId) { $0.id = id }
                        streamingId = id
                    case .delta(let piece):
                        update(streamingId) { $0.content += piece }
                    case .failed(_, let message):
                        error = message
                    case .done(let message, let newChat):
                        replace(streamingId, with: message)
                        chat = newChat
                        store.upsert(newChat)
                        store.bumpUsage(messages: 1)
                    }
                }
            } catch is CancellationError {
                update(streamingId) { $0.status = MessageStatus.stopped }
            } catch let apiError as APIError {
                if apiError.isLimit {
                    limitReached = true
                    messages.removeAll { $0.id == streamingId || $0.id == tempUserId }
                    if let text { input = text }
                } else {
                    update(streamingId) { $0.status = MessageStatus.error }
                    error = apiError.message
                }
            } catch {
                update(streamingId) { $0.status = MessageStatus.error }
                self.error = error.localizedDescription
            }
            if Task.isCancelled { update(streamingId) { $0.status = MessageStatus.stopped } }
            streaming = false
            streamingId = nil
            if let id = chat?.id { store.saveMessages(chatId: id, messages) }
        }
    }

    private func replace(_ id: String?, with message: Message) {
        guard let id, let i = messages.firstIndex(where: { $0.id == id }) else { return }
        messages[i] = message
    }

    private func update(_ id: String?, _ change: (inout Message) -> Void) {
        guard let id, let i = messages.firstIndex(where: { $0.id == id }) else { return }
        change(&messages[i])
    }

    // MARK: Attachments

    func addFile(_ file: PickedFile?) {
        guard let store else { return }
        guard pending.count < Self.maxFiles else {
            error = "Можно приложить не больше \(Self.maxFiles) файлов"
            return
        }
        guard let file else {
            error = "Не удалось открыть файл"
            return
        }
        var item = PendingFile(name: file.name, mime: file.mime, size: Int64(file.data.count))
        if file.mime.hasPrefix("image/") { item.preview = file.data }
        pending.append(item)
        let id = item.id
        Task {
            do {
                let uploaded = try await store.api.upload(data: file.data, mime: file.mime, name: file.name)
                updatePending(id) { $0.uploading = false; $0.attachment = uploaded }
            } catch {
                updatePending(id) { $0.uploading = false; $0.error = error.localizedDescription }
            }
        }
    }

    func removePending(_ id: UUID) {
        pending.removeAll { $0.id == id }
    }

    private func updatePending(_ id: UUID, _ change: (inout PendingFile) -> Void) {
        guard let i = pending.firstIndex(where: { $0.id == id }) else { return }
        change(&pending[i])
    }

    // MARK: Chat actions

    func rename(_ title: String) {
        guard let store, let current = chat else { return }
        Task {
            do {
                try await store.renameChat(id: current.id, title: title)
                chat?.title = title
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    func delete(onDone: @escaping () -> Void) {
        guard let store, let current = chat else { onDone(); return }
        Task {
            do {
                try await store.deleteChat(id: current.id)
                onDone()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
