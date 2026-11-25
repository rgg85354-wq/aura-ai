import Foundation

/// HTTP client for the Aura server. Every call throws `APIError` with a readable message.
final class APIClient {
    let session: SessionStore
    /// Called when the server rejects the token so the app can return to sign-in.
    var onUnauthorized: (() -> Void)?

    private let http: URLSession
    private let streamHTTP: URLSession
    private let decoder = JSONDecoder()

    init(session: SessionStore) {
        self.session = session
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 200 // image generation can take a while
        config.waitsForConnectivity = false
        http = URLSession(configuration: config)
        let streamConfig = URLSessionConfiguration.default
        streamConfig.timeoutIntervalForRequest = 90 // server sends a heartbeat every 15 s
        streamHTTP = URLSession(configuration: streamConfig)
    }

    func absoluteURL(_ path: String) -> URL? {
        if path.hasPrefix("http") { return URL(string: path) }
        return URL(string: session.serverURL + path)
    }

    private func makeRequest(_ method: String, _ path: String, json: [String: Any]? = nil, auth: Bool = true) throws -> URLRequest {
        guard let url = absoluteURL("/api" + path) else {
            throw APIError(code: "bad_url", message: "Некорректный адрес сервера. Исправьте его в настройках.")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        if auth, let token = session.token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let json {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: json)
        }
        return request
    }

    private func perform(_ request: URLRequest) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await http.data(for: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw APIError.network
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw errorFrom(status: status, data: data) }
        return data
    }

    private func send<T: Decodable>(_ type: T.Type, _ request: URLRequest) async throws -> T {
        let data = try await perform(request)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError(code: "bad_response", message: "Сервер вернул неожиданный ответ. Проверьте адрес сервера.")
        }
    }

    private func errorFrom(status: Int, data: Data) -> APIError {
        let info = try? decoder.decode(ErrorBody.self, from: data).error
        if status == 401, session.token != nil {
            DispatchQueue.main.async { self.onUnauthorized?() }
        }
        return APIError(code: info?.code ?? "http_\(status)", message: info?.message ?? "Ошибка сервера (\(status))", status: status)
    }

    // MARK: Health

    func checkServer(_ url: String) async -> Bool {
        guard let u = URL(string: url.trimmingCharacters(in: .whitespaces) + "/api/health") else { return false }
        var request = URLRequest(url: u)
        request.timeoutInterval = 8
        guard let (_, response) = try? await http.data(for: request) else { return false }
        return (response as? HTTPURLResponse)?.statusCode == 200
    }

    // MARK: Auth & profile

    func register(name: String, email: String, password: String) async throws -> AuthResponse {
        try await send(AuthResponse.self, makeRequest("POST", "/auth/register", json: [
            "name": name, "email": email, "password": password, "device": "iOS",
        ], auth: false))
    }

    func login(email: String, password: String) async throws -> AuthResponse {
        try await send(AuthResponse.self, makeRequest("POST", "/auth/login", json: [
            "email": email, "password": password, "device": "iOS",
        ], auth: false))
    }

    func logout() async throws {
        _ = try await perform(makeRequest("POST", "/auth/logout"))
    }

    func me() async throws -> Account {
        try await send(Account.self, makeRequest("GET", "/me"))
    }

    func updateProfile(name: String? = nil, responseStyle: String? = nil, language: String? = nil) async throws -> Account {
        var body: [String: Any] = [:]
        if let name { body["name"] = name }
        var prefs: [String: Any] = [:]
        if let responseStyle { prefs["responseStyle"] = responseStyle }
        if let language { prefs["language"] = language }
        if !prefs.isEmpty { body["preferences"] = prefs }
        return try await send(Account.self, makeRequest("PATCH", "/me", json: body))
    }

    func changePassword(current: String, new: String) async throws {
        _ = try await perform(makeRequest("POST", "/me/password", json: ["currentPassword": current, "newPassword": new]))
    }

    func deleteAccount() async throws {
        _ = try await perform(makeRequest("DELETE", "/me"))
    }

    // MARK: Chats

    func chats(query: String = "") async throws -> [Chat] {
        var path = "/chats"
        if !query.isEmpty, let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            path += "?q=\(q)"
        }
        return try await send(ChatsResponse.self, makeRequest("GET", path)).chats
    }

    func createChat(mode: String) async throws -> Chat {
        try await send(ChatResponse.self, makeRequest("POST", "/chats", json: ["mode": mode])).chat
    }

    func renameChat(id: String, title: String) async throws -> Chat {
        try await send(ChatResponse.self, makeRequest("PATCH", "/chats/\(id)", json: ["title": title])).chat
    }

    func deleteChat(id: String) async throws {
        _ = try await perform(makeRequest("DELETE", "/chats/\(id)"))
    }

    func deleteAllChats() async throws {
        _ = try await perform(makeRequest("DELETE", "/chats"))
    }

    func messages(chatId: String) async throws -> MessagesResponse {
        try await send(MessagesResponse.self, makeRequest("GET", "/chats/\(chatId)/messages"))
    }

    /// Streams the assistant reply. Cancelling the consuming task stops generation on the server.
    /// Pass `text: nil` to regenerate the answer to the last message.
    func streamReply(chatId: String, text: String?, attachmentIds: [String]) -> AsyncThrowingStream<StreamEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    var request: URLRequest
                    if let text {
                        request = try makeRequest("POST", "/chats/\(chatId)/messages", json: ["text": text, "attachmentIds": attachmentIds])
                    } else {
                        request = try makeRequest("POST", "/chats/\(chatId)/regenerate")
                    }
                    request.setValue("text/event-stream", forHTTPHeaderField: "Accept")

                    let bytes: URLSession.AsyncBytes
                    let response: URLResponse
                    do {
                        (bytes, response) = try await streamHTTP.bytes(for: request)
                    } catch {
                        if Task.isCancelled { throw CancellationError() }
                        throw APIError.network
                    }
                    let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                    if !(200..<300).contains(status) {
                        var data = Data()
                        for try await byte in bytes { data.append(byte) }
                        throw errorFrom(status: status, data: data)
                    }
                    // Each server event is "event: name" followed by a single "data: {...}" line.
                    var event = ""
                    do {
                        for try await line in bytes.lines {
                            if line.hasPrefix("event:") {
                                event = line.dropFirst(6).trimmingCharacters(in: .whitespaces)
                            } else if line.hasPrefix("data:") {
                                let payload = Data(line.dropFirst(5).trimmingCharacters(in: .whitespaces).utf8)
                                if let parsed = parse(event: event, data: payload) { continuation.yield(parsed) }
                                event = ""
                            }
                        }
                    } catch {
                        if Task.isCancelled { throw CancellationError() }
                        throw APIError(code: "network", message: "Соединение прервалось во время ответа. Нажмите «Повторить».")
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private func parse(event: String, data: Data) -> StreamEvent? {
        switch event {
        case "user": return (try? decoder.decode(Message.self, from: data)).map { .userSaved($0) }
        case "assistant": return (try? decoder.decode(StreamStart.self, from: data)).map { .assistantStarted($0.id) }
        case "delta": return (try? decoder.decode(StreamDelta.self, from: data)).map { .delta($0.text) }
        case "error": return (try? decoder.decode(StreamError.self, from: data)).map { .failed(code: $0.code, message: $0.message) }
        case "done": return (try? decoder.decode(StreamDone.self, from: data)).map { .done($0.message, $0.chat) }
        default: return nil
        }
    }

    // MARK: Files

    func upload(data: Data, mime: String, name: String) async throws -> Attachment {
        var request = try makeRequest("POST", "/uploads")
        request.setValue(mime, forHTTPHeaderField: "Content-Type")
        request.setValue(name.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "file", forHTTPHeaderField: "X-File-Name")
        let responseData: Data
        let response: URLResponse
        do {
            (responseData, response) = try await http.upload(for: request, from: data)
        } catch {
            throw APIError.network
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw errorFrom(status: status, data: responseData) }
        guard let file = try? decoder.decode(FileResponse.self, from: responseData).file else {
            throw APIError(code: "bad_response", message: "Сервер вернул неожиданный ответ")
        }
        return file
    }

    func download(_ path: String) async throws -> Data {
        guard let url = absoluteURL(path) else { throw APIError.network }
        do {
            let (data, response) = try await http.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                throw APIError(code: "download_failed", message: "Не удалось скачать файл")
            }
            return data
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.network
        }
    }

    // MARK: Images

    func images() async throws -> [GeneratedImage] {
        try await send(ImagesResponse.self, makeRequest("GET", "/images")).images
    }

    func generateImage(prompt: String, style: String, size: String) async throws -> GeneratedImage {
        try await send(ImageResponse.self, makeRequest("POST", "/images", json: ["prompt": prompt, "style": style, "size": size])).image
    }

    func deleteImage(id: String) async throws {
        _ = try await perform(makeRequest("DELETE", "/images/\(id)"))
    }

    // MARK: Subscription

    func verifyApplePurchase(signedTransaction: String) async throws -> Account {
        try await send(Account.self, makeRequest("POST", "/subscription/verify", json: [
            "platform": "apple", "signedTransaction": signedTransaction,
        ]))
    }
}
