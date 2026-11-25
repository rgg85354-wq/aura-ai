import StoreKit
import SwiftUI

struct ProView: View {
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var purchases: StoreManager
    @EnvironmentObject private var router: Router
    @Environment(\.openURL) private var openURL
    @State private var selected: String?
    @State private var float = false

    private var user: User? { store.account?.user }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Aura Pro", onBack: { router.back() })
            ScrollView {
                VStack(spacing: 0) {
                    ZStack {
                        Glow(size: 200)
                        Image(systemName: "crown.fill")
                            .font(.system(size: 50))
                            .foregroundStyle(Aura.gold)
                            .frame(width: 108, height: 108)
                            .glass(cornerRadius: 34, strong: true)
                    }
                    .offset(y: float ? -8 : 0)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 2.6).repeatForever(autoreverses: true)) { float = true }
                    }

                    Text(user?.isPro == true ? "Pro активен" : "Раскройте Aura полностью")
                        .font(AuraFont.headline)
                        .foregroundStyle(Aura.textPrimary)
                        .multilineTextAlignment(.center)
                    if user?.isPro == true {
                        Text("Действует до \(user?.proUntil.map { Format.date($0) } ?? "—")")
                            .font(AuraFont.bodySmall)
                            .foregroundStyle(Aura.textSecondary)
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        Benefit(icon: "infinity", title: "До 1000 сообщений в день", subtitle: "Вместо 30 в бесплатном плане")
                        Benefit(icon: "photo.stack", title: "100 изображений в день", subtitle: "Вместо 5 в бесплатном плане")
                        Benefit(icon: "brain", title: "Более глубокое мышление", subtitle: "ИИ дольше думает над кодом и сложными вопросами")
                        Benefit(icon: "arrow.triangle.2.circlepath", title: "На всех устройствах", subtitle: "Одна подписка для iPhone и Android в вашем аккаунте")
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .glass()
                    .padding(.top, 20)

                    plans.padding(.top, 18)

                    switch purchases.status {
                    case .failed(let message): ErrorBanner(message: message).padding(.top, 12)
                    case .success(let message): Text(message).font(AuraFont.label).foregroundStyle(Aura.success).padding(.top, 12)
                    default: EmptyView()
                    }

                    Button("Восстановить покупки") { Task { await purchases.restore(silent: false) } }
                        .font(AuraFont.label)
                        .foregroundStyle(Aura.lilac)
                        .padding(.top, 14)

                    Text("Подписка продлевается автоматически, пока вы не отмените её в настройках Apple ID. Оплата списывается с аккаунта Apple при подтверждении покупки.")
                        .font(AuraFont.caption)
                        .foregroundStyle(Aura.textTertiary)
                        .multilineTextAlignment(.center)
                        .padding(.vertical, 20)
                }
                .padding(.horizontal, 20)
            }
        }
        .task {
            purchases.status = .idle
            if purchases.products.isEmpty { await purchases.loadProducts() }
            if selected == nil { selected = purchases.products.last?.id }
        }
    }

    @ViewBuilder
    private var plans: some View {
        if user?.isPro == true {
            GradientButton(title: "Управлять подпиской") {
                if let url = URL(string: "https://apps.apple.com/account/subscriptions") { openURL(url) }
            }
        } else if !purchases.products.isEmpty {
            VStack(spacing: 10) {
                ForEach(purchases.products, id: \.id) { product in
                    let active = product.id == selected
                    Button { selected = product.id } label: {
                        HStack(spacing: 14) {
                            ZStack {
                                Circle().strokeBorder(active ? Aura.violet : Aura.textTertiary, lineWidth: 2)
                                if active {
                                    Circle().fill(Aura.violet)
                                    Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                                }
                            }
                            .frame(width: 22, height: 22)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(product.id.contains("year") ? "Годовая" : "Месячная").font(AuraFont.titleMedium).foregroundStyle(Aura.textPrimary)
                                if product.id.contains("year") {
                                    Text("Выгоднее всего").font(AuraFont.caption).foregroundStyle(Aura.lilac)
                                }
                            }
                            Spacer()
                            Text("\(product.displayPrice) \(product.id.contains("year") ? "в год" : "в месяц")")
                                .font(AuraFont.label)
                                .foregroundStyle(Aura.textPrimary)
                        }
                        .padding(16)
                        .background(RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous).fill(active ? Aura.violet.opacity(0.18) : Color.white.opacity(0.04)))
                        .overlay(
                            RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous)
                                .strokeBorder(active ? AnyShapeStyle(Aura.primaryGradient) : AnyShapeStyle(Aura.glassStroke), lineWidth: active ? 2 : 1)
                        )
                    }
                    .buttonStyle(PressableStyle())
                }
                GradientButton(title: "Оформить Aura Pro", icon: "sparkles", loading: purchases.status == .working) {
                    guard let product = purchases.products.first(where: { $0.id == selected }) ?? purchases.products.last else { return }
                    Task { await purchases.buy(product) }
                }
                .padding(.top, 6)
            }
        } else if purchases.loaded {
            ErrorBanner(message: "Подписки не найдены. Создайте aura_pro_monthly и aura_pro_yearly в App Store Connect или подключите файл AuraAI.storekit в схеме Xcode.")
        } else {
            ProgressView().tint(Aura.lilac)
        }
    }
}

private struct Benefit: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Aura.lilac)
                .frame(width: 40, height: 40)
                .glass(cornerRadius: 12)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(AuraFont.label).foregroundStyle(Aura.textPrimary)
                Text(subtitle).font(AuraFont.caption).foregroundStyle(Aura.textSecondary)
            }
        }
    }
}
