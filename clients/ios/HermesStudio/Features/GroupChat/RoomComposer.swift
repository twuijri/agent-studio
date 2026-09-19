import SwiftUI

/// The group room's message box: the same card, the same attachment sheet,
/// the same microphone and the same send control as the chat composer, with
/// the @ menu in place of the session pills.
///
/// Only what a room genuinely does not have is missing — see
/// `ComposerConfiguration.room`.
struct RoomComposer: View {
    @Binding var text: String
    var focused: FocusState<Bool>.Binding
    let uploads: [AttachmentUpload]
    let state: ComposerState
    let actions: ComposerActions
    let availableWidth: CGFloat

    var body: some View {
        ComposerCard(
            text: $text,
            focused: focused,
            configuration: .room,
            uploads: uploads,
            // A quoted line is folded into the draft by `ReferenceQuote` on
            // this screen, so there is no separate chip to cancel.
            reference: nil,
            state: state,
            actions: actions,
            availableWidth: availableWidth
        )
    }
}
