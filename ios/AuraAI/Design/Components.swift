import SwiftUI

/// The Aura four-point spark.
struct SparkShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        let cx = rect.midX, cy = rect.midY
        let k: CGFloat = 0.18, s: CGFloat = 0.06
        var p = Path()
        p.move(to: CGPoint(x: cx, y: rect.minY))
        p.addCurve(to: CGPoint(x: rect.maxX, y: cy), control1: CGPoint(x: cx + w * s, y: cy - h * k), control2: CGPoint(x: cx + w * k, y: cy - h * s))
        p.addCurve(to: CGPoint(x: cx, y: rect.maxY), control1: CGPoint(x: cx + w * k, y: cy + h * s), control2: CGPoint(x: cx + w * s, y: cy + h * k))
        p.addCurve(to: CGPoint(x: rect.minX, y: cy), control1: CGPoint(x: cx - w * s, y: cy + h * k), control2: CGPoint(x: cx - w * k, y: cy + h * s))
        p.addCurve(to: CGPoint(x: cx, y: rect.minY), control1: CGPoint(x: cx - w * k, y: cy - h * s), control2: CGPoint(x: cx - w * s, y: cy - h * k))
        p.closeSubpath()
        return p
    }
}

struct AuraSpark: View {
    var size: CGFloat
    var body: some View {
        SparkShape().fill(Aura.sparkGradient).frame(width: size, height: size)
    }
}

struct AuraLogoTile: View {
    var size: CGFloat = 64
    var body: some View {
        AuraSpark(size: size * 0.55)
            .frame(width: size, height: size)
            .glass(cornerRadius: size * 0.3, strong: true)
    }
}

struct Glow: View {
    var color: Color = Aura.violet
    var size: CGFloat
    var body: some View {
        RadialGradient(colors: [color.opacity(0.55), .clear], center: .center, startRadius: 0, endRadius: size / 2)
            .frame(width: size, height: size)
            .allowsHitTesting(false)
    }
}

/// Shrinks slightly while pressed.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.18), value: configuration.isPressed)
    }
}

struct GradientButton: View {
    let title: String
    var icon: String?
    var enabled = true
    var loading = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                if loading {
                    ProgressView().tint(.white)
                } else {
                    HStack(spacing: 8) {
                        if let icon { Image(systemName: icon) }
                        Text(title).font(AuraFont.label)
                    }
                    .foregroundStyle(enabled ? Color.white : Aura.textTertiary)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(
                RoundedRectangle(cornerRadius: Aura.radiusS, style: .continuous)
                    .fill(enabled ? AnyShapeStyle(Aura.primaryGradient) : AnyShapeStyle(Color.white.opacity(0.08)))
            )
            .overlay(
                RoundedRectangle(cornerRadius: Aura.radiusS, style: .continuous)
                    .strokeBorder(Color.white.opacity(enabled ? 0.25 : 0.06), lineWidth: 1)
            )
            .shadow(color: Aura.violet.opacity(enabled ? 0.35 : 0), radius: 18, y: 8)
        }
        .buttonStyle(PressableStyle())
        .disabled(!enabled || loading)
    }
}

struct GlassIconButton: View {
    let icon: String
    var size: CGFloat = 44
    var tint: Color = Aura.textPrimary
    var filled = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size * 0.4, weight: .semibold))
                .foregroundStyle(enabled ? (filled ? Color.white : tint) : Aura.textTertiary)
                .frame(width: size, height: size)
                .background {
                    if filled { Circle().fill(Aura.primaryGradient) }
                }
                .modifier(OptionalGlassCircle(enabled: !filled))
        }
        .buttonStyle(PressableStyle())
        .disabled(!enabled)
    }
}

private struct OptionalGlassCircle: ViewModifier {
    let enabled: Bool
    @ViewBuilder func body(content: Content) -> some View {
        if enabled { content.glassCircle() } else { content.clipShape(Circle()) }
    }
}

struct AuraTextField: View {
    let placeholder: String
    @Binding var text: String
    var icon: String?
    var secure = false
    var axis: Axis = .horizontal
    var keyboard: UIKeyboardType = .default
    @State private var reveal = false

    var body: some View {
        HStack(alignment: axis == .vertical ? .top : .center, spacing: 12) {
            if let icon {
                Image(systemName: icon).foregroundStyle(Aura.textSecondary).frame(width: 20)
            }
            Group {
                if secure && !reveal {
                    SecureField("", text: $text, prompt: Text(placeholder).foregroundColor(Aura.textTertiary))
                } else {
                    TextField("", text: $text, prompt: Text(placeholder).foregroundColor(Aura.textTertiary), axis: axis)
                        .lineLimit(axis == .vertical ? 3...8 : 1...1)
                        .keyboardType(keyboard)
                }
            }
            .font(AuraFont.body)
            .foregroundStyle(Aura.textPrimary)
            .tint(Aura.lilac)
            .textInputAutocapitalization(keyboard == .emailAddress || secure ? TextInputAutocapitalization.never : TextInputAutocapitalization.sentences)
            .autocorrectionDisabled(keyboard == .emailAddress || secure)
            if secure {
                Button { reveal.toggle() } label: {
                    Image(systemName: reveal ? "eye.slash" : "eye").foregroundStyle(Aura.textSecondary)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 15)
        .glass(cornerRadius: Aura.radiusS)
    }
}

struct TopBar<Trailing: View>: View {
    let title: String
    var onBack: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 12) {
            if let onBack {
                GlassIconButton(icon: "chevron.left", action: onBack)
            }
            Text(title)
                .font(AuraFont.title)
                .foregroundStyle(Aura.textPrimary)
                .lineLimit(1)
            Spacer(minLength: 8)
            trailing()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

extension TopBar where Trailing == EmptyView {
    init(title: String, onBack: (() -> Void)?) {
        self.init(title: title, onBack: onBack) { EmptyView() }
    }
}

struct AvatarView: View {
    let name: String
    var size: CGFloat = 48
    var pro = false

    var body: some View {
        Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased().ifEmpty("A"))
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(Aura.textPrimary)
            .frame(width: size, height: size)
            .background(Circle().fill(LinearGradient(colors: [Aura.graphiteLight, Aura.graphite], startPoint: .top, endPoint: .bottom)))
            .overlay(
                Circle().strokeBorder(pro ? AnyShapeStyle(Aura.primaryGradient) : AnyShapeStyle(Aura.glassStroke), lineWidth: pro ? 2 : 1)
            )
    }
}

struct ProChip: View {
    let isPro: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "crown.fill").foregroundStyle(Aura.gold)
                Text(isPro ? "Pro ✓" : "Pro").font(AuraFont.label).foregroundStyle(Aura.textPrimary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .glassCapsule()
        }
        .buttonStyle(PressableStyle())
    }
}

/// Three pulsing dots shown while Aura is thinking.
struct TypingDots: View {
    @State private var phase = false
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(Aura.lilac)
                    .frame(width: 8, height: 8)
                    .scaleEffect(phase ? 1 : 0.6)
                    .opacity(phase ? 1 : 0.3)
                    .animation(.easeInOut(duration: 0.6).repeatForever().delay(Double(i) * 0.16), value: phase)
            }
        }
        .onAppear { phase = true }
    }
}

struct ErrorBanner: View {
    let message: String
    var action: String?
    var onAction: (() -> Void)?

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.circle").foregroundStyle(Aura.danger)
            Text(message).font(AuraFont.bodySmall).foregroundStyle(Aura.textPrimary)
            Spacer(minLength: 4)
            if let action, let onAction {
                Button(action, action: onAction).font(AuraFont.label).foregroundStyle(Aura.lilac)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: Aura.radiusS).fill(Aura.danger.opacity(0.12)))
        .overlay(RoundedRectangle(cornerRadius: Aura.radiusS).strokeBorder(Aura.danger.opacity(0.35)))
    }
}

struct EmptyStateView: View {
    let icon: String
    let title: String
    let subtitle: String
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 30))
                .foregroundStyle(Aura.lilac)
                .frame(width: 72, height: 72)
                .glass(cornerRadius: 24)
            Text(title).font(AuraFont.titleMedium).foregroundStyle(Aura.textPrimary)
            Text(subtitle).font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary).multilineTextAlignment(.center)
        }
        .padding(32)
    }
}

struct SectionTitle<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: () -> Trailing
    var body: some View {
        HStack {
            Text(title).font(AuraFont.title).foregroundStyle(Aura.textPrimary)
            Spacer()
            trailing()
        }
    }
}

/// Small rounded icon tile used in feature cards and lists.
struct IconTile: View {
    let icon: String
    var size: CGFloat = 46
    var body: some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.45, weight: .medium))
            .foregroundStyle(Aura.lilac)
            .frame(width: size, height: size)
            .background(
                RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
                    .fill(LinearGradient(colors: [Aura.violet.opacity(0.35), Aura.violetDeep.opacity(0.15)], startPoint: .topLeading, endPoint: .bottomTrailing))
            )
    }
}

extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}
