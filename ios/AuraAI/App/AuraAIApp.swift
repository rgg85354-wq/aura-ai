import SwiftUI

@main
struct AuraAIApp: App {
    @StateObject private var store = AuraStore()
    @StateObject private var purchases = StoreManager()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(purchases)
                .preferredColorScheme(.dark)
                .tint(Aura.lilac)
                .onAppear { purchases.store = store }
        }
        .onChange(of: scenePhase) { _, phase in
            // Sync history and subscription every time the app comes to the foreground.
            guard phase == .active, store.loggedIn else { return }
            Task {
                try? await store.sync()
                await purchases.restore(silent: true)
            }
        }
    }
}
