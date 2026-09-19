import SwiftUI

/// The conversation screen's message box.
///
/// Everything visual lives in `Shared/ComposerCard.swift`; this is the chat's
/// `ComposerConfiguration` and nothing else. The group room draws the same
/// card with `RoomComposer`.
struct ChatComposer: View {
    @Binding var text: String
    var focused: FocusState<Bool>.Binding
    let uploads: [AttachmentUpload]
    let reference: ChatLine?
    let state: ComposerState
    let actions: ComposerActions
    let availableWidth: CGFloat

    var body: some View {
        ComposerCard(
            text: $text,
            focused: focused,
            configuration: .chat,
            uploads: uploads,
            reference: reference,
            state: state,
            actions: actions,
            availableWidth: availableWidth
        )
    }
}
