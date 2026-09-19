import CoreGraphics
import Foundation
import SwiftUI

/// One resolved (absolute-coordinate) drawing command of an icon path.
enum IconPathCommand: Equatable {
    case move(CGPoint)
    case line(CGPoint)
    case curve(to: CGPoint, control1: CGPoint, control2: CGPoint)
    case close
}

/// A primitive of a 24-viewBox icon as written in `DESIGN-SPEC.md`.
enum IconShape: Equatable {
    case path(String)
    case circle(cx: CGFloat, cy: CGFloat, r: CGFloat)
    case rect(x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat, rx: CGFloat)
}

/// A small SVG path parser for the subset used by the spec icons:
/// `M/m L/l H/h V/v C/c A/a Z/z`, implicit repeated commands, comma or
/// whitespace separators and compact negative numbers (`-3.5-3.5`).
/// Arcs are converted to cubic Bézier segments so the result is plain
/// move/line/curve commands.
enum IconPath {
    /// Parses `d` into absolute commands. Unknown letters are ignored.
    static func parse(_ d: String) -> [IconPathCommand] {
        var commands: [IconPathCommand] = []
        var current = CGPoint.zero
        var subpathStart = CGPoint.zero
        var lastControl: CGPoint? = nil
        let tokens = tokenize(d)
        var index = 0
        var letter: Character = "M"

        func number() -> CGFloat? {
            guard index < tokens.count, case let .number(value) = tokens[index] else { return nil }
            index += 1
            return value
        }
        func hasNumber() -> Bool {
            if index < tokens.count, case .number = tokens[index] { return true }
            return false
        }

        while index < tokens.count {
            if case let .letter(next) = tokens[index] {
                letter = next
                index += 1
                if letter == "Z" || letter == "z" {
                    commands.append(.close)
                    current = subpathStart
                    lastControl = nil
                    continue
                }
            }
            // A letter directly followed by another letter (or the end) draws
            // nothing; the loop head consumes the next letter.
            guard hasNumber() else { continue }
            let relative = letter.isLowercase
            let base = relative ? current : .zero
            switch letter.uppercased() {
            case "M":
                guard let x = number(), let y = number() else { break }
                current = CGPoint(x: base.x + x, y: base.y + y)
                subpathStart = current
                commands.append(.move(current))
                // Subsequent coordinate pairs are implicit line-tos.
                letter = relative ? "l" : "L"
                lastControl = nil
            case "L":
                guard let x = number(), let y = number() else { break }
                current = CGPoint(x: base.x + x, y: base.y + y)
                commands.append(.line(current))
                lastControl = nil
            case "H":
                guard let x = number() else { break }
                current = CGPoint(x: (relative ? current.x : 0) + x, y: current.y)
                commands.append(.line(current))
                lastControl = nil
            case "V":
                guard let y = number() else { break }
                current = CGPoint(x: current.x, y: (relative ? current.y : 0) + y)
                commands.append(.line(current))
                lastControl = nil
            case "C":
                guard let x1 = number(), let y1 = number(), let x2 = number(), let y2 = number(), let x = number(), let y = number() else { break }
                let c1 = CGPoint(x: base.x + x1, y: base.y + y1)
                let c2 = CGPoint(x: base.x + x2, y: base.y + y2)
                current = CGPoint(x: base.x + x, y: base.y + y)
                commands.append(.curve(to: current, control1: c1, control2: c2))
                lastControl = c2
            case "S":
                guard let x2 = number(), let y2 = number(), let x = number(), let y = number() else { break }
                let c1: CGPoint
                if let lastControl { c1 = CGPoint(x: 2 * current.x - lastControl.x, y: 2 * current.y - lastControl.y) } else { c1 = current }
                let c2 = CGPoint(x: base.x + x2, y: base.y + y2)
                current = CGPoint(x: base.x + x, y: base.y + y)
                commands.append(.curve(to: current, control1: c1, control2: c2))
                lastControl = c2
            case "A":
                guard let rx = number(), let ry = number(), let rotation = number(), let large = number(), let sweep = number(), let x = number(), let y = number() else { break }
                let end = CGPoint(x: base.x + x, y: base.y + y)
                for segment in arcToCurves(from: current, to: end, rx: rx, ry: ry, rotationDegrees: rotation, largeArc: large != 0, sweep: sweep != 0) {
                    commands.append(.curve(to: segment.end, control1: segment.control1, control2: segment.control2))
                }
                current = end
                lastControl = nil
            default:
                // Unsupported command: skip its number.
                index += 1
            }
        }
        return commands
    }

    struct CurveSegment: Equatable {
        let control1: CGPoint
        let control2: CGPoint
        let end: CGPoint
    }

    /// SVG arc (endpoint parameterization, spec appendix F.6.5) → cubic
    /// Bézier segments of at most 90° each.
    static func arcToCurves(from start: CGPoint, to end: CGPoint, rx rxIn: CGFloat, ry ryIn: CGFloat, rotationDegrees: CGFloat, largeArc: Bool, sweep: Bool) -> [CurveSegment] {
        if start == end { return [] }
        var rx = abs(rxIn), ry = abs(ryIn)
        if rx == 0 || ry == 0 {
            return [CurveSegment(control1: start, control2: end, end: end)]
        }
        let phi = rotationDegrees * .pi / 180
        let cosPhi = cos(phi), sinPhi = sin(phi)
        let dx2 = (start.x - end.x) / 2, dy2 = (start.y - end.y) / 2
        let x1p = cosPhi * dx2 + sinPhi * dy2
        let y1p = -sinPhi * dx2 + cosPhi * dy2
        let lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        let rx2 = rx * rx, ry2 = ry * ry
        let numerator = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p
        let denominator = rx2 * y1p * y1p + ry2 * x1p * x1p
        var coefficient = denominator == 0 ? 0 : sqrt(max(0, numerator / denominator))
        if largeArc == sweep { coefficient = -coefficient }
        let cxp = coefficient * (rx * y1p / ry)
        let cyp = coefficient * -(ry * x1p / rx)
        let cx = cosPhi * cxp - sinPhi * cyp + (start.x + end.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (start.y + end.y) / 2

        func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let dot = ux * vx + uy * vy
            let length = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            guard length > 0 else { return 0 }
            var value = acos(max(-1, min(1, dot / length)))
            if ux * vy - uy * vx < 0 { value = -value }
            return value
        }
        let theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if !sweep && delta > 0 { delta -= 2 * .pi }
        if sweep && delta < 0 { delta += 2 * .pi }

        let segments = max(1, Int(ceil(abs(delta) / (.pi / 2) - 0.0001)))
        let step = delta / CGFloat(segments)
        let t = 4 / 3 * tan(step / 4)
        func point(_ theta: CGFloat) -> CGPoint {
            let x = rx * cos(theta), y = ry * sin(theta)
            return CGPoint(x: cx + cosPhi * x - sinPhi * y, y: cy + sinPhi * x + cosPhi * y)
        }
        func derivative(_ theta: CGFloat) -> CGPoint {
            let x = -rx * sin(theta), y = ry * cos(theta)
            return CGPoint(x: cosPhi * x - sinPhi * y, y: sinPhi * x + cosPhi * y)
        }
        var result: [CurveSegment] = []
        var theta = theta1
        for index in 0..<segments {
            let next = theta + step
            let p0 = point(theta), p3 = index == segments - 1 ? end : point(next)
            let d0 = derivative(theta), d3 = derivative(next)
            result.append(CurveSegment(
                control1: CGPoint(x: p0.x + t * d0.x, y: p0.y + t * d0.y),
                control2: CGPoint(x: p3.x - t * d3.x, y: p3.y - t * d3.y),
                end: p3
            ))
            theta = next
        }
        return result
    }

    /// Builds a SwiftUI `Path` for the shapes, scaling the `viewBox` square
    /// into `rect`.
    static func path(_ shapes: [IconShape], in rect: CGRect, viewBox: CGFloat = 24) -> Path {
        let scale = min(rect.width, rect.height) / viewBox
        let offsetX = rect.minX + (rect.width - viewBox * scale) / 2
        let offsetY = rect.minY + (rect.height - viewBox * scale) / 2
        func map(_ point: CGPoint) -> CGPoint { CGPoint(x: offsetX + point.x * scale, y: offsetY + point.y * scale) }
        var path = Path()
        for shape in shapes {
            switch shape {
            case let .path(d):
                for command in parse(d) {
                    switch command {
                    case let .move(point): path.move(to: map(point))
                    case let .line(point): path.addLine(to: map(point))
                    case let .curve(to, control1, control2): path.addCurve(to: map(to), control1: map(control1), control2: map(control2))
                    case .close: path.closeSubpath()
                    }
                }
            case let .circle(cx, cy, r):
                let center = map(CGPoint(x: cx, y: cy))
                path.addEllipse(in: CGRect(x: center.x - r * scale, y: center.y - r * scale, width: 2 * r * scale, height: 2 * r * scale))
            case let .rect(x, y, width, height, rx):
                let origin = map(CGPoint(x: x, y: y))
                let frame = CGRect(x: origin.x, y: origin.y, width: width * scale, height: height * scale)
                path.addRoundedRect(in: frame, cornerSize: CGSize(width: rx * scale, height: rx * scale))
            }
        }
        return path
    }

    // MARK: Tokenizer

    enum Token: Equatable {
        case letter(Character)
        case number(CGFloat)
    }

    static func tokenize(_ d: String) -> [Token] {
        var tokens: [Token] = []
        var buffer = ""
        func flush() {
            if !buffer.isEmpty, let value = Double(buffer) { tokens.append(.number(CGFloat(value))) }
            buffer = ""
        }
        for character in d {
            if character.isLetter {
                flush()
                tokens.append(.letter(character))
            } else if character == "," || character.isWhitespace {
                flush()
            } else if character == "-" {
                // A minus starts a new number unless it follows an exponent.
                if buffer.hasSuffix("e") || buffer.hasSuffix("E") { buffer.append(character) } else { flush(); buffer.append(character) }
            } else if character == "." {
                // A second dot starts a new number ("1.5.5" → 1.5, .5).
                if buffer.contains(".") { flush() }
                buffer.append(character)
            } else {
                buffer.append(character)
            }
        }
        flush()
        return tokens
    }
}

/// A `Shape` that draws spec icon primitives inside its frame.
struct IconShapes: Shape {
    let shapes: [IconShape]
    var viewBox: CGFloat = CoreHubTokens.Layout.iconViewBox

    func path(in rect: CGRect) -> Path { IconPath.path(shapes, in: rect, viewBox: viewBox) }
}
