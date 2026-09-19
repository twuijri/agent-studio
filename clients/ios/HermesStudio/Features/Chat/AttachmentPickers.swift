import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Camera capture (`UIImagePickerController`) returning JPEG data.
struct CameraPicker: UIViewControllerRepresentable {
    let onCapture: (Data, String) -> Void
    @Environment(\.dismiss) private var dismiss

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage, let data = image.jpegData(compressionQuality: 0.85) {
                parent.onCapture(data, "photo-\(Int(Date().timeIntervalSince1970)).jpg")
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

/// A file picked for upload: bytes, display name and MIME type.
struct PickedAttachment {
    let data: Data
    let name: String
    let mime: String

    /// Reads a Photos item (image or video) as data.
    static func load(_ item: PhotosPickerItem) async throws -> PickedAttachment? {
        guard let data = try await item.loadTransferable(type: Data.self) else { return nil }
        let type = item.supportedContentTypes.first
        let ext = type?.preferredFilenameExtension ?? "bin"
        let prefix = type?.conforms(to: .movie) == true ? "video" : "photo"
        return PickedAttachment(data: data, name: "\(prefix)-\(Int(Date().timeIntervalSince1970)).\(ext)", mime: type?.preferredMIMEType ?? "application/octet-stream")
    }

    /// Reads a security-scoped file URL from the document picker.
    static func load(_ url: URL) throws -> PickedAttachment {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        let type = (try? url.resourceValues(forKeys: [.contentTypeKey]).contentType)?.preferredMIMEType
        return PickedAttachment(data: data, name: url.lastPathComponent, mime: type ?? UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream")
    }
}
