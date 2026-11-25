import SwiftUI

enum Route: Hashable {
    case chat(id: String?, mode: String, draft: String)
    case images
    case profile
    case pro
}

enum AuraTab: String, CaseIterable {
    case home, history, tools, settings

    var title: String {
        switch self {
        case .home: return "Главная"
        case .history: return "История"
        case .tools: return "Инструменты"
        case .settings: return "Настройки"
        }
    }

    var icon: String {
        switch self {
        case .home: return "house.fill"
        case .history: return "clock"
        case .tools: return "square.grid.2x2"
        case .settings: return "gearshape"
        }
    }
}

/// Navigation state shared by all screens.
@MainActor
final class Router: ObservableObject {
    @Published var path = NavigationPath()
    @Published var tab: AuraTab = .home

    func open(_ route: Route) { path.append(route) }
    func back() { if !path.isEmpty { path.removeLast() } }
    func newChat(mode: String = ChatMode.chat, draft: String = "") { open(.chat(id: nil, mode: mode, draft: draft)) }
}

struct RootView: View {
    @EnvironmentObject private var store: AuraStore

    var body: some View {
        ZStack {
            AuraBackground()
            if store.loggedIn {
                MainView().transition(.opacity)
            } else {
                AuthView().transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.35), value: store.loggedIn)
    }
}

/// Bottom spacing that keeps content above the floating tab bar.
let tabBarSpace: CGFloat = 96

struct MainView: View {
    @StateObject private var router = Router()
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var purchases: StoreManager

    var body: some View {
        NavigationStack(path: $router.path) {
            ZStack(alignment: .bottom) {
                Group {
                    switch router.tab {
                    case .home: HomeView()
                    case .history: HistoryView()
                    case .tools: ToolsView()
                    case .settings: SettingsView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .transition(.opacity)

                TabBar(selected: $router.tab)
            }
            .background(AuraBackground())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { route in
                Group {
                    switch route {
                    case .chat(let id, let mode, let draft): ChatView(chatId: id, mode: mode, draft: draft)
                    case .images: ImagesView()
                    case .profile: ProfileView()
                    case .pro: ProView()
                    }
                }
                .background(AuraBackground())
                .toolbar(.hidden, for: .navigationBar)
            }
        }
        .environmentObject(router)
        .task {
            purchases.store = store
            await purchases.loadProducts()
            await purchases.restore(silent: true)
        }
    }
}

private struct TabBar: View {
    @Binding var selected: AuraTab
    @EnvironmentObject private var store: AuraStore

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AuraTab.allCases, id: \.self) { tab in
                Button {
                    MediaUtils.haptic(store.session.hapticsEnabled)
                    withAnimation(.easeInOut(duration: 0.2)) { selected = tab }
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.icon).font(.system(size: 21, weight: .semibold))
                        Text(tab.title).font(.system(size: 11, weight: selected == tab ? .semibold : .regular)).lineLimit(1)
                    }
                    .foregroundStyle(selected == tab ? Aura.lilac : Aura.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 6)
        .glass(cornerRadius: 30, strong: true)
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
    }
}
