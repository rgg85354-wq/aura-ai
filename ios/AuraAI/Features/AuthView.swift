import SwiftUI

struct AuthView: View {
    @EnvironmentObject private var store: AuraStore
    @State private var register = false
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var loading = false
    @State private var error: String?
    @State private var showServer = false
    @State private var float = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer().frame(height: 48)
                ZStack {
                    Glow(size: 190)
                    AuraSpark(size: 56)
                        .frame(width: 104, height: 104)
                        .glass(cornerRadius: 32, strong: true)
                }
                .offset(y: float ? -6 : 4)
                .animation(.easeInOut(duration: 2.8).repeatForever(autoreverses: true), value: float)
                .onAppear { float = true }

                Text("Aura").font(AuraFont.display).foregroundStyle(Aura.textPrimary).padding(.top, 20)
                Text(register
                     ? "Создайте аккаунт, чтобы история чатов синхронизировалась между устройствами"
                     : "Войдите, чтобы продолжить общение с вашим ИИ-ассистентом")
                    .font(AuraFont.bodySmall)
                    .foregroundStyle(Aura.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 6)

                segmented.padding(.top, 28)

                VStack(spacing: 12) {
                    if register {
                        AuraTextField(placeholder: "Имя", text: $name, icon: "person")
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    AuraTextField(placeholder: "Email", text: $email, icon: "envelope", keyboard: .emailAddress)
                    AuraTextField(placeholder: "Пароль", text: $password, icon: "lock", secure: true)
                }
                .padding(.top, 20)
                .animation(.spring(duration: 0.35), value: register)

                if let error {
                    ErrorBanner(message: error).padding(.top, 14).transition(.opacity)
                }

                GradientButton(title: register ? "Создать аккаунт" : "Войти", loading: loading, action: submit)
                    .padding(.top, 20)

                Button { showServer = true } label: {
                    Label("Сервер: \(store.session.serverURL)", systemImage: "server.rack")
                        .font(AuraFont.caption)
                        .foregroundStyle(Aura.textTertiary)
                }
                .padding(.top, 24)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .sheet(isPresented: $showServer) { ServerSheet() }
    }

    private var segmented: some View {
        HStack(spacing: 0) {
            ForEach([false, true], id: \.self) { isRegister in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { register = isRegister; error = nil }
                } label: {
                    Text(isRegister ? "Регистрация" : "Вход")
                        .font(AuraFont.label)
                        .foregroundStyle(register == isRegister ? Color.white : Aura.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background {
                            if register == isRegister {
                                RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Aura.violet.opacity(0.85))
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .glass(cornerRadius: 18)
    }

    private func submit() {
        error = nil
        if register && name.trimmingCharacters(in: .whitespaces).isEmpty { error = "Введите имя"; return }
        if !email.contains("@") { error = "Введите корректный email"; return }
        if password.count < 8 { error = "Пароль должен быть не короче 8 символов"; return }
        loading = true
        Task {
            defer { loading = false }
            do {
                if register {
                    try await store.register(name: name, email: email, password: password)
                } else {
                    try await store.login(email: email, password: password)
                }
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

/// Lets the user point the app at their Aura server and checks that it answers.
struct ServerSheet: View {
    @EnvironmentObject private var store: AuraStore
    @Environment(\.dismiss) private var dismiss
    @State private var url = ""
    @State private var checking = false
    @State private var status: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Адрес сервера Aura").font(AuraFont.title).foregroundStyle(Aura.textPrimary)
            Text("Например http://192.168.1.20:8787 для сервера на вашем компьютере или https://aura.example.com")
                .font(AuraFont.bodySmall)
                .foregroundStyle(Aura.textSecondary)
            AuraTextField(placeholder: "http://…", text: $url, icon: "server.rack", keyboard: .URL)
            if let status {
                Text(status).font(AuraFont.caption).foregroundStyle(status.hasPrefix("✓") ? Aura.success : Aura.danger)
            }
            GradientButton(title: checking ? "Проверяю…" : "Сохранить", enabled: url.hasPrefix("http"), loading: checking) {
                checking = true
                status = nil
                Task {
                    let ok = await store.api.checkServer(url)
                    checking = false
                    if ok {
                        var clean = url.trimmingCharacters(in: .whitespaces)
                        while clean.hasSuffix("/") { clean.removeLast() }
                        let changed = clean != store.session.serverURL
                        store.session.serverURL = clean
                        if changed && store.loggedIn { store.signOutLocally() }
                        dismiss()
                    } else {
                        status = "Сервер не отвечает. Проверьте адрес и что телефон в той же сети."
                    }
                }
            }
            Spacer()
        }
        .padding(24)
        .background(Aura.graphite.ignoresSafeArea())
        .presentationDetents([.medium])
        .onAppear { url = store.session.serverURL }
    }
}
