import XCTest
@testable import HermesStudio

/// The Swift port of `boring-avatars-vanilla` must agree with the web,
/// character for character, or the same profile shows two different faces on
/// the phone and in the browser.
///
/// The expectations below were **generated from the library itself**, not
/// hand-derived: `node_modules/boring-avatars-vanilla/dist/index.js` was
/// invoked with `variant: 'beam'` and the default palette for each seed, and
/// the numbers were read back out of the SVG it returned (the background
/// `fill`, the wrapper `transform` / `rx`, the face group `transform`, the
/// first eye's `x`, and whether the mouth path carries `stroke-linecap`).
final class BoringAvatarTests: XCTestCase {
    private struct Fixture {
        let seed: String
        let wrapper: String
        let face: String
        let background: String
        let translateX: Double
        let translateY: Double
        let rotate: Double
        let scale: Double
        let mouthOpen: Bool
        let circle: Bool
        let eyeSpread: Int
        let mouthSpread: Int
        let faceRotate: Double
        let faceTranslateX: Double
        let faceTranslateY: Double
    }

    private let fixtures: [Fixture] = [
        Fixture(seed: "default", wrapper: "#92A1C6", face: "#000000", background: "#C271B4", translateX: -1, translateY: 5, rotate: 345, scale: 1, mouthOpen: false, circle: true, eyeSpread: 0, mouthSpread: 0, faceRotate: 5, faceTranslateX: -1, faceTranslateY: 1),
        Fixture(seed: "Clara Barton", wrapper: "#146A7C", face: "#FFFFFF", background: "#C20D90", translateX: 5, translateY: 3, rotate: 191, scale: 1.2, mouthOpen: true, circle: false, eyeSpread: 1, mouthSpread: 2, faceRotate: -1, faceTranslateX: 7, faceTranslateY: 0),
        Fixture(seed: "twuijri", wrapper: "#C271B4", face: "#000000", background: "#146A7C", translateX: 8, translateY: 8, rotate: 38, scale: 1.2, mouthOpen: false, circle: false, eyeSpread: 3, mouthSpread: 2, faceRotate: -8, faceTranslateX: 4, faceTranslateY: 4),
        Fixture(seed: "ekko-agent", wrapper: "#92A1C6", face: "#000000", background: "#C271B4", translateX: 4, translateY: 4, rotate: 250, scale: 1.1, mouthOpen: true, circle: false, eyeSpread: 0, mouthSpread: 1, faceRotate: 0, faceTranslateX: 2, faceTranslateY: -1),
        Fixture(seed: "hermes", wrapper: "#F0AB3D", face: "#000000", background: "#92A1C6", translateX: 2, translateY: 2, rotate: 122, scale: 1.2, mouthOpen: true, circle: true, eyeSpread: 2, mouthSpread: 2, faceRotate: 2, faceTranslateX: -2, faceTranslateY: 0),
        Fixture(seed: "Core Hub", wrapper: "#146A7C", face: "#FFFFFF", background: "#C20D90", translateX: 6, translateY: 6, rotate: 236, scale: 1.2, mouthOpen: false, circle: false, eyeSpread: 1, mouthSpread: 2, faceRotate: 6, faceTranslateX: 4, faceTranslateY: 0),
        Fixture(seed: "المدير", wrapper: "#C20D90", face: "#FFFFFF", background: "#F0AB3D", translateX: 8, translateY: 0, rotate: 34, scale: 1.1, mouthOpen: true, circle: false, eyeSpread: 4, mouthSpread: 1, faceRotate: -4, faceTranslateX: 4, faceTranslateY: -2),
        Fixture(seed: "a", wrapper: "#F0AB3D", face: "#000000", background: "#92A1C6", translateX: 7, translateY: -3, rotate: 97, scale: 1.1, mouthOpen: true, circle: false, eyeSpread: 2, mouthSpread: 1, faceRotate: -7, faceTranslateX: 3.5, faceTranslateY: -6),
        Fixture(seed: "", wrapper: "#92A1C6", face: "#000000", background: "#C271B4", translateX: 4, translateY: 4, rotate: 0, scale: 1, mouthOpen: true, circle: true, eyeSpread: 0, mouthSpread: 0, faceRotate: 0, faceTranslateX: 0, faceTranslateY: 0),
        Fixture(seed: "Ω≈ç√", wrapper: "#C20D90", face: "#FFFFFF", background: "#F0AB3D", translateX: 8, translateY: 8, rotate: 194, scale: 1.2, mouthOpen: false, circle: false, eyeSpread: 4, mouthSpread: 2, faceRotate: 4, faceTranslateX: 4, faceTranslateY: 4),
        Fixture(seed: "default-profile", wrapper: "#C271B4", face: "#000000", background: "#146A7C", translateX: 1, translateY: 1, rotate: 243, scale: 1, mouthOpen: true, circle: true, eyeSpread: 3, mouthSpread: 0, faceRotate: -3, faceTranslateX: -3, faceTranslateY: -2),
        Fixture(seed: "Agent 7", wrapper: "#C271B4", face: "#000000", background: "#146A7C", translateX: -4, translateY: -4, rotate: 268, scale: 1.1, mouthOpen: true, circle: true, eyeSpread: 3, mouthSpread: 1, faceRotate: -8, faceTranslateX: -4, faceTranslateY: -4),
        Fixture(seed: "🙂", wrapper: "#92A1C6", face: "#000000", background: "#C271B4", translateX: -1, translateY: 5, rotate: 325, scale: 1.1, mouthOpen: false, circle: true, eyeSpread: 0, mouthSpread: 1, faceRotate: -5, faceTranslateX: -5, faceTranslateY: 5),
        Fixture(seed: "aa", wrapper: "#C20D90", face: "#FFFFFF", background: "#F0AB3D", translateX: 0, translateY: 8, rotate: 224, scale: 1.2, mouthOpen: false, circle: true, eyeSpread: 4, mouthSpread: 2, faceRotate: 4, faceTranslateX: 0, faceTranslateY: 4),
        Fixture(seed: "ab", wrapper: "#92A1C6", face: "#000000", background: "#C271B4", translateX: -1, translateY: 5, rotate: 225, scale: 1, mouthOpen: false, circle: true, eyeSpread: 0, mouthSpread: 0, faceRotate: 5, faceTranslateX: -1, faceTranslateY: 4),
        Fixture(seed: "zz", wrapper: "#C20D90", face: "#FFFFFF", background: "#F0AB3D", translateX: 0, translateY: 8, rotate: 304, scale: 1.1, mouthOpen: false, circle: true, eyeSpread: 4, mouthSpread: 1, faceRotate: 4, faceTranslateX: 0, faceTranslateY: 4),
        Fixture(seed: "user@example.com", wrapper: "#F0AB3D", face: "#000000", background: "#92A1C6", translateX: 6, translateY: 6, rotate: 152, scale: 1.2, mouthOpen: false, circle: false, eyeSpread: 2, mouthSpread: 2, faceRotate: 2, faceTranslateX: 0, faceTranslateY: 0),
    ]

    func testBeamMatchesTheWebLibraryForEverySeed() {
        for fixture in fixtures {
            let beam = BoringAvatar.beam(name: fixture.seed)
            let seed = "seed \(fixture.seed.debugDescription)"
            XCTAssertEqual(beam.wrapperColor, fixture.wrapper, seed)
            XCTAssertEqual(beam.faceColor, fixture.face, seed)
            XCTAssertEqual(beam.backgroundColor, fixture.background, seed)
            XCTAssertEqual(beam.wrapperTranslateX, fixture.translateX, accuracy: 1e-9, seed)
            XCTAssertEqual(beam.wrapperTranslateY, fixture.translateY, accuracy: 1e-9, seed)
            XCTAssertEqual(beam.wrapperRotate, fixture.rotate, accuracy: 1e-9, seed)
            XCTAssertEqual(beam.wrapperScale, fixture.scale, accuracy: 1e-9, seed)
            XCTAssertEqual(beam.isMouthOpen, fixture.mouthOpen, seed)
            XCTAssertEqual(beam.isCircle, fixture.circle, seed)
            XCTAssertEqual(beam.eyeSpread, fixture.eyeSpread, seed)
            XCTAssertEqual(beam.mouthSpread, fixture.mouthSpread, seed)
            XCTAssertEqual(beam.faceRotate, fixture.faceRotate, accuracy: 1e-9, seed)
            XCTAssertEqual(beam.faceTranslateX, fixture.faceTranslateX, accuracy: 1e-9, seed)
            XCTAssertEqual(beam.faceTranslateY, fixture.faceTranslateY, accuracy: 1e-9, seed)
        }
    }

    /// `hashCode` walks UTF-16 code units, so an emoji contributes two and a
    /// non-Latin script is not special-cased.
    func testHashMatchesJavaScriptCharCodeAt() {
        XCTAssertEqual(BoringAvatar.hash(""), 0)
        XCTAssertEqual(BoringAvatar.hash("a"), 97)
        XCTAssertEqual(BoringAvatar.hash("aa"), 3104)
        XCTAssertEqual(BoringAvatar.hash("twuijri"), 905600918)
        XCTAssertEqual(BoringAvatar.hash("المدير"), 623012434)
        XCTAssertEqual(BoringAvatar.hash("🙂"), 1772965, "one emoji is two UTF-16 units")
        // 32-bit wraparound: a long seed keeps the hash inside Int32 before abs.
        XCTAssertLessThanOrEqual(BoringAvatar.hash(String(repeating: "core-hub", count: 64)), 2147483648)
    }

    func testDigitBooleanAndUnitFollowTheLibrary() {
        XCTAssertEqual(BoringAvatar.digit(1544803905, 3), 3)
        XCTAssertEqual(BoringAvatar.digit(97, 1), 9)
        XCTAssertEqual(BoringAvatar.digit(97, 0), 7)
        XCTAssertEqual(BoringAvatar.digit(1234, 1), 3)
        XCTAssertFalse(BoringAvatar.boolean(1234, 1), "digit 3 is odd")
        XCTAssertTrue(BoringAvatar.boolean(1244, 1), "digit 4 is even")
        // index 0 is falsy in JS, so `getUnit` never negates without an index.
        XCTAssertEqual(BoringAvatar.unit(97, 10), 7)
        XCTAssertEqual(BoringAvatar.unit(97, 10, index: 1), 7, "digit 9 is odd → not negated")
        XCTAssertEqual(BoringAvatar.unit(1220755802, 10, index: 2), -2, "digit 8 is even → negated")
    }

    /// The YIQ contrast test that decides whether the face is drawn in black
    /// or white on top of the wrapper colour.
    func testContrastPicksBlackOnLightAndWhiteOnDark() {
        XCTAssertEqual(BoringAvatar.contrast("#92A1C6"), "#000000")
        XCTAssertEqual(BoringAvatar.contrast("#F0AB3D"), "#000000")
        XCTAssertEqual(BoringAvatar.contrast("#146A7C"), "#FFFFFF")
        XCTAssertEqual(BoringAvatar.contrast("#C20D90"), "#FFFFFF")
        XCTAssertEqual(BoringAvatar.contrast("92A1C6"), "#000000", "the leading # is optional")
    }

    /// `ProfileAvatar.vue`: `avatar?.seed || (name || 'default')`.
    func testSeedFallsBackLikeTheWebComponent() {
        XCTAssertEqual(BoringAvatar.seed(avatarSeed: nil, name: "main"), "main")
        XCTAssertEqual(BoringAvatar.seed(avatarSeed: nil, name: ""), "default")
        XCTAssertEqual(BoringAvatar.seed(avatarSeed: "  ", name: "main"), "main", "a blank seed is not a seed")
        let spec = AvatarSpec(["type": "generated", "seed": "pinned"])
        XCTAssertEqual(BoringAvatar.seed(avatarSeed: spec?.seed, name: "main"), "pinned")
        let image = AvatarSpec(["type": "image", "dataUrl": "data:image/png;base64,AAAA"])
        XCTAssertEqual(BoringAvatar.seed(avatarSeed: image?.seed, name: "main"), "main",
                       "an image avatar still has a generated seed to fall back to")
    }
}
