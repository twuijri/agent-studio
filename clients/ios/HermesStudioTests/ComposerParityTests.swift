import SwiftUI
import XCTest
@testable import HermesStudio

/// The group room and the conversation screen draw one composer
/// (`ComposerCard`). These tests pin the part that decides what each screen
/// shows, the room's @mention insertion, and the direction rules the shared
/// field and the dictation merge rely on. The views need a host, so only the
/// pure model is checked.
final class ComposerParityTests: XCTestCase {

    // MARK: - The shared configuration

    func testRoomCarriesEverythingTheOwnerMissed() {
        let room = ComposerConfiguration.room
        // The complaint on Android was a room box with no microphone and
        // none of the chat controls. All three must be present here.
        XCTAssertTrue(room.hasDictation, "the room composer has no microphone")
        XCTAssertTrue(room.showsSettings, "the room composer has no settings pill")
        XCTAssertTrue(room.pills.contains(.mention), "the room composer has no @ menu")
        XCTAssertTrue(ComposerConfiguration.chat.hasDictation)
    }

    func testRoomOmitsOnlyWhatASessionOrASeatOwns() {
        let chat = ComposerConfiguration.chat
        let room = ComposerConfiguration.room
        // The model and the reasoning effort belong to a session, and every
        // seat carries its own model, so both stay in the room settings.
        XCTAssertEqual(Set(chat.pills).subtracting(room.pills), [.reasoning, .model])
        XCTAssertEqual(Set(room.pills).subtracting(chat.pills), [.mention])
        // Queuing a draft behind the running reply is a chat socket call.
        XCTAssertEqual(Set(chat.controls).subtracting(room.controls), [.queue])
        XCTAssertTrue(Set(room.controls).subtracting(chat.controls).isEmpty)
    }

    func testEverySettingsRowDoesSomethingOnItsScreen() {
        // Voice mode speaks the reply back and push is stored on the session;
        // a room has neither, so its menu carries only the switch its rows
        // actually honour. A pill with no rows is never drawn.
        XCTAssertEqual(ComposerConfiguration.room.toggles, [.showToolCalls])
        XCTAssertEqual(ComposerConfiguration.chat.toggles, [.voiceMode, .showToolCalls, .push])
        for configuration in [ComposerConfiguration.chat, .room] {
            XCTAssertEqual(configuration.showsSettings,
                           configuration.pills.contains(.settings) && !configuration.toggles.isEmpty)
            XCTAssertTrue(configuration.showsSettings, "\(configuration.toggles) has a settings pill with no rows")
        }
    }

    func testBothScreensLeaveRoomForTheirCounter() {
        XCTAssertEqual(ComposerConfiguration.chat.counter, .context)
        // A room has no single context window — each seat carries its own —
        // so the room counter is a total with no bar.
        XCTAssertEqual(ComposerConfiguration.room.counter, .total)
        XCTAssertEqual(ComposerConfiguration.chat.topInset, ComposerConfiguration.room.topInset)
        XCTAssertEqual(ComposerConfiguration(placeholder: "", pills: [], toggles: [], controls: [],
                                             counter: .none, stopsRun: false).topInset, 14)
    }

    func testOnlyTheSingleRunScreenTurnsSendIntoStop() {
        XCTAssertTrue(ComposerConfiguration.chat.stopsRun)
        // Several seats can be replying at once; each is interrupted from
        // the room's activity strip, so one stop button would be ambiguous.
        XCTAssertFalse(ComposerConfiguration.room.stopsRun)
    }

    func testEachScreenHasItsOwnPlaceholder() {
        XCTAssertNotEqual(ComposerConfiguration.chat.placeholder, ComposerConfiguration.room.placeholder)
    }

    func testTogglesReadTheirValueFromTheState() {
        var state = ComposerState()
        state.voiceMode = true
        state.showToolCalls = false
        state.pushEnabled = true
        XCTAssertTrue(state.isOn(.voiceMode))
        XCTAssertFalse(state.isOn(.showToolCalls))
        XCTAssertTrue(state.isOn(.push))
    }

    func testTheRoomCounterHidesUntilTheServerReportsATotal() {
        // The wording is localized; the number is the part under test.
        XCTAssertTrue(TokenTotalView.label(45_000).contains("45.0k"), TokenTotalView.label(45_000))
        XCTAssertTrue(TokenTotalView.label(1_500_000).contains("1.5M"), TokenTotalView.label(1_500_000))
    }

    // MARK: - Mention insertion (the room's @ menu)

    func testMentionSuggestionsListTheSeatsAndAllWhenAllowed() {
        let agents = [RoomAgent(["id": "1", "name": "hermes"]), RoomAgent(["id": "2", "name": "مساعد"])]
        XCTAssertEqual(GroupMentions.suggestions(agents: agents, canMentionAll: true), ["all", "hermes", "مساعد"])
        XCTAssertEqual(GroupMentions.suggestions(agents: agents, canMentionAll: false), ["hermes", "مساعد"])
    }

    func testMentionIsAppendedWithExactlyOneSpace() {
        XCTAssertEqual(GroupMentions.insert("hermes", into: ""), "@hermes ")
        XCTAssertEqual(GroupMentions.insert("hermes", into: "check this"), "check this @hermes ")
        XCTAssertEqual(GroupMentions.insert("hermes", into: "check this "), "check this @hermes ")
        XCTAssertEqual(GroupMentions.insert("hermes", into: "line\n"), "line\n@hermes ")
    }

    func testMentionReplacesThePartialTokenTheOwnerTyped() {
        XCTAssertEqual(GroupMentions.insert("hermes", into: "hello @her"), "hello @hermes ")
        XCTAssertEqual(GroupMentions.insert("hermes", into: "@"), "@hermes ")
        // Only a trailing partial is replaced; a finished mention stays.
        XCTAssertEqual(GroupMentions.insert("ekko", into: "@hermes and "), "@hermes and @ekko ")
    }

    func testMentionKeepsArabicSeatNamesIntact() {
        XCTAssertEqual(GroupMentions.insert("مساعد", into: "راجع"), "راجع @مساعد ")
        XCTAssertEqual(GroupMentions.mentioned(in: "راجع @مساعد", agents: [RoomAgent(["id": "1", "name": "مساعد"])]), ["مساعد"])
    }

    // MARK: - Direction in the shared field

    func testTheFieldFollowsTheDraftNotTheInterface() {
        // An Arabic draft inside an English app, and the reverse. This is the
        // rule `ComposerInput` applies with `.contentDirection(of:)`.
        XCTAssertEqual(ContentDirection.resolve("راجع هذا", interface: .leftToRight), .rightToLeft)
        XCTAssertEqual(ContentDirection.resolve("review this", interface: .rightToLeft), .leftToRight)
    }

    func testAnEmptyDraftKeepsTheInterfaceDirection() {
        // The caret and the placeholder must not jump sides before a word is
        // typed, in either interface.
        XCTAssertEqual(ContentDirection.resolve("", interface: .rightToLeft), .rightToLeft)
        XCTAssertEqual(ContentDirection.resolve("", interface: .leftToRight), .leftToRight)
    }

    func testAnInsertedMentionDoesNotDecideTheDirection() {
        // "@" carries no direction, so a draft that starts with a mention
        // follows the seat name, and an Arabic draft stays right-to-left
        // after a Latin seat name is appended to it.
        let latinFirst = GroupMentions.insert("hermes", into: "")
        XCTAssertEqual(ContentDirection.resolve(latinFirst, interface: .rightToLeft), .leftToRight)
        let arabicDraft = GroupMentions.insert("hermes", into: "راجع هذا")
        XCTAssertEqual(ContentDirection.resolve(arabicDraft, interface: .leftToRight), .rightToLeft)
        let arabicSeat = GroupMentions.insert("مساعد", into: "")
        XCTAssertEqual(ContentDirection.resolve(arabicSeat, interface: .leftToRight), .rightToLeft)
    }

    // MARK: - Dictation lands in the field

    func testDictationAppendsAfterTheDraftWithOneSpace() {
        XCTAssertEqual(DictationText.merged(base: "", transcript: "hello"), "hello")
        XCTAssertEqual(DictationText.merged(base: "note", transcript: "hello"), "note hello")
        XCTAssertEqual(DictationText.merged(base: "note ", transcript: "hello"), "note hello")
        XCTAssertEqual(DictationText.merged(base: "note\n", transcript: "hello"), "note\nhello")
    }

    func testAnEmptyTranscriptRestoresTheDraftUntouched() {
        // A partial result that comes back empty must not eat what was typed
        // before the microphone was opened.
        XCTAssertEqual(DictationText.merged(base: "note", transcript: ""), "note")
        XCTAssertEqual(DictationText.merged(base: "", transcript: ""), "")
    }

    func testDictatedArabicAfterALatinDraftStillFollowsTheFirstWord() {
        let merged = DictationText.merged(base: "note", transcript: "راجع هذا")
        XCTAssertEqual(merged, "note راجع هذا")
        XCTAssertEqual(ContentDirection.resolve(merged, interface: .rightToLeft), .leftToRight)
        let arabicFirst = DictationText.merged(base: "راجع", transcript: "this")
        XCTAssertEqual(ContentDirection.resolve(arabicFirst, interface: .leftToRight), .rightToLeft)
    }

    func testDictationStateStartsSilent() {
        let state = DictationState()
        XCTAssertEqual(state.voice, .idle)
        XCTAssertFalse(state.showingHint)
        XCTAssertFalse(state.showingLanguagePicker)
        XCTAssertFalse(state.replyPending)
    }
}
