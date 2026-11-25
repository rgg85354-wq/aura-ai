import CoreGraphics
import Foundation

struct Preferences: Codable, Equatable {
    var responseStyle: String = "balanced"
    var language: String = "auto"
}

struct User: Codable, Equatable, Identifiable {
    let id: String
    var email: String
    var name: String
    var createdAt: Int64 = 0
    var isPro: Bool = false
    var proUntil: Int64?
    var proPlatform: String?
    var preferences: Preferences = Preferences()
}

struct Usage: Codable, Equatable {
    var messagesToday: Int = 0
    var imagesToday: Int = 0
}

struct Limits: Codable, Equatable {
    var dailyMessages: Int = 0
    var dailyImages: Int = 0
}

struct Account: Codable, Equatable {
    var user: User
    var usage: Usage
    var limits: Limits
}

struct AuthResponse: Codable {
    let token: String
    let user: User
    let usage: Usage
    let limits: Limits
    var account: Account { Account(user: user, usage: usage, limits: limits) }
}

enum ChatMode {
    static let chat = "chat"
    static let code = "code"
    static let ideas = "ideas"

    static func title(_ mode: String) -> String {
        switch mode {
        case code: return "Код"
        case ideas: return "Идеи"
        default: return "Чат"
        }
    }

    static func icon(_ mode: String) -> String {
        switch mode {
        case code: return "chevron.left.forwardslash.chevron.right"
        case ideas: return "lightbulb"
        default: return "bubble.left.and.bubble.right"
        }
    }
}

struct Chat: Codable, Equatable, Identifiable, Hashable {
    let id: String
    var title: String
    var mode: String = ChatMode.chat
    var createdAt: Int64 = 0
    var updatedAt: Int64 = 0
    var preview: String = ""
    var messageCount: Int = 0
}

struct Attachment: Codable, Equatable, Identifiable, Hashable {
    let id: String
    let name: String
    let mime: String
    var size: Int64 = 0
    let url: String
    var isImage: Bool { mime.hasPrefix("image/") }
}

enum MessageStatus {
    static let complete = "complete"
    static let streaming = "streaming"
    static let stopped = "stopped"
    static let error = "error"
    static let refused = "refused"
    static let truncated = "truncated"
}

struct Message: Codable, Equatable, Identifiable {
    var id: String
    var chatId: String = ""
    var role: String
    var content: String
    var status: String = MessageStatus.complete
    var createdAt: Int64 = 0
    var attachments: [Attachment] = []
    var isUser: Bool { role == "user" }
}

struct GeneratedImage: Codable, Equatable, Identifiable, Hashable {
    let id: String
    let prompt: String
    var style: String = "none"
    var size: String = "1024x1024"
    var createdAt: Int64 = 0
    let url: String

    var aspectRatio: CGFloat {
        let parts = size.split(separator: "x").compactMap { Double($0) }
        guard parts.count == 2, parts[1] > 0 else { return 1 }
        return CGFloat(parts[0] / parts[1])
    }
}

/// Text and files typed on the home screen, handed over to a new chat.
struct Draft {
    var text: String
    var autoSend: Bool = true
}

// MARK: - Response wrappers

struct ChatsResponse: Codable { let chats: [Chat] }
struct ChatResponse: Codable { let chat: Chat }
struct MessagesResponse: Codable { let chat: Chat; let messages: [Message] }
struct FileResponse: Codable { let file: Attachment }
struct ImagesResponse: Codable { let images: [GeneratedImage] }
struct ImageResponse: Codable { let image: GeneratedImage }
struct ErrorBody: Codable { let error: ErrorInfo }
struct ErrorInfo: Codable { let code: String; let message: String }
struct StreamStart: Codable { let id: String }
struct StreamDelta: Codable { let text: String }
struct StreamError: Codable { let code: String; let message: String }
struct StreamDone: Codable { let message: Message; let chat: Chat }

enum StreamEvent {
    case userSaved(Message)
    case assistantStarted(String)
    case delta(String)
    case failed(code: String, message: String)
    case done(Message, Chat)
}

struct APIError: LocalizedError, Equatable {
    let code: String
    let message: String
    var status: Int = 0

    var errorDescription: String? { message }
    var isLimit: Bool { code == "limit_reached" }

    static let network = APIError(code: "network", message: "Нет связи с сервером Aura. Проверьте интернет и адрес сервера в настройках.")
}

// MARK: - Formatting

enum Format {
    private static let ru = Locale(identifier: "ru_RU")

    static func relative(_ millis: Int64) -> String {
        guard millis > 0 else { return "" }
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        let minutes = Int(Date().timeIntervalSince(date) / 60)
        if minutes < 1 { return "только что" }
        if minutes < 60 { return "\(minutes) мин назад" }
        if minutes < 24 * 60 { return "\(minutes / 60) ч назад" }
        if Calendar.current.isDateInYesterday(date) { return "вчера" }
        let f = DateFormatter()
        f.locale = ru
        f.dateFormat = "d MMM"
        return f.string(from: date)
    }

    static func dayGroup(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        if Calendar.current.isDateInToday(date) { return "Сегодня" }
        if Calendar.current.isDateInYesterday(date) { return "Вчера" }
        if Date().timeIntervalSince(date) < 7 * 24 * 3600 { return "На этой неделе" }
        return "Ранее"
    }

    static func date(_ millis: Int64) -> String {
        let f = DateFormatter()
        f.locale = ru
        f.dateFormat = "d MMMM yyyy"
        return f.string(from: Date(timeIntervalSince1970: TimeInterval(millis) / 1000))
    }

    static func size(_ bytes: Int64) -> String {
        if bytes < 1024 { return "\(bytes) Б" }
        if bytes < 1024 * 1024 { return "\(bytes / 1024) КБ" }
        return String(format: "%.1f МБ", Double(bytes) / 1024 / 1024)
    }
}
