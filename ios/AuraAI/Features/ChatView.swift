import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

private let suggestions: [String: [String]] = [
    ChatMode.chat: [
        "Объясни простыми словами, как работает нейросеть",
        "Составь план тренировок на неделю",
        "Помоги написать вежливое письмо преподавателю",
    ],
    ChatMode.code: [
        "Напиши функцию на Swift, которая проверяет палиндром",
        "Объясни, что такое async/await, с примером",
        "Как сделать REST API на Python за 10 минут?",
    ],
    ChatMode.ideas: [
        "Идеи для мобильного приложения",
        "Чем заняться на выходных с друзьями",
        "Идеи подарка на день рождения",
    ],
]

struct ChatView: View {
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var router: Router
    @StateObject private var vm: ChatViewModel
    private let draft: String

    @State private var showPhotos = false
    @State private var showFiles = false
    @State private var photoItem: PhotosPickerItem?
    @State private var renaming = false
    @State private var newTitle = ""
    @State private var deleting = false
    @FocusState private var focused: Bool

    init(chatId: String?, mode: String, draft: String) {
        _vm = StateObject(wrappedValue: ChatViewModel(chatId: chatId, mode: mode, initialText: draft == "attach" ? "" : draft))
        self.draft = draft
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: vm.chat?.title ?? "Новый чат · \(ChatMode.title(vm.currentMode))", onBack: { router.back() }) {
                if vm.chat != nil {
                    Menu {
                        Button { newTitle = vm.chat?.title ?? ""; renaming = true } label: { Label("Переименовать", systemImage: "pencil") }
                        Button(role: .destructive) { deleting = true } label: { Label("Удалить чат", systemImage: "trash") }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Aura.textPrimary)
                            .frame(width: 44, height: 44)
                            .glassCircle()
                    }
                }
            }

            ZStack {
                if vm.loading {
                    ProgressView().tint(Aura.lilac)
                } else if vm.messages.isEmpty {
                    EmptyChat(mode: vm.currentMode) { text in
                        MediaUtils.haptic(store.session.hapticsEnabled)
                        vm.send(text)
                    }
                } else {
                    messageList
                }
            }
            .frame(maxHeight: .infinity)

            if vm.limitReached {
                LimitBanner { router.open(.pro) }.padding(.horizontal, 16).padding(.vertical, 6)
            }
            if let error = vm.error {
                ErrorBanner(message: error, action: "Закрыть") { vm.error = nil }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
            }
            composer
        }
        .onAppear {
            vm.attach(to: store)
            if draft == "attach" { showFiles = true }
        }
        .photosPicker(isPresented: $showPhotos, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            photoItem = nil
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                vm.addFile(data.flatMap { MediaUtils.prepareImage($0) })
            }
        }
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.pdf, .plainText, .sourceCode, .json, .commaSeparatedText, .image, .text, .data]) { result in
            switch result {
            case .success(let url):
                do { vm.addFile(try MediaUtils.readDocument(url)) } catch { vm.error = error.localizedDescription }
            case .failure(let error):
                vm.error = error.localizedDescription
            }
        }
        .alert("Название чата", isPresented: $renaming) {
            TextField("Название", text: $newTitle)
            Button("Сохранить") { if !newTitle.trimmingCharacters(in: .whitespaces).isEmpty { vm.rename(newTitle) } }
            Button("Отмена", role: .cancel) {}
        }
        .confirmationDialog("Удалить чат?", isPresented: $deleting, titleVisibility: .visible) {
            Button("Удалить", role: .destructive) { vm.delete { router.back() } }
        } message: {
            Text("Чат удалится на всех ваших устройствах. Это действие нельзя отменить.")
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 14) {
                    ForEach(vm.messages) { message in
                        let isLast = message.id == vm.messages.last?.id
                        MessageRow(
                            message: message,
                            isLast: isLast,
                            streaming: vm.streaming && isLast,
                            onCopy: {
                                UIPasteboard.general.string = message.content
                                MediaUtils.haptic(store.session.hapticsEnabled)
                            },
                            onRetry: {
                                MediaUtils.haptic(store.session.hapticsEnabled)
                                vm.retry()
                            }
                        )
                        .id(message.id)
                    }
                    Color.clear.frame(height: 1).id("bottom")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear { proxy.scrollTo("bottom", anchor: .bottom) }
            .onChange(of: vm.messages.count) { _, _ in
                withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo("bottom", anchor: .bottom) }
            }
            .onChange(of: vm.messages.last?.content.count ?? 0) { _, _ in
                if vm.streaming { proxy.scrollTo("bottom", anchor: .bottom) }
            }
        }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !vm.pending.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(vm.pending) { file in
                            PendingChip(file: file) { vm.removePending(file.id) }
                        }
                    }
                    .padding(.horizontal, 6)
                    .padding(.top, 4)
                }
            }
            HStack(alignment: .bottom, spacing: 10) {
                Menu {
                    Button { showPhotos = true } label: { Label("Фото", systemImage: "photo") }
                    Button { showFiles = true } label: { Label("Файл (PDF, текст, код)", systemImage: "doc") }
                } label: {
                    Image(systemName: "paperclip")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Aura.textPrimary)
                        .frame(width: 44, height: 44)
                        .glassCircle()
                }
                TextField("", text: $vm.input, prompt: Text(placeholder).foregroundColor(Aura.textTertiary), axis: .vertical)
                    .lineLimit(1...6)
                    .font(AuraFont.body)
                    .foregroundStyle(Aura.textPrimary)
                    .tint(Aura.lilac)
                    .focused($focused)
                    .padding(.vertical, 11)
                if vm.streaming {
                    GlassIconButton(icon: "stop.fill", filled: true) {
                        MediaUtils.haptic(store.session.hapticsEnabled)
                        vm.stop()
                    }
                } else {
                    GlassIconButton(icon: "arrow.up", filled: vm.canSend, enabled: vm.canSend) {
                        MediaUtils.haptic(store.session.hapticsEnabled)
                        vm.send()
                    }
                }
            }
        }
        .padding(6)
        .glass(cornerRadius: 26, strong: true)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .animation(.easeInOut(duration: 0.2), value: vm.pending.count)
    }

    private var placeholder: String {
        switch vm.currentMode {
        case ChatMode.code: return "Опишите задачу или вставьте код…"
        case ChatMode.ideas: return "Для чего нужны идеи?"
        default: return "Спросите что-нибудь…"
        }
    }
}

private struct EmptyChat: View {
    let mode: String
    let onPick: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                ZStack {
                    Glow(size: 170)
                    AuraSpark(size: 46).frame(width: 88, height: 88).glass(cornerRadius: 28, strong: true)
                }
                .padding(.top, 40)
                Text(mode == ChatMode.code ? "Напишу, объясню и исправлю код" : mode == ChatMode.ideas ? "Придумаем что-нибудь классное" : "Чем могу помочь?")
                    .font(AuraFont.title)
                    .foregroundStyle(Aura.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 10)
                ForEach(suggestions[mode] ?? [], id: \.self) { text in
                    Button { onPick(text) } label: {
                        Text(text)
                            .font(AuraFont.bodySmall)
                            .foregroundStyle(Aura.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 13)
                            .glass(cornerRadius: 16)
                    }
                    .buttonStyle(PressableStyle())
                }
            }
            .padding(24)
        }
    }
}

private struct MessageRow: View {
    @EnvironmentObject private var store: AuraStore
    let message: Message
    let isLast: Bool
    let streaming: Bool
    let onCopy: () -> Void
    let onRetry: () -> Void

    var body: some View {
        if message.isUser { userBubble } else { assistantBubble }
    }

    private var userBubble: some View {
        VStack(alignment: .trailing, spacing: 6) {
            if !message.attachments.isEmpty {
                HStack(spacing: 6) {
                    ForEach(message.attachments) { att in
                        if att.isImage {
                            AsyncImage(url: store.url(att.url)) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                Aura.graphiteLight
                            }
                            .frame(width: 96, height: 96)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Aura.glassStroke))
                        } else {
                            FileChip(name: att.name, subtitle: Format.size(att.size))
                        }
                    }
                }
            }
            if !message.content.isEmpty {
                Text(message.content)
                    .font(AuraFont.body)
                    .foregroundStyle(Aura.textPrimary)
                    .textSelection(.enabled)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .background(
                        UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 22, bottomTrailingRadius: 6, topTrailingRadius: 22, style: .continuous)
                            .fill(LinearGradient(colors: [Aura.violet.opacity(0.85), Aura.violetDeep.opacity(0.9)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    )
                    .overlay(
                        UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 22, bottomTrailingRadius: 6, topTrailingRadius: 22, style: .continuous)
                            .strokeBorder(Color.white.opacity(0.18))
                    )
                    .frame(maxWidth: 310, alignment: .trailing)
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    private var note: String? {
        switch message.status {
        case MessageStatus.stopped: return "Генерация остановлена"
        case MessageStatus.error: return "Ответ не получен"
        case MessageStatus.refused: return "Aura не может ответить на этот запрос"
        case MessageStatus.truncated: return "Ответ получился слишком длинным и был обрезан"
        default: return nil
        }
    }

    private var assistantBubble: some View {
        HStack(alignment: .top, spacing: 10) {
            AuraSpark(size: 16).frame(width: 32, height: 32).glassCircle()
            VStack(alignment: .leading, spacing: 6) {
                VStack(alignment: .leading, spacing: 8) {
                    if message.content.isEmpty && message.status == MessageStatus.streaming {
                        HStack(spacing: 10) {
                            TypingDots()
                            Text("Aura думает…").font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary)
                        }
                    } else if !message.content.isEmpty {
                        MarkdownView(text: message.content)
                    }
                    if let note {
                        Text(note).font(AuraFont.caption).foregroundStyle(message.status == MessageStatus.error ? Aura.danger : Aura.textTertiary)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .modifier(GlassBackground(shape: UnevenRoundedRectangle(topLeadingRadius: 6, bottomLeadingRadius: 22, bottomTrailingRadius: 22, topTrailingRadius: 22, style: .continuous)))

                if !streaming && message.status != MessageStatus.streaming {
                    HStack(spacing: 4) {
                        if !message.content.isEmpty { SmallAction(icon: "doc.on.doc", title: "Копировать", action: onCopy) }
                        if isLast { SmallAction(icon: "arrow.clockwise", title: "Повторить", action: onRetry) }
                    }
                }
            }
        }
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }
}

private struct SmallAction: View {
    let icon: String
    let title: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(AuraFont.caption)
                .foregroundStyle(Aura.textSecondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
        }
        .buttonStyle(.plain)
    }
}

private struct FileChip: View {
    let name: String
    let subtitle: String
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "doc.text").foregroundStyle(Aura.lilac)
            VStack(alignment: .leading, spacing: 1) {
                Text(name).font(AuraFont.label).foregroundStyle(Aura.textPrimary).lineLimit(1)
                if !subtitle.isEmpty { Text(subtitle).font(AuraFont.caption).foregroundStyle(Aura.textSecondary).lineLimit(1) }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: 200, alignment: .leading)
        .glass(cornerRadius: 14)
    }
}

private struct PendingChip: View {
    let file: PendingFile
    let onRemove: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Group {
                if let data = file.preview, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                } else {
                    FileChip(name: file.name, subtitle: file.error ?? (file.size > 0 ? Format.size(file.size) : ""))
                }
            }
            .overlay {
                if file.uploading {
                    ZStack {
                        RoundedRectangle(cornerRadius: 14).fill(Color.black.opacity(0.35))
                        ProgressView().tint(.white)
                    }
                } else if file.error != nil && file.preview != nil {
                    RoundedRectangle(cornerRadius: 14).strokeBorder(Aura.danger, lineWidth: 2)
                }
            }
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 20, height: 20)
                    .background(Circle().fill(Color.black.opacity(0.7)))
            }
            .padding(2)
        }
    }
}

private struct LimitBanner: View {
    let onUpgrade: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "crown.fill").foregroundStyle(Aura.gold)
            VStack(alignment: .leading, spacing: 2) {
                Text("Дневной лимит исчерпан").font(AuraFont.label).foregroundStyle(Aura.textPrimary)
                Text("С Aura Pro можно общаться почти без ограничений").font(AuraFont.caption).foregroundStyle(Aura.textSecondary)
            }
            Spacer()
            Button("Pro", action: onUpgrade)
                .font(AuraFont.label)
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Capsule().fill(Aura.primaryGradient))
        }
        .padding(14)
        .glass(cornerRadius: 18, strong: true)
    }
}
