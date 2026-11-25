import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

/// Aura design tokens. The Android app uses the same values (see docs/design-system.md).
enum Aura {
    static let background = Color(hex: 0x07060B)
    static let graphite = Color(hex: 0x131219)
    static let graphiteLight = Color(hex: 0x1C1A24)
    static let violet = Color(hex: 0x8B5CF6)
    static let violetDeep = Color(hex: 0x6D28D9)
    static let violetLight = Color(hex: 0xC4B5FD)
    static let lilac = Color(hex: 0xA78BFA)
    static let textPrimary = Color(hex: 0xF5F3FF)
    static let textSecondary = Color(hex: 0xA1A1AA)
    static let textTertiary = Color(hex: 0x71717A)
    static let danger = Color(hex: 0xF87171)
    static let success = Color(hex: 0x34D399)
    static let gold = Color(hex: 0xFBBF24)

    static let primaryGradient = LinearGradient(colors: [lilac, violet, violetDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let heroGradient = LinearGradient(
        colors: [Color(hex: 0x6D28D9, alpha: 0.85), Color(hex: 0x3B1E7A, alpha: 0.75), Color(hex: 0x16121F, alpha: 0.9)],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
    static let glassFill = LinearGradient(colors: [.white.opacity(0.10), .white.opacity(0.035)], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let glassFillStrong = LinearGradient(colors: [.white.opacity(0.16), .white.opacity(0.06)], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let glassStroke = LinearGradient(
        colors: [.white.opacity(0.28), .white.opacity(0.05), violet.opacity(0.35)],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
    static let sparkGradient = LinearGradient(colors: [Color(hex: 0xEDE9FE), lilac, violet], startPoint: .topLeading, endPoint: .bottomTrailing)

    static let radiusL: CGFloat = 28
    static let radiusM: CGFloat = 22
    static let radiusS: CGFloat = 16
}

/// Translucent glass surface with a light-catching edge.
struct GlassBackground<S: InsettableShape>: ViewModifier {
    let shape: S
    var strong = false

    func body(content: Content) -> some View {
        content
            .background(
                shape
                    .fill(strong ? Aura.glassFillStrong : Aura.glassFill)
                    .background(.ultraThinMaterial, in: shape)
            )
            .overlay(shape.strokeBorder(Aura.glassStroke, lineWidth: 1))
            .clipShape(shape)
    }
}

extension View {
    func glass(cornerRadius: CGFloat = Aura.radiusM, strong: Bool = false) -> some View {
        modifier(GlassBackground(shape: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous), strong: strong))
    }

    func glassCapsule(strong: Bool = false) -> some View {
        modifier(GlassBackground(shape: Capsule(), strong: strong))
    }

    func glassCircle(strong: Bool = false) -> some View {
        modifier(GlassBackground(shape: Circle(), strong: strong))
    }
}

/// Black-graphite background with slowly drifting violet light.
struct AuraBackground: View {
    @State private var drift = false

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack {
                LinearGradient(colors: [Aura.background, Aura.graphite, Aura.background], startPoint: .top, endPoint: .bottom)
                RadialGradient(colors: [Aura.violetDeep.opacity(0.45), .clear], center: .center, startRadius: 0, endRadius: w * 0.9)
                    .frame(width: w * 1.8, height: w * 1.8)
                    .position(x: w * (drift ? 0.25 : 0.1), y: h * 0.08)
                RadialGradient(colors: [Aura.violet.opacity(0.22), .clear], center: .center, startRadius: 0, endRadius: w * 0.8)
                    .frame(width: w * 1.6, height: w * 1.6)
                    .position(x: w * (drift ? 0.75 : 0.95), y: h * (drift ? 0.8 : 0.75))
            }
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.easeInOut(duration: 14).repeatForever(autoreverses: true)) { drift = true }
        }
    }
}

enum AuraFont {
    static let display = Font.system(size: 34, weight: .bold)
    static let headline = Font.system(size: 28, weight: .bold)
    static let title = Font.system(size: 22, weight: .bold)
    static let titleMedium = Font.system(size: 17, weight: .semibold)
    static let body = Font.system(size: 16)
    static let bodySmall = Font.system(size: 14)
    static let label = Font.system(size: 15, weight: .semibold)
    static let caption = Font.system(size: 12, weight: .medium)
}
