import SwiftUI

private let styles: [(String, String)] = [
    ("none", "Без стиля"), ("photo", "Фото"), ("3d", "3D"), ("anime", "Аниме"),
    ("art", "Арт"), ("minimal", "Минимализм"), ("neon", "Неон"),
]
private let sizes: [(String, String)] = [("1024x1024", "1:1"), ("1024x1536", "2:3"), ("1536x1024", "3:2")]

struct ImagesView: View {
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var router: Router
    @State private var prompt = ""
    @State private var style = "none"
    @State private var size = "1024x1024"
    @State private var generating = false
    @State private var error: String?
    @State private var limit = false
    @State private var latest: GeneratedImage?
    @State private var viewing: GeneratedImage?

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Генерация изображений", onBack: { router.back() })
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    AuraTextField(placeholder: "Опишите картинку: «уютная кофейня в дождливый вечер, тёплый свет»", text: $prompt, axis: .vertical)

                    Text("Стиль").font(AuraFont.label).foregroundStyle(Aura.textPrimary)
                    ChipRow(options: styles, selected: $style)
                    Text("Формат").font(AuraFont.label).foregroundStyle(Aura.textPrimary)
                    ChipRow(options: sizes, selected: $size)

                    GradientButton(title: generating ? "Создаю…" : "Создать", icon: "sparkles",
                                   enabled: !prompt.trimmingCharacters(in: .whitespaces).isEmpty && !generating,
                                   loading: generating, action: generate)
                        .padding(.top, 4)

                    if let acc = store.account {
                        Text("Осталось сегодня: \(max(0, acc.limits.dailyImages - acc.usage.imagesToday)) из \(acc.limits.dailyImages)")
                            .font(AuraFont.caption)
                            .foregroundStyle(Aura.textSecondary)
                            .frame(maxWidth: .infinity)
                    }
                    if limit {
                        ErrorBanner(message: "Дневной лимит изображений исчерпан", action: "Aura Pro") { router.open(.pro) }
                    }
                    if let error { ErrorBanner(message: error) }

                    if generating {
                        GeneratingPlaceholder(ratio: ratio(size))
                    } else if let latest {
                        ImageTile(image: latest, ratio: latest.aspectRatio) { viewing = latest }
                    }

                    if !store.images.isEmpty {
                        Text("Галерея").font(AuraFont.title).foregroundStyle(Aura.textPrimary).padding(.top, 8)
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                            ForEach(store.images) { image in
                                ImageTile(image: image, ratio: 1) { viewing = image }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .fullScreenCover(item: $viewing) { image in
            ImageViewer(image: image) { deleted in
                if deleted, latest?.id == image.id { latest = nil }
                viewing = nil
            }
            .environmentObject(store)
        }
    }

    private func ratio(_ size: String) -> CGFloat {
        let parts = size.split(separator: "x").compactMap { Double($0) }
        return parts.count == 2 ? CGFloat(parts[0] / parts[1]) : 1
    }

    private func generate() {
        error = nil
        limit = false
        generating = true
        Task {
            defer { generating = false }
            do {
                latest = try await store.generateImage(prompt: prompt.trimmingCharacters(in: .whitespacesAndNewlines), style: style, size: size)
            } catch let apiError as APIError where apiError.isLimit {
                limit = true
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

private struct ChipRow: View {
    let options: [(String, String)]
    @Binding var selected: String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(options, id: \.0) { id, label in
                    Button { withAnimation(.easeInOut(duration: 0.15)) { selected = id } } label: {
                        Text(label)
                            .font(AuraFont.label)
                            .foregroundStyle(selected == id ? Color.white : Aura.textSecondary)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background { if selected == id { Capsule().fill(Aura.primaryGradient) } }
                            .modifier(ChipGlass(active: selected == id))
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
    }
}

private struct ChipGlass: ViewModifier {
    let active: Bool
    @ViewBuilder func body(content: Content) -> some View {
        if active { content.clipShape(Capsule()) } else { content.glassCapsule() }
    }
}

private struct ImageTile: View {
    @EnvironmentObject private var store: AuraStore
    let image: GeneratedImage
    let ratio: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Color.clear
                .aspectRatio(ratio, contentMode: .fit)
                .overlay {
                    AsyncImage(url: store.url(image.url)) { phase in
                        switch phase {
                        case .success(let img): img.resizable().scaledToFill()
                        case .failure: Image(systemName: "photo").foregroundStyle(Aura.textTertiary)
                        default: ProgressView().tint(Aura.lilac)
                        }
                    }
                }
                .background(Aura.graphiteLight)
                .clipShape(RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous).strokeBorder(Aura.glassStroke))
        }
        .buttonStyle(PressableStyle())
    }
}

private struct GeneratingPlaceholder: View {
    let ratio: CGFloat
    @State private var shimmer = false
    @State private var pulse = false

    var body: some View {
        ZStack {
            Aura.graphiteLight
            LinearGradient(colors: [.clear, Aura.violet.opacity(0.25), .clear], startPoint: .leading, endPoint: .trailing)
                .offset(x: shimmer ? 300 : -300)
            VStack(spacing: 10) {
                Image(systemName: "sparkles")
                    .font(.system(size: 38))
                    .foregroundStyle(Aura.lilac)
                    .scaleEffect(pulse ? 1.08 : 0.9)
                Text("Рисую… обычно это 20–60 секунд").font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary)
            }
        }
        .aspectRatio(ratio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous).strokeBorder(Aura.glassStroke))
        .onAppear {
            withAnimation(.linear(duration: 1.6).repeatForever(autoreverses: false)) { shimmer = true }
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) { pulse = true }
        }
    }
}

private struct ImageViewer: View {
    @EnvironmentObject private var store: AuraStore
    let image: GeneratedImage
    let onClose: (Bool) -> Void
    @State private var busy = false
    @State private var message: String?
    @State private var shareURL: URL?
    @State private var confirmDelete = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.95).ignoresSafeArea()
            VStack(spacing: 14) {
                HStack {
                    Spacer()
                    GlassIconButton(icon: "xmark") { onClose(false) }
                }
                Spacer()
                AsyncImage(url: store.url(image.url)) { img in
                    img.resizable().scaledToFit()
                } placeholder: {
                    ProgressView().tint(Aura.lilac)
                }
                .clipShape(RoundedRectangle(cornerRadius: Aura.radiusM, style: .continuous))
                Spacer()
                Text(image.prompt).font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary).lineLimit(3)
                if let message { Text(message).font(AuraFont.caption).foregroundStyle(Aura.success) }
                HStack(spacing: 28) {
                    GlassIconButton(icon: "square.and.arrow.down", size: 56, enabled: !busy) { save() }
                    GlassIconButton(icon: "square.and.arrow.up", size: 56, enabled: !busy) { share() }
                    GlassIconButton(icon: "trash", size: 56, tint: Aura.danger, enabled: !busy) { confirmDelete = true }
                }
                .padding(.bottom, 12)
            }
            .padding(16)
        }
        .sheet(item: Binding(get: { shareURL.map { ShareItem(url: $0) } }, set: { if $0 == nil { shareURL = nil } })) { item in
            ShareSheet(items: [item.url])
        }
        .confirmationDialog("Удалить изображение?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Удалить", role: .destructive) {
                Task {
                    do {
                        try await store.deleteImage(id: image.id)
                        onClose(true)
                    } catch {
                        message = error.localizedDescription
                    }
                }
            }
        } message: {
            Text("Оно исчезнет из галереи на всех устройствах.")
        }
    }

    private func save() {
        busy = true
        Task {
            defer { busy = false }
            do {
                let data = try await store.api.download(image.url)
                try await MediaUtils.saveToPhotos(data)
                message = "Сохранено в Фото"
            } catch {
                message = error.localizedDescription
            }
        }
    }

    private func share() {
        busy = true
        Task {
            defer { busy = false }
            do {
                let data = try await store.api.download(image.url)
                shareURL = try MediaUtils.temporaryFile(data, name: "aura-\(image.id.prefix(8)).png")
            } catch {
                message = error.localizedDescription
            }
        }
    }
}

private struct ShareItem: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}
