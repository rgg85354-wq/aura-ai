import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var router: Router
    @State private var editName = false
    @State private var newName = ""
    @State private var changePassword = false
    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var deleteAccount = false
    @State private var error: String?
    @State private var info: String?

    private var user: User? { store.account?.user }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Профиль", onBack: { router.back() })
            ScrollView {
                VStack(spacing: 0) {
                    ZStack {
                        Glow(size: 200)
                        AvatarView(name: user?.name ?? "A", size: 104, pro: user?.isPro == true)
                    }
                    Text(user?.name ?? "").font(AuraFont.headline).foregroundStyle(Aura.textPrimary)
                    Text(user?.email ?? "").font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary)
                    Text(user?.isPro == true ? "Aura Pro" : "Бесплатный план")
                        .font(AuraFont.label)
                        .foregroundStyle(user?.isPro == true ? Color.white : Aura.textSecondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background { if user?.isPro == true { Capsule().fill(Aura.primaryGradient) } }
                        .modifier(ProfileChipGlass(active: user?.isPro == true))
                        .padding(.top, 10)

                    if let error { ErrorBanner(message: error, action: "OK") { self.error = nil }.padding(.top, 14) }
                    if let info { Text(info).font(AuraFont.label).foregroundStyle(Aura.success).padding(.top, 14) }

                    if let acc = store.account {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Сегодня").font(AuraFont.titleMedium).foregroundStyle(Aura.textPrimary)
                            UsageBar(label: "Сообщения", used: acc.usage.messagesToday, limit: acc.limits.dailyMessages)
                            UsageBar(label: "Изображения", used: acc.usage.imagesToday, limit: acc.limits.dailyImages)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .glass()
                        .padding(.top, 20)
                    }

                    SettingsGroup(title: "Аккаунт") {
                        Button { newName = user?.name ?? ""; editName = true } label: {
                            SettingsRow(icon: "pencil", title: "Имя", value: user?.name)
                        }
                        SettingsRow(icon: "envelope", title: "Email", value: user?.email)
                        SettingsRow(icon: "calendar", title: "С нами с", value: user.map { Format.date($0.createdAt) })
                        Button { currentPassword = ""; newPassword = ""; changePassword = true } label: {
                            SettingsRow(icon: "lock", title: "Сменить пароль")
                        }
                    }
                    SettingsGroup(title: "Подписка") {
                        Button { router.open(.pro) } label: {
                            SettingsRow(icon: "crown", title: "Aura Pro",
                                        value: user?.isPro == true ? "до \(user?.proUntil.map { Format.date($0) } ?? "—")" : "Не оформлена")
                        }
                    }
                    SettingsGroup(title: "") {
                        Button { Task { await store.logout() } } label: {
                            SettingsRow(icon: "rectangle.portrait.and.arrow.right", title: "Выйти", destructive: true)
                        }
                        Button { deleteAccount = true } label: {
                            SettingsRow(icon: "person.crop.circle.badge.xmark", title: "Удалить аккаунт", destructive: true)
                        }
                    }
                    Spacer().frame(height: 32)
                }
                .padding(.horizontal, 20)
            }
        }
        .alert("Ваше имя", isPresented: $editName) {
            TextField("Имя", text: $newName)
            Button("Сохранить") {
                let name = newName.trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return }
                Task { do { try await store.updateProfile(name: name) } catch { self.error = error.localizedDescription } }
            }
            Button("Отмена", role: .cancel) {}
        }
        .alert("Смена пароля", isPresented: $changePassword) {
            SecureField("Текущий пароль", text: $currentPassword)
            SecureField("Новый пароль (от 8 символов)", text: $newPassword)
            Button("Сменить") {
                guard newPassword.count >= 8 else { error = "Новый пароль должен быть не короче 8 символов"; return }
                Task {
                    do {
                        try await store.changePassword(current: currentPassword, new: newPassword)
                        info = "Пароль изменён. Другие устройства вышли из аккаунта."
                    } catch {
                        self.error = error.localizedDescription
                    }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .confirmationDialog("Удалить аккаунт?", isPresented: $deleteAccount, titleVisibility: .visible) {
            Button("Удалить навсегда", role: .destructive) {
                Task { do { try await store.deleteAccount() } catch { self.error = error.localizedDescription } }
            }
        } message: {
            Text("Все чаты, файлы и изображения будут удалены навсегда. Подписку в App Store нужно отменить отдельно.")
        }
    }
}

private struct ProfileChipGlass: ViewModifier {
    let active: Bool
    @ViewBuilder func body(content: Content) -> some View {
        if active { content.clipShape(Capsule()) } else { content.glassCapsule() }
    }
}

private struct UsageBar: View {
    let label: String
    let used: Int
    let limit: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(label).font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary)
                Spacer()
                Text("\(used) / \(limit)").font(AuraFont.label).foregroundStyle(Aura.textPrimary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.08))
                    Capsule().fill(Aura.primaryGradient)
                        .frame(width: geo.size.width * (limit > 0 ? min(1, CGFloat(used) / CGFloat(limit)) : 0))
                }
            }
            .frame(height: 8)
        }
    }
}
