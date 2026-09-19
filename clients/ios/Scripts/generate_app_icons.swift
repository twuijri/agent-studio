#!/usr/bin/env swift

// Renders the Core Hub app icon: the vector mark from
// packages/client/public/core-hub-mark.svg (#101010) centred at 60 % width on
// the splash colour #f7f7f4, at every size the Info.plist references.
//
//   swift Scripts/generate_app_icons.swift HermesStudio/Resources
//   swift Scripts/generate_app_icons.swift HermesStudio/Resources Design/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
//
// The same geometry is rasterised on Linux with PIL (see docs/mobile/PLAN.md,
// "M2 iOS"); both produce identical images apart from resampling.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

private struct IconSpec {
    let name: String
    let pixels: Int
}

private let specs = [
    IconSpec(name: "AppIcon20x20.png", pixels: 20),
    IconSpec(name: "AppIcon20x20@2x.png", pixels: 40),
    IconSpec(name: "AppIcon20x20@3x.png", pixels: 60),
    IconSpec(name: "AppIcon29x29.png", pixels: 29),
    IconSpec(name: "AppIcon29x29@2x.png", pixels: 58),
    IconSpec(name: "AppIcon29x29@3x.png", pixels: 87),
    IconSpec(name: "AppIcon40x40.png", pixels: 40),
    IconSpec(name: "AppIcon40x40@2x.png", pixels: 80),
    IconSpec(name: "AppIcon40x40@3x.png", pixels: 120),
    IconSpec(name: "AppIcon60x60@2x.png", pixels: 120),
    IconSpec(name: "AppIcon60x60@3x.png", pixels: 180),
    IconSpec(name: "AppIcon76x76.png", pixels: 76),
    IconSpec(name: "AppIcon76x76@2x.png", pixels: 152),
    IconSpec(name: "AppIcon83.5x83.5@2x.png", pixels: 167),
    IconSpec(name: "AppIcon1024.png", pixels: 1024),
]

/// Splash / theme colour #f7f7f4 (DESIGN-SPEC.md).
private let background = CGColor(red: 0xF7 / 255.0, green: 0xF7 / 255.0, blue: 0xF4 / 255.0, alpha: 1)
/// Mark ink #101010 (core-hub-mark.svg).
private let ink = CGColor(red: 0x10 / 255.0, green: 0x10 / 255.0, blue: 0x10 / 255.0, alpha: 1)
/// Mark width relative to the icon width.
private let markScale: CGFloat = 0.60

/// The outer "C" path of core-hub-mark.svg (1024 viewBox).
private func markPath() -> CGMutablePath {
    let path = CGMutablePath()
    path.move(to: CGPoint(x: 302, y: 110))
    path.addLine(to: CGPoint(x: 738, y: 110))
    path.addCurve(to: CGPoint(x: 922, y: 286), control1: CGPoint(x: 847, y: 110), control2: CGPoint(x: 922, y: 180))
    path.addLine(to: CGPoint(x: 922, y: 425))
    path.addLine(to: CGPoint(x: 713, y: 425))
    path.addLine(to: CGPoint(x: 713, y: 328))
    path.addCurve(to: CGPoint(x: 648, y: 263), control1: CGPoint(x: 713, y: 287), control2: CGPoint(x: 687, y: 263))
    path.addLine(to: CGPoint(x: 381, y: 263))
    path.addCurve(to: CGPoint(x: 287, y: 353), control1: CGPoint(x: 326, y: 263), control2: CGPoint(x: 287, y: 299))
    path.addLine(to: CGPoint(x: 287, y: 671))
    path.addCurve(to: CGPoint(x: 381, y: 761), control1: CGPoint(x: 287, y: 725), control2: CGPoint(x: 326, y: 761))
    path.addLine(to: CGPoint(x: 648, y: 761))
    path.addCurve(to: CGPoint(x: 713, y: 696), control1: CGPoint(x: 687, y: 761), control2: CGPoint(x: 713, y: 737))
    path.addLine(to: CGPoint(x: 713, y: 599))
    path.addLine(to: CGPoint(x: 922, y: 599))
    path.addLine(to: CGPoint(x: 922, y: 738))
    path.addCurve(to: CGPoint(x: 738, y: 914), control1: CGPoint(x: 922, y: 844), control2: CGPoint(x: 847, y: 914))
    path.addLine(to: CGPoint(x: 302, y: 914))
    path.addCurve(to: CGPoint(x: 102, y: 720), control1: CGPoint(x: 180, y: 914), control2: CGPoint(x: 102, y: 840))
    path.addLine(to: CGPoint(x: 102, y: 304))
    path.addCurve(to: CGPoint(x: 302, y: 110), control1: CGPoint(x: 102, y: 184), control2: CGPoint(x: 180, y: 110))
    path.closeSubpath()
    // The rounded core square.
    path.addRoundedRect(in: CGRect(x: 369, y: 373, width: 278, height: 278), cornerWidth: 66, cornerHeight: 66)
    return path
}

private func render(pixels: Int) -> CGImage? {
    guard let context = CGContext(
        data: nil,
        width: pixels,
        height: pixels,
        bitsPerComponent: 8,
        bytesPerRow: pixels * 4,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    ) else { return nil }

    context.setShouldAntialias(true)
    context.setAllowsAntialiasing(true)
    context.setFillColor(background)
    context.fill(CGRect(x: 0, y: 0, width: pixels, height: pixels))

    // Mark bounds in the SVG: x 102...922, y 110...914.
    let boundsWidth: CGFloat = 820, boundsHeight: CGFloat = 804
    let scale = CGFloat(pixels) * markScale / boundsWidth
    let offsetX = (CGFloat(pixels) - boundsWidth * scale) / 2 - 102 * scale
    let offsetY = (CGFloat(pixels) - boundsHeight * scale) / 2 - 110 * scale

    // SVG y grows downwards; CGContext y grows upwards.
    context.translateBy(x: 0, y: CGFloat(pixels))
    context.scaleBy(x: 1, y: -1)
    context.translateBy(x: offsetX, y: offsetY)
    context.scaleBy(x: scale, y: scale)

    context.setFillColor(ink)
    context.addPath(markPath())
    context.fillPath(using: .winding)

    return context.makeImage()
}

private func write(_ image: CGImage, to url: URL) throws {
    guard let destination = CGImageDestinationCreateWithURL(
        url as CFURL,
        UTType.png.identifier as CFString,
        1,
        nil
    ) else { throw CocoaError(.fileWriteUnknown) }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { throw CocoaError(.fileWriteUnknown) }
}

let arguments = Array(CommandLine.arguments.dropFirst())
let output = URL(fileURLWithPath: arguments.first ?? "HermesStudio/Resources", isDirectory: true)
try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)

for spec in specs {
    guard let image = render(pixels: spec.pixels) else { throw CocoaError(.fileWriteUnknown) }
    try write(image, to: output.appendingPathComponent(spec.name))
}

if arguments.count > 1, let master = render(pixels: 1024) {
    try write(master, to: URL(fileURLWithPath: arguments[1]))
}
