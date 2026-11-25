import Foundation
import Photos
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct PickedFile {
    let data: Data
    let mime: String
    let name: String
}

enum MediaUtils {
    static let maxBytes = 20 * 1024 * 1024
    static let maxImageSide: CGFloat = 2048

    /// Shrinks a photo and converts it to JPEG (also handles HEIC from the camera).
    static func prepareImage(_ data: Data, name: String = "photo") -> PickedFile? {
        guard let image = UIImage(data: data) else { return nil }
        let scale = min(1, maxImageSide / max(image.size.width, image.size.height))
        let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        guard let jpeg = resized.jpegData(compressionQuality: 0.88) else { return nil }
        let base = (name as NSString).deletingPathExtension
        return PickedFile(data: jpeg, mime: "image/jpeg", name: (base.isEmpty ? "photo" : base) + ".jpg")
    }

    /// Reads a document chosen in the Files picker.
    static func readDocument(_ url: URL) throws -> PickedFile {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        if data.count > maxBytes { throw APIError(code: "too_large", message: "Файл больше 20 МБ") }
        let type = UTType(filenameExtension: url.pathExtension)
        if type?.conforms(to: .image) == true, let image = prepareImage(data, name: url.lastPathComponent) {
            return image
        }
        var mime = type?.preferredMIMEType ?? "application/octet-stream"
        if type?.conforms(to: .sourceCode) == true || type?.conforms(to: .plainText) == true, !mime.hasPrefix("text/") {
            mime = "text/plain"
        }
        return PickedFile(data: data, mime: mime, name: url.lastPathComponent)
    }

    /// Saves image bytes to the photo library.
    static func saveToPhotos(_ data: Data) async throws {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else {
            throw APIError(code: "photos_denied", message: "Разрешите Aura добавлять фото: Настройки → Aura AI → Фото")
        }
        try await PHPhotoLibrary.shared().performChanges {
            PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
        }
    }

    static func temporaryFile(_ data: Data, name: String) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try data.write(to: url, options: .atomic)
        return url
    }

    static func haptic(_ enabled: Bool, _ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
        guard enabled else { return }
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
}

/// UIKit share sheet for SwiftUI.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Keeps the swipe-back gesture working while the system navigation bar is hidden.
extension UINavigationController: UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}
