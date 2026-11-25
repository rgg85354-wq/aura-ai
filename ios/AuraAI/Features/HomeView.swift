import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var router: Router
    @State private var input = ""

    private var user: User? { store.account?.user }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header.padding(.top, 12)
                    HeroCard(name: user?.name.components(separatedBy: " ").first) {
                        router.newChat()
                    }
                    .padding(.top, 22)

                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        FeatureCard(title: "Чат", subtitle: "Общайся с ИИ", icon: "bubble.left.and.bubble.right") { router.newChat(mode: ChatMode.chat) }
                        FeatureCard(title: "Изображения", subtitle: "Создавай картинки", icon: "photo") { router.open(.images) }
                        FeatureCard(title: "Код", subtitle: "Пиши и разбирай", icon: "chevron.left.forwardslash.chevron.right") { router.newChat(mode: ChatMode.code) }
                        FeatureCard(title: "Идеи", subtitle: "Получай идеи", icon: "lightbulb") { router.newChat(mode: ChatMode.ideas) }
                    }
                    .padding(.top, 16)

                    SectionTitle(title: "Недавние чаты") {
                        Button {
                            withAnimation { router.tab = .history }
                        } label: {
                            HStack(spacing: 2) {
                                Text("Все")
                                Image(systemName: "chevron.right")
                            }
                            .font(AuraFont.label)
                            .foregroundStyle(Aura.lilac)
                        }
                    }
                    .padding(.top, 26)

                    VStack(spacing: 10) {
                        if store.chats.isEmpty {
                            Text("Здесь появятся ваши разговоры")
                                .font(AuraFont.bodySmall)
                                .foregroundStyle(Aura.textSecondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(16)
                                .glass()
                        } else {
                            ForEach(store.chats.prefix(3)) { chat in
                                ChatRow(chat: chat) { router.open(.chat(id: chat.id, mode: chat.mode, draft: "")) }
                            }
                        }
                    }
                    .padding(.top, 12)
                    Spacer().frame(height: tabBarSpace + 90)
                }
                .padding(.horizontal, 20)
            }
            .scrollDismissesKeyboard(.interactively)
            .refreshable { try? await store.sync() }

            composer.padding(.bottom, tabBarSpace - 12)
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            AuraLogoTile(size: 68)
            VStack(alignment: .leading, spacing: 2) {
                Text("Aura").font(AuraFont.display).foregroundStyle(Aura.textPrimary)
                Text("Твой ИИ-ассистент").font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary)
            }
            Spacer()
            ProChip(isPro: user?.isPro == true) { router.open(.pro) }
            Button { router.open(.profile) } label: {
                AvatarView(name: user?.name ?? "A", size: 46, pro: user?.isPro == true)
            }
            .buttonStyle(PressableStyle())
        }
    }

    private var composer: some View {
        HStack(spacing: 10) {
            GlassIconButton(icon: "paperclip") {
                store.pendingDraft = Draft(text: input, autoSend: false)
                input = ""
                router.newChat(mode: ChatMode.chat, draft: "attach")
            }
            TextField("", text: $input, prompt: Text("Написать сообщение…").foregroundColor(Aura.textTertiary))
                .font(AuraFont.body)
                .foregroundStyle(Aura.textPrimary)
                .submitLabel(.send)
                .onSubmit(send)
            GlassIconButton(icon: "paperplane.fill", filled: !input.trimmingCharacters(in: .whitespaces).isEmpty,
                            enabled: !input.trimmingCharacters(in: .whitespaces).isEmpty, action: send)
        }
        .padding(6)
        .padding(.leading, 0)
        .glass(cornerRadius: 28, strong: true)
        .padding(.horizontal, 16)
    }

    private func send() {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        MediaUtils.haptic(store.session.hapticsEnabled)
        store.pendingDraft = Draft(text: text)
        input = ""
        router.newChat(mode: ChatMode.chat)
    }
}

private struct HeroCard: View {
    let name: String?
    let action: () -> Void
    @State private var float = false

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: Aura.radiusL, style: .continuous).fill(Aura.heroGradient)
                Glow(color: Aura.violet, size: 260).offset(x: 140)
                HStack {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(name.map { "Привет, \($0)!" } ?? "Привет!")
                            .font(AuraFont.headline)
                            .foregroundStyle(Aura.textPrimary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Text("Чем я могу помочь тебе сегодня?")
                            .font(AuraFont.body)
                            .foregroundStyle(Aura.violetLight)
                            .multilineTextAlignment(.leading)
                        Image(systemName: "arrow.right")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 46, height: 46)
                            .background(Circle().fill(Aura.primaryGradient))
                            .padding(.top, 10)
                    }
                    Spacer(minLength: 12)
                    AuraSpark(size: 58)
                        .frame(width: 112, height: 112)
                        .glass(cornerRadius: 34, strong: true)
                        .rotationEffect(.degrees(float ? -4 : -10))
                        .offset(y: float ? -8 : 4)
                }
                .padding(22)
            }
            .frame(height: 190)
            .clipShape(RoundedRectangle(cornerRadius: Aura.radiusL, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Aura.radiusL, style: .continuous).strokeBorder(Aura.glassStroke, lineWidth: 1))
        }
        .buttonStyle(PressableStyle())
        .onAppear {
            withAnimation(.easeInOut(duration: 3.2).repeatForever(autoreverses: true)) { float = true }
        }
    }
}

private struct FeatureCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                IconTile(icon: icon)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(AuraFont.label).foregroundStyle(Aura.textPrimary).lineLimit(1)
                    Text(subtitle).font(AuraFont.caption).foregroundStyle(Aura.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(height: 86)
            .glass()
        }
        .buttonStyle(PressableStyle())
    }
}

struct ChatRow: View {
    let chat: Chat
    var showPreview = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: ChatMode.icon(chat.mode))
                    .font(.system(size: 18))
                    .foregroundStyle(Aura.lilac)
                    .frame(width: 44, height: 44)
                    .glass(cornerRadius: 14)
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(chat.title).font(AuraFont.titleMedium).foregroundStyle(Aura.textPrimary).lineLimit(1)
                        Spacer(minLength: 8)
                        if showPreview {
                            Text(Format.relative(chat.updatedAt)).font(AuraFont.caption).foregroundStyle(Aura.textSecondary)
                        }
                    }
                    Text(showPreview && !chat.preview.isEmpty ? chat.preview.replacingOccurrences(of: "\n", with: " ") : Format.relative(chat.updatedAt))
                        .font(showPreview ? AuraFont.bodySmall : AuraFont.caption)
                        .foregroundStyle(Aura.textSecondary)
                        .lineLimit(1)
                }
                if !showPreview {
                    Image(systemName: "chevron.right").foregroundStyle(Aura.textTertiary)
                }
            }
            .padding(14)
            .glass()
        }
        .buttonStyle(PressableStyle())
    }
}
