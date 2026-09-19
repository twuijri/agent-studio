import AVFoundation
import SwiftUI
import UIKit

/// Camera view that reports the first QR code it sees (AVFoundation only,
/// no third-party library).
struct QRScannerView: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    let onError: (String) -> Void

    func makeUIViewController(context: Context) -> QRScannerController {
        let controller = QRScannerController()
        controller.onCode = onCode
        controller.onError = onError
        return controller
    }

    func updateUIViewController(_ controller: QRScannerController, context: Context) {}
}

final class QRScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    var onError: ((String) -> Void)?

    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "us.i3u.hermesstudio.qr-scanner")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var delivered = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        requestCameraAccess()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        let session = self.session
        sessionQueue.async { if session.isRunning { session.stopRunning() } }
    }

    private func requestCameraAccess() {
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self else { return }
                guard granted else {
                    self.onError?(String(localized: "Camera access is required to scan the pairing code. Enable it in Settings → Core Hub → Camera."))
                    return
                }
                self.configureSession()
            }
        }
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video) else {
            onError?(String(localized: "No camera is available on this device."))
            return
        }
        do {
            let input = try AVCaptureDeviceInput(device: device)
            guard session.canAddInput(input) else { throw HermesError.server(String(localized: "The camera could not be opened.")) }
            session.addInput(input)
            let output = AVCaptureMetadataOutput()
            guard session.canAddOutput(output) else { throw HermesError.server(String(localized: "The camera could not be opened.")) }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            output.metadataObjectTypes = [.qr]
        } catch {
            onError?(error.localizedDescription)
            return
        }
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        previewLayer = layer
        let session = self.session
        sessionQueue.async { session.startRunning() }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !delivered,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              object.type == .qr,
              let value = object.stringValue, !value.isEmpty else { return }
        delivered = true
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        let session = self.session
        sessionQueue.async { if session.isRunning { session.stopRunning() } }
        onCode?(value)
    }
}
