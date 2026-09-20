import SwiftUI

/// The row that replaces the pills while the microphone is open, modelled on
/// the Claude app: ✕ at the leading edge, a live waveform filling the middle,
/// ■ stop and ↑ send at the trailing edge. The transcript keeps landing in
/// the field above, which stays editable. The rules are `RecordingStrip` in
/// `Core/Dictation.swift`; this file only draws them.
struct RecordingStripView: View {
    let state: ComposerState
    let actions: ComposerActions
    let canSend: Bool
    /// Send turns into stop-the-run on the one screen that has one run,
    /// exactly as it does in the pill row.
    let isRunning: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 6) {
            RecordingRoundButton(symbol: "xmark", action: actions.cancelDictation)
                .accessibilityLabel("Cancel dictation")
                .accessibilityHint("Discards what was dictated and restores the draft")
            RecordingWaveformView(waveform: state.waveform, language: state.speechLanguage)
            RecordingRoundButton(symbol: "stop.fill", action: actions.stopDictation)
                .accessibilityLabel("Stop dictation")
                .accessibilityHint("Keeps the text and closes the recording strip")
            SendButton(isRunning: isRunning, sending: state.sending, canSend: canSend, send: actions.send, stop: actions.stop)
        }
    }
}

/// ✕ and ■: the composer's 30 pt round control on `bgCard`, like the "+".
struct RecordingRoundButton: View {
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                .frame(width: CoreHubTokens.Layout.composerButton, height: CoreHubTokens.Layout.composerButton)
                .background(CoreHubTokens.Palette.bgCard, in: Circle())
                .overlay(Circle().stroke(CoreHubTokens.Palette.inputBorderIdle))
        }
        .buttonStyle(.plain)
    }
}

/// ~36 bars of `accent`: a bar with sound at full alpha, an idle bar a dot at
/// the idle alpha. The newest level is at the right in both interface
/// directions — a time line reads the same way everywhere — so the layout
/// direction is pinned rather than mirrored. One accessibility element that
/// names the dictation language and reports the level.
struct RecordingWaveformView: View {
    let waveform: RecordingWaveform
    let language: String

    var body: some View {
        Canvas { context, size in
            Self.draw(waveform.levels, in: context, size: size)
        }
        .frame(maxWidth: .infinity)
        .frame(height: CoreHubTokens.Layout.composerButton)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Self.label(language: language))
        .accessibilityValue(Self.value(level: waveform.current))
    }

    static func label(language: String) -> String {
        language.isEmpty
            ? String(localized: "Recording")
            : String(format: String(localized: "Recording in %@"), language)
    }

    static func value(level: Double) -> String {
        String(format: String(localized: "Level %lld%%"), Int((level * 100).rounded()))
    }

    private static func draw(_ levels: [Double], in context: GraphicsContext, size: CGSize) {
        let gap = CoreHubTokens.Layout.waveformBarGap
        let count = CGFloat(levels.count)
        let width = max(1, (size.width - gap * (count - 1)) / count)
        let dot = CoreHubTokens.Layout.waveformDot
        for (index, level) in levels.enumerated() {
            let height = max(dot, CGFloat(level) * size.height)
            let rect = CGRect(x: CGFloat(index) * (width + gap), y: (size.height - height) / 2, width: width, height: height)
            let alpha = RecordingWaveform.isIdle(level) ? CoreHubTokens.Alpha.waveformIdle : 1
            context.fill(Path(roundedRect: rect, cornerRadius: width / 2),
                         with: .color(CoreHubTokens.Palette.accent.opacity(alpha)))
        }
    }
}
