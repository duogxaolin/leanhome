//
//  CameraScanView.swift
//  FeatureOnboarding
//
//  V2 — On-device Camera AI Body Scan. Uses AVFoundation for the camera
//  preview and the Apple Vision framework (`VNDetectHumanBodyPoseRequest`)
//  to extract shoulder/hip landmarks. All processing is 100% on-device:
//  no body photo or biometric data is ever uploaded. The detected
//  Shoulder-to-Waist (SWR) and Waist-to-Hip (WHR) ratios are mapped to an
//  existing `BodyShape` archetype and offered to the caller. If the user
//  declines camera permission, the pose can't be detected, or any error
//  occurs, the scan dismisses silently and the caller falls back to the
//  manual 2D archetype selection — per the spec, no error is surfaced.
//

#if canImport(SwiftUI) && canImport(UIKit)

import SwiftUI
import AVFoundation
import Vision
import CoreDomain

// MARK: - Public entry

/// Full-screen camera scan view. Presented as a sheet from the archetype
/// selection step. On success it invokes `onScanned` with the detected
/// `BodyShape`; on cancel/permission-denied/failure it invokes `onCancel`.
public struct CameraScanView: View {
    private let gender: Gender
    private let onScanned: (BodyShape) -> Void
    private let onCancel: () -> Void

    @StateObject private var sessionModel: CameraSessionModel

    public init(
        gender: Gender,
        onScanned: @escaping (BodyShape) -> Void,
        onCancel: @escaping () -> Void,
    ) {
        self.gender = gender
        self.onScanned = onScanned
        self.onCancel = onCancel
        _sessionModel = StateObject(wrappedValue: CameraSessionModel(gender: gender))
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            CameraPreviewLayerView(session: sessionModel.session)
                .ignoresSafeArea()
            SilhouetteGuideOverlay(detected: sessionModel.detectedShape != nil)
                .allowsHitTesting(false)
        }
        .overlay(alignment: .bottom) {
            scanControls
                .padding()
        }
        .onAppear { sessionModel.start() }
        .onDisappear { sessionModel.stop() }
        // When the user denies camera access, Vision permission is
        // tied to the camera status; fall back silently.
        .onChange(of: sessionModel.permissionDenied) { denied in
            if denied { onCancel() }
        }
    }

    private var scanControls: some View {
        VStack(spacing: 12) {
            Text("Camera Body Scan")
                .font(.headline)
                .foregroundStyle(.white)
            Text("All processing happens on your device. Nothing is uploaded.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.75))
            if let result = sessionModel.detectedShape {
                VStack(spacing: 4) {
                    Text("Detected: \(result.displayName)")
                        .font(.subheadline)
                        .foregroundStyle(.green)
                    Text(String(format: "SWR %.2f · WHR %.2f", sessionModel.swr, sessionModel.whr))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.85))
                }
            } else {
                Text("Stand in frame so shoulders and hips are visible.")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
            }
            HStack(spacing: 12) {
                Button("Cancel", action: onCancel)
                    .buttonStyle(.bordered)
                    .tint(.white)
                Button("Use Scan") {
                    if let shape = sessionModel.detectedShape {
                        onScanned(shape)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(sessionModel.detectedShape == nil)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial.opacity(0.85))
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 12)
    }
}

// MARK: - AV / Vision session model

final class CameraSessionModel: ObservableObject {
    @Published var detectedShape: BodyShape?
    @Published var swr: Double = 0
    @Published var whr: Double = 0
    @Published var permissionDenied: Bool = false

    let session = AVCaptureSession()
    private let gender: Gender
    private let visionQueue = DispatchQueue(label: "leanhome.camera.vision", qos: .userInitiated)
    private let videoOutput = AVCaptureVideoDataOutput()

    init(gender: Gender) {
        self.gender = gender
    }

    func start() {
        permissionDenied = false
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndStart()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if granted { self.configureAndStart() } else { self.permissionDenied = true }
                }
            }
        default:
            permissionDenied = true
        }
    }

    func stop() {
        if session.isRunning {
            session.stopRunning()
        }
    }

    private func configureAndStart() {
        session.beginConfiguration()
        session.sessionPreset = .high
        // Remove any prior inputs/outputs in case start() is reinvoked.
        session.inputs.forEach { session.removeInput($0) }
        session.outputs.forEach { session.removeOutput($0) }

        guard let camera = AVCaptureDevice.default(
            .builtInWideAngleCamera, for: .video, position: .front,
        ) else {
            session.commitConfiguration()
            setErrorFallback()
            return
        }
        do {
            let input = try AVCaptureDeviceInput(device: camera)
            if session.canAddInput(input) { session.addInput(input) }
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.setSampleBufferDelegate(self, queue: visionQueue)
            if session.canAddOutput(videoOutput) { session.addOutput(videoOutput) }
            // Mirror the preview so the user sees themselves naturally.
            if let connection = videoOutput.connection(with: .video) {
                connection.automaticallyAdjustsVideoMirroring = true
                connection.isVideoMirrored = true
            }
            session.commitConfiguration()
            session.startRunning()
        } catch {
            session.commitConfiguration()
            setErrorFallback()
            return
        }
    }

    private func setErrorFallback() {
        // Spec: on insufficient landmarks or any failure, fall back silently.
        detectedShape = nil
        permissionDenied = true
    }
}
extension CameraSessionModel: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection,
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        // Pose analysis runs on `visionQueue` (background); once it
        // finishes, hop to the main queue to update @Published state.
        let resolvedGender = gender
        let request = VNDetectHumanBodyPoseRequest { request, _ in
            DispatchQueue.main.async {
                self.handleVisionResult(request: request, gender: resolvedGender)
            }
        }
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .up, options: [:])
        do {
            try handler.perform([request])
        } catch {
            // Swallow; pose detection unavailable for this frame.
        }
    }

    private func handleVisionResult(request: VNRequest, gender: Gender) {
        guard let observation = request.results?.first as? VNHumanBodyPoseObservation,
              let result = archetypeFromObservation(observation, gender: gender) else {
            return // insufficient landmarks → keep prior detection or none
        }
        detectedShape = result.archetype
        swr = result.swr
        whr = result.whr
    }
}

// MARK: - Vision → landmark computations

/// Composition of computed ratios and the final archetype for a single
/// `VNHumanBodyPoseObservation`. Returned when vector confidence is high
/// enough; `nil` otherwise (silently fall back to manual selection).
struct ArcResult {
    let archetype: BodyShape
    let swr: Double
    let whr: Double
}

/// Pure-function mapping from SWR + WHR to a `BodyShape` per the V2 spec:
///   SWR > 1.4 and WHR < 0.85 → INVERTED_TRIANGLE
///   SWR 1.0–1.4 and WHR 0.85–1.0 → RECTANGLE
///   WHR > 0.9 (male/other) or > 0.85 (female) → OVAL/TRIANGLE/APPLE
///   WHR < 0.85 (female only) → PEAR
///
/// Male-like high WHR falls back to OVAL (endomorph). Female-like
/// ambiguous cases fall back to RECTANGLE. Deterministic, unit-testable.
public func archetype(fromRatios swr: Double, whr: Double, gender: Gender) -> BodyShape {
    let maleLike = (gender == .male || gender == .other)
    switch (swr, whr) {
    case (_, let w) where swr > 1.4 && w < 0.85:
        return .invertedTriangle
    case (_, let w) where (1.0...1.4).contains(swr) && (0.85...1.0).contains(w):
        return .rectangle
    case (_, let w) where w > 0.9 && maleLike:
        return .oval
    case (_, let w) where w > 0.85 && !maleLike:
        return .apple
    case (_, let w) where w < 0.85 && !maleLike:
        return .pear
    // Male-like high WHR not matched above.
    case (_, let w) where w > 0.9:
        return .oval
    default:
        // Male-like low WHR (≤ 0.9) without a stronger inverted-triangle
        // taper → the male pear analogue (.triangle). Female-like
        // ambiguous matches fall back to the neutral rectangle.
        return maleLike ? .triangle : .rectangle
    }
}

/// Extract four key landmarks (shoulders + hips) from a Vision body-pose
/// observation, compute SWR/WHR using a midpoint waist estimate (mean of
/// shoulder width and hip width, matching the iOS/Android shared chunk),
/// and return the mapped archetype along with the ratios.
func archetypeFromObservation(
    _ observation: VNHumanBodyPoseObservation,
    gender: Gender,
) -> ArcResult? {
    guard let ls = try? observation.recognizedPoint(.leftShoulder),
          let rs = try? observation.recognizedPoint(.rightShoulder),
          let lh = try? observation.recognizedPoint(.leftHip),
          let rh = try? observation.recognizedPoint(.rightHip) else { return nil }
    // Confidence check: each point must be confident enough to be reliable.
    let minConfidence: Float = 0.5
    guard ls.confidence >= minConfidence,
          rs.confidence >= minConfidence,
          lh.confidence >= minConfidence,
          rh.confidence >= minConfidence else { return nil }
    // VNRecognizedPoint inherits from VNPoint, exposing `x` and `y` as
    // Double in normalized image coordinates.
    let shoulderWidth = abs(rs.x - ls.x)
    let hipWidth = abs(rh.x - lh.x)
    // Vision exposes a `root` joint (the waist center); we use the mean of
    // shoulder width and hip width as the torso mid-rib-cage estimate
    // since the `root` is a single mid-sagittal point, not a width pair.
    let waistWidth = (shoulderWidth + hipWidth) / 2.0
    if shoulderWidth < 0.02 || hipWidth < 0.02 || waistWidth < 0.02 { return nil }
    let swr = shoulderWidth / waistWidth
    let whr = waistWidth / hipWidth
    let shape = archetype(fromRatios: swr, whr: whr, gender: gender)
    return ArcResult(archetype: shape, swr: swr, whr: whr)
}
// MARK: - Camera preview wrapper

/// Renders a `CALayer`-hosted `AVCaptureVideoPreviewLayer` in SwiftUI.
struct CameraPreviewLayerView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewContainerView {
        let view = PreviewContainerView()
        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.bounds
        view.videoPreviewLayer = preview
        view.layer.addSublayer(preview)
        return view
    }

    func updateUIView(_ uiView: PreviewContainerView, context: Context) {
        uiView.videoPreviewLayer?.frame = uiView.bounds
    }
}

final class PreviewContainerView: UIView {
    var videoPreviewLayer: AVCaptureVideoPreviewLayer?
    override func layoutSubviews() {
        super.layoutSubviews()
        videoPreviewLayer?.frame = bounds
    }
}

// MARK: - Silhouette overlay

private struct SilhouetteGuideOverlay: View {
    let detected: Bool

    var body: some View {
        Canvas { context, size in
            let outline: Color = detected ? Color.green : Color.yellow
            let cx = size.width / 2
            let headRadius = size.width * 0.10
            let torsoTop = size.height * 0.20
            let torsoBottom = size.height * 0.75
            // Head circle
            context.stroke(
                Path { p in
                    p.addEllipse(
                        in: CGRect(
                            x: cx - headRadius, y: torsoTop * 0.45 - headRadius,
                            width: headRadius * 2, height: headRadius * 2,
                        ),
                    )
                },
                with: .color(outline.opacity(0.65)),
                lineWidth: 4,
            )
            // Shoulders line
            context.stroke(
                Path { p in
                    p.move(to: CGPoint(x: cx - size.width * 0.18, y: torsoTop))
                    p.addLine(to: CGPoint(x: cx + size.width * 0.18, y: torsoTop))
                },
                with: .color(outline), lineWidth: 4,
            )
            // Torso sides (triangle silhouette)
            context.stroke(
                Path { p in
                    p.move(to: CGPoint(x: cx - size.width * 0.18, y: torsoTop))
                    p.addLine(to: CGPoint(x: cx - size.width * 0.08, y: torsoBottom))
                },
                with: .color(outline.opacity(0.5)), lineWidth: 3,
            )
            context.stroke(
                Path { p in
                    p.move(to: CGPoint(x: cx + size.width * 0.18, y: torsoTop))
                    p.addLine(to: CGPoint(x: cx + size.width * 0.08, y: torsoBottom))
                },
                with: .color(outline.opacity(0.5)), lineWidth: 3,
            )
            // Hips line
            context.stroke(
                Path { p in
                    p.move(to: CGPoint(x: cx - size.width * 0.11, y: torsoBottom))
                    p.addLine(to: CGPoint(x: cx + size.width * 0.11, y: torsoBottom))
                },
                with: .color(outline), lineWidth: 4,
            )
        }
    }
}

#endif // canImport(SwiftUI) && canImport(UIKit)
