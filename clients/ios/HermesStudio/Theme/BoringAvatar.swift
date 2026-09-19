import SwiftUI

/// A Swift port of `boring-avatars-vanilla` (MIT), the generator the web client
/// uses in `packages/client/src/components/hermes/profiles/ProfileAvatar.vue`.
///
/// Only the `beam` variant is ported, because that is the only one the web
/// asks for. Every helper below is a literal translation of
/// `node_modules/boring-avatars-vanilla/dist/index.js` so that the same seed
/// yields the same avatar in the browser and on the phone:
///
/// ```js
/// const hashCode = (name) => { let hash = 0
///   for (let i = 0; i < name.length; i++) {
///     const character = name.charCodeAt(i)
///     hash = (hash << 5) - hash + character; hash = hash & hash }
///   return Math.abs(hash) }
/// ```
///
/// `charCodeAt` walks UTF-16 code units (so an emoji contributes two), the
/// intermediate arithmetic wraps at 32 bits, and `Math.abs` of `Int32.min`
/// widens to 2_147_483_648 — all three are reproduced here.
enum BoringAvatar {
    /// The library default, used because `ProfileAvatar.vue` passes no palette.
    static let defaultPalette = ["#92A1C6", "#146A7C", "#F0AB3D", "#C271B4", "#C20D90"]

    /// The beam variant's viewBox, `SIZE` in the library.
    static let size: Double = 36

    static func hash(_ name: String) -> Int {
        var value: Int32 = 0
        for unit in name.utf16 {
            value = (value &<< 5) &- value &+ Int32(unit)
        }
        return abs(Int(value))
    }

    /// `Math.floor(number / 10 ** position % 10)`. `number` is never negative
    /// here, so integer division is exact and matches the JS float maths.
    static func digit(_ number: Int, _ position: Int) -> Int {
        var divisor = 1
        for _ in 0..<max(position, 0) { divisor *= 10 }
        return (number / divisor) % 10
    }

    /// `getBoolean` — true when the digit at `position` is even.
    static func boolean(_ number: Int, _ position: Int) -> Bool {
        digit(number, position) % 2 == 0
    }

    /// `getUnit`. `index` 0 means "no index": JS treats `0` as falsy and never
    /// negates, so the default argument keeps that behaviour.
    static func unit(_ number: Int, _ range: Int, index: Int = 0) -> Int {
        let value = number % range
        if index != 0 && digit(number, index) % 2 == 0 { return -value }
        return value
    }

    static func randomColor(_ number: Int, _ colors: [String]) -> String {
        colors[number % colors.count]
    }

    /// `getContrast` — the YIQ test that picks the face colour.
    static func contrast(_ hex: String) -> String {
        let digits = Array(hex.hasPrefix("#") ? String(hex.dropFirst()) : hex)
        guard digits.count >= 6 else { return "#FFFFFF" }
        func component(_ start: Int) -> Double {
            Double(Int(String(digits[start..<(start + 2)]), radix: 16) ?? 0)
        }
        let yiq = (component(0) * 299 + component(2) * 587 + component(4) * 114) / 1000
        return yiq >= 128 ? "#000000" : "#FFFFFF"
    }

    /// Everything `generateData` produces for the `beam` variant.
    struct Beam: Equatable {
        var wrapperColor: String
        var faceColor: String
        var backgroundColor: String
        var wrapperTranslateX: Double
        var wrapperTranslateY: Double
        var wrapperRotate: Double
        var wrapperScale: Double
        var isMouthOpen: Bool
        var isCircle: Bool
        var eyeSpread: Int
        var mouthSpread: Int
        var faceRotate: Double
        var faceTranslateX: Double
        var faceTranslateY: Double
    }

    static func beam(name: String, colors: [String] = defaultPalette) -> Beam {
        let number = hash(name)
        let wrapperColor = randomColor(number, colors)

        let preTranslateX = unit(number, 10, index: 1)
        let translateX = preTranslateX < 5 ? Double(preTranslateX) + size / 9 : Double(preTranslateX)
        let preTranslateY = unit(number, 10, index: 2)
        let translateY = preTranslateY < 5 ? Double(preTranslateY) + size / 9 : Double(preTranslateY)

        return Beam(
            wrapperColor: wrapperColor,
            faceColor: contrast(wrapperColor),
            backgroundColor: randomColor(number + 13, colors),
            wrapperTranslateX: translateX,
            wrapperTranslateY: translateY,
            wrapperRotate: Double(unit(number, 360)),
            wrapperScale: 1 + Double(unit(number, Int(size / 12))) / 10,
            isMouthOpen: boolean(number, 2),
            isCircle: boolean(number, 1),
            eyeSpread: unit(number, 5),
            mouthSpread: unit(number, 3),
            faceRotate: Double(unit(number, 10, index: 3)),
            faceTranslateX: translateX > size / 6 ? translateX / 2 : Double(unit(number, 8, index: 1)),
            faceTranslateY: translateY > size / 6 ? translateY / 2 : Double(unit(number, 7, index: 2))
        )
    }

    /// `ProfileAvatar.vue`'s seed: `props.avatar?.seed || (props.name || 'default')`.
    static func seed(avatarSeed: String?, name: String) -> String {
        avatarSeed?.nilIfEmpty ?? name.nilIfEmpty ?? "default"
    }

    /// `#RRGGBB` → `Color`. The palette and `getContrast` only ever produce
    /// six-digit hex, so anything else falls back to clear rather than crash.
    static func color(_ hex: String) -> Color {
        let text = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard text.count == 6, let value = Int(text, radix: 16) else { return .clear }
        return Color(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

/// Draws one `beam` avatar with Core Graphics instead of shipping an SVG
/// renderer. The geometry is the library's markup, in its own 36 × 36 user
/// space, scaled to the requested size.
struct BeamAvatarView: View {
    let seed: String
    var size: CGFloat = 40

    private var beam: BoringAvatar.Beam { BoringAvatar.beam(name: seed) }

    var body: some View {
        Canvas(rendersAsynchronously: false) { context, canvasSize in
            let scale = canvasSize.width / CGFloat(BoringAvatar.size)
            draw(in: &context, base: CGAffineTransform(scaleX: scale, y: scale), beam: beam)
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    /// `<rect width=36 height=36 fill=backgroundColor />`, the transformed
    /// wrapper, then the face group. `base` maps the library's 36 × 36 user
    /// space onto the view; every element transform is concatenated onto it.
    private func draw(in context: inout GraphicsContext, base: CGAffineTransform, beam: BoringAvatar.Beam) {
        let box = CGRect(x: 0, y: 0, width: BoringAvatar.size, height: BoringAvatar.size)

        var background = context
        background.transform = base
        background.fill(Path(box), with: .color(BoringAvatar.color(beam.backgroundColor)))

        var wrapper = context
        wrapper.transform = wrapperTransform(beam).concatenating(base)
        // `rx="36"` is clamped by SVG to half the width, i.e. a circle.
        let radius = beam.isCircle ? BoringAvatar.size / 2 : BoringAvatar.size / 6
        wrapper.fill(
            Path(roundedRect: box, cornerRadius: radius),
            with: .color(BoringAvatar.color(beam.wrapperColor))
        )

        var face = context
        face.transform = faceTransform(beam).concatenating(base)
        drawFace(in: &face, beam: beam)
    }

    /// `translate(tx ty) rotate(r 18 18) scale(s)` — SVG applies the list
    /// right to left, so scale runs first.
    private func wrapperTransform(_ beam: BoringAvatar.Beam) -> CGAffineTransform {
        let centre = BoringAvatar.size / 2
        return CGAffineTransform.identity
            .translatedBy(x: beam.wrapperTranslateX, y: beam.wrapperTranslateY)
            .translatedBy(x: centre, y: centre)
            .rotated(by: beam.wrapperRotate * .pi / 180)
            .translatedBy(x: -centre, y: -centre)
            .scaledBy(x: beam.wrapperScale, y: beam.wrapperScale)
    }

    /// `translate(ftx fty) rotate(fr 18 18)`.
    private func faceTransform(_ beam: BoringAvatar.Beam) -> CGAffineTransform {
        let centre = BoringAvatar.size / 2
        return CGAffineTransform.identity
            .translatedBy(x: beam.faceTranslateX, y: beam.faceTranslateY)
            .translatedBy(x: centre, y: centre)
            .rotated(by: beam.faceRotate * .pi / 180)
            .translatedBy(x: -centre, y: -centre)
    }

    private func drawFace(in context: inout GraphicsContext, beam: BoringAvatar.Beam) {
        let ink = GraphicsContext.Shading.color(BoringAvatar.color(beam.faceColor))
        let mouthY = 19.0 + Double(beam.mouthSpread)

        if beam.isMouthOpen {
            // `M15 y c2 1 4 1 6 0`, stroke width 1, round caps.
            var path = Path()
            path.move(to: CGPoint(x: 15, y: mouthY))
            path.addCurve(
                to: CGPoint(x: 21, y: mouthY),
                control1: CGPoint(x: 17, y: mouthY + 1),
                control2: CGPoint(x: 19, y: mouthY + 1)
            )
            context.stroke(path, with: ink, style: StrokeStyle(lineWidth: 1, lineCap: .round))
        } else {
            // `M13,y a1,0.75 0 0,0 10,0` — the radii are too small for the
            // 10-unit chord, so SVG scales them up to 5 × 3.75; sweep 0 puts
            // the bulge below the line. Filling closes the chord. Written as
            // two Bézier quarters rather than `addArc`, whose `clockwise`
            // flag reads differently in a y-down space.
            let k = 0.5522847498307936
            var path = Path()
            path.move(to: CGPoint(x: -1, y: 0))
            path.addCurve(
                to: CGPoint(x: 0, y: 1),
                control1: CGPoint(x: -1, y: k),
                control2: CGPoint(x: -k, y: 1)
            )
            path.addCurve(
                to: CGPoint(x: 1, y: 0),
                control1: CGPoint(x: k, y: 1),
                control2: CGPoint(x: 1, y: k)
            )
            path.closeSubpath()
            let mouth = CGAffineTransform(translationX: 18, y: mouthY).scaledBy(x: 5, y: 3.75)
            context.fill(path.applying(mouth), with: ink)
        }

        // Two 1.5 × 2 rects with `rx="1"`: SVG clamps rx to 0.75 and keeps
        // ry at 1, so each corner radius covers the whole rect — an ellipse.
        for x in [14 - Double(beam.eyeSpread), 20 + Double(beam.eyeSpread)] {
            let eye = CGRect(x: x, y: 14, width: 1.5, height: 2)
            context.fill(Path(ellipseIn: eye), with: ink)
        }
    }
}
