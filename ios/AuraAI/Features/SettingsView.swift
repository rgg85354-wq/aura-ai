import SwiftUI

let responseStyles: [(String, String)] = [("concise", "Кратко"), ("balanced", "Баланс"), ("detailed", "Подробно")]
let responseLanguages: [(String, String)] = [
    ("auto", "Как в вопросе"), ("ru", "Русский"), ("en", "English"), ("uk", "Українська"),
    ("kk", "Қазақша"), ("de", "Deutsch"), ("es", "Español"), ("fr", "Français"),
]

struct SettingsView: View {
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var router: Router
    @State private var haptics = true
    @State private var clearing = false
    @State private var loggingOut = false
    @State private var showServer = false
    @State private var error: String?

    private var user: User? { store.account?.user }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Настройки").font(AuraFont.headline).foregroundStyle(Aura.textPrimary).padding(.vertical, 12)

                Button { router.open(.profile) } label: {
                    HStack(spacing: 14) {
                        AvatarView(name: user?.name ?? "A", size: 54, pro: user?.isPro == true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user?.name ?? "—").font(AuraFont.titleMedium).foregroundStyle(Aura.textPrimary)
                            Text(user?.email ?? "").font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary).lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(Aura.textTertiary)
                    }
                    .padding(16)
                    .glass()
                }
                .buttonStyle(PressableStyle())

                Button { router.open(.pro) } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "crown.fill").font(.system(size: 24)).foregroundStyle(Aura.gold)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user?.isPro == true ? "Aura Pro активен" : "Aura Pro").font(AuraFont.titleMedium).foregroundStyle(Aura.textPrimary)
                            Text(user?.isPro == true ? "Спасибо за поддержку!" : "Больше сообщений, изображений и глубины")
                                .font(AuraFont.bodySmall).foregroundStyle(Aura.violetLight)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(Aura.violetLight)
                    }
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous).fill(Aura.heroGradient))
                }
                .buttonStyle(PressableStyle())
                .padding(.top, 12)

                if let error {
                    ErrorBanner(message: error, action: "OK") { self.error = nil }.padding(.top, 12)
                }

                SettingsGroup(title: "Ответы ИИ") {
                    SettingsRow(icon: "slider.horizontal.3", title: "Стиль ответов")
                    HStack(spacing: 0) {
                        ForEach(responseStyles, id: \.0) { id, label in
                            let selected = user?.preferences.responseStyle == id
                            Button { save(style: id) } label: {
                                Text(label)
                                    .font(AuraFont.label)
                                    .foregroundStyle(selected ? Color.white : Aura.textSecondary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background { if selected { RoundedRectangle(cornerRadius: 11).fill(Aura.violet.opacity(0.8)) } }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(3)
                    .glass(cornerRadius: 14)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 14)

                    Menu {
                        ForEach(responseLanguages, id: \.0) { id, label in
                            Button(label) { save(language: id) }
                        }
                    } label: {
                        SettingsRow(icon: "character.bubble", title: "Язык ответов",
                                    value: responseLanguages.first { $0.0 == user?.preferences.language }?.1 ?? "Как в вопросе")
                    }
                }

                SettingsGroup(title: "Приложение") {
                    HStack(spacing: 14) {
                        Image(systemName: "hand.tap").foregroundStyle(Aura.lilac).frame(width: 24)
                        Toggle("Тактильный отклик", isOn: $haptics)
                            .font(AuraFont.body)
                            .foregroundStyle(Aura.textPrimary)
                            .tint(Aura.violet)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .onChange(of: haptics) { _, value in store.session.hapticsEnabled = value }

                    Button { showServer = true } label: {
                        SettingsRow(icon: "server.rack", title: "Сервер",
                                    value: store.session.serverURL.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: ""))
                    }
                }

                SettingsGroup(title: "Данные") {
                    Button {
                        Task { do { try await store.sync() } catch { self.error = error.localizedDescription } }
                    } label: {
                        SettingsRow(icon: "arrow.triangle.2.circlepath", title: "Синхронизация",
                                    value: store.lastSync.map { "Обновлено \(Format.relative(Int64($0.timeIntervalSince1970 * 1000)))" } ?? "Не выполнялась",
                                    loading: store.syncing)
                    }
                    Button { clearing = true } label: {
                        SettingsRow(icon: "trash", title: "Очистить историю", destructive: true)
                    }
                }

                SettingsGroup(title: "О приложении") {
                    SettingsRow(icon: "info.circle", title: "Версия",
                                value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                    Button { loggingOut = true } label: {
                        SettingsRow(icon: "rectangle.portrait.and.arrow.right", title: "Выйти из аккаунта", destructive: true)
                    }
                }
                Spacer().frame(height: tabBarSpace + 24)
            }
            .padding(.horizontal, 20)
        }
        .onAppear { haptics = store.session.hapticsEnabled }
        .sheet(isPresented: $showServer) { ServerSheet() }
        .confirmationDialog("Очистить историю?", isPresented: $clearing, titleVisibility: .visible) {
            Button("Очистить", role: .destructive) {
                Task { do { try await store.clearHistory() } catch { self.error = error.localizedDescription } }
            }
        } message: {
            Text("Все чаты будут удалены на всех ваших устройствах. Это нельзя отменить.")
        }
        .confirmationDialog("Выйти из аккаунта?", isPresented: $loggingOut, titleVisibility: .visible) {
            Button("Выйти", role: .destructive) { Task { await store.logout() } }
        } message: {
            Text("История останется на сервере и вернётся после входа.")
        }
    }

    private func save(style: String? = nil, language: String? = nil) {
        Task {
            do { try await store.updateProfile(responseStyle: style, language: language) } catch { self.error = error.localizedDescription }
        }
    }
}

struct SettingsGroup<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !title.isEmpty {
                Text(title).font(AuraFont.caption).foregroundStyle(Aura.textSecondary).padding(.leading, 4)
            }
            VStack(spacing: 0, content: content).glass()
        }
        .padding(.top, 22)
    }
}

struct SettingsRow: View {
    let icon: String
    let title: String
    var value: String?
    var destructive = false
    var loading = false

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon).foregroundStyle(destructive ? Aura.danger : Aura.lilac).frame(width: 24)
            Text(title).font(AuraFont.body).foregroundStyle(destructive ? Aura.danger : Aura.textPrimary)
            Spacer(minLength: 8)
            if loading {
                ProgressView().tint(Aura.lilac)
            } else if let value {
                Text(value).font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary).lineLimit(1).frame(maxWidth: 180, alignment: .trailing)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }
}
