import Foundation
import StoreKit

enum BillingStatus: Equatable {
    case idle
    case working
    case success(String)
    case failed(String)
}

/// StoreKit 2 for the Aura Pro subscription. Every transaction is confirmed by the Aura server.
@MainActor
final class StoreManager: ObservableObject {
    static let productIDs = ["aura_pro_monthly", "aura_pro_yearly"]

    @Published private(set) var products: [Product] = []
    @Published private(set) var loaded = false
    @Published var status: BillingStatus = .idle

    weak var store: AuraStore?
    private var updates: Task<Void, Never>?

    init() {
        // Renewals and purchases made on another device arrive here.
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                await self?.handle(result, silent: true)
            }
        }
    }

    deinit { updates?.cancel() }

    func loadProducts() async {
        do {
            let list = try await Product.products(for: Self.productIDs)
            products = list.sorted { $0.price < $1.price }
        } catch {
            products = []
        }
        loaded = true
    }

    func buy(_ product: Product) async {
        status = .working
        do {
            var options: Set<Product.PurchaseOption> = []
            if let id = store?.account?.user.id, let uuid = UUID(uuidString: id) {
                options.insert(.appAccountToken(uuid))
            }
            let result = try await product.purchase(options: options)
            switch result {
            case .success(let verification):
                await handle(verification, silent: false)
            case .userCancelled:
                status = .idle
            case .pending:
                status = .success("Покупка ожидает подтверждения. Pro включится автоматически.")
            @unknown default:
                status = .idle
            }
        } catch {
            status = .failed("Не удалось оформить подписку: \(error.localizedDescription)")
        }
    }

    /// Re-checks active subscriptions with the server (runs on launch and from "Restore").
    func restore(silent: Bool) async {
        if !silent {
            status = .working
            try? await AppStore.sync()
        }
        var found = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let tx) = result, Self.productIDs.contains(tx.productID) {
                found = true
                await handle(result, silent: silent)
            }
        }
        if !found && !silent { status = .failed("Активных подписок не найдено") }
    }

    private func handle(_ result: VerificationResult<Transaction>, silent: Bool) async {
        guard case .verified(let transaction) = result else {
            if !silent { status = .failed("Покупка не прошла проверку App Store") }
            return
        }
        guard let store else { return }
        do {
            let account = try await store.api.verifyApplePurchase(signedTransaction: result.jwsRepresentation)
            store.applyAccount(account)
            await transaction.finish()
            if !silent { status = .success("Aura Pro активирован") }
        } catch let error as APIError {
            if !silent { status = .failed(error.message) }
        } catch {
            if !silent { status = .failed(error.localizedDescription) }
        }
    }
}
