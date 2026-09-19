import SwiftUI

/// Runtime id → bundled avatar asset, mirroring the web's
/// `packages/client/src/utils/chat-agent-avatar.ts`.
enum AgentAvatarAsset: String, CaseIterable {
    case hermes = "Agent-hermes"
    case ekko = "Agent-ekko-agent"
    case claude = "Agent-claude-code"
    case codex = "Agent-codex"
    case pi = "Agent-pi"
    case grok = "Agent-grok"
    case opencode = "Agent-opencode"
    case deepseek = "Agent-deepseek"

    var label: String {
        switch self {
        case .hermes: return "Hermes"
        case .ekko: return "Ekko"
        case .claude: return "Claude"
        case .codex: return "Codex"
        case .pi: return "Pi"
        case .grok: return "Grok"
        case .opencode: return "OpenCode"
        case .deepseek: return "DeepSeek Harness"
        }
    }

    /// `runtime` is `codingAgentId` / `agent` as stored on the session;
    /// `source == "coding_agent"` without a known runtime falls back to Claude,
    /// everything else to Hermes. A `nil` session means Ekko (the web default).
    static func resolve(runtime: String?, source: String?) -> AgentAvatarAsset {
        guard runtime != nil || source != nil else { return .ekko }
        switch (runtime ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "ekko-agent", "ekko_agent", "ekko": return .ekko
        case "claude", "claude-code": return .claude
        case "codex": return .codex
        case "pi": return .pi
        case "grok": return .grok
        case "dsh": return .deepseek
        case "opencode": return .opencode
        default: return source == "coding_agent" ? .claude : .hermes
        }
    }

    static func resolve(session: SessionSummary) -> AgentAvatarAsset {
        resolve(runtime: session.agentID, source: session.source)
    }
}

/// 18 pt (default) circular agent avatar with the 1 px white border; a
/// streaming session shows the animated rainbow ring from the web.
struct AgentAvatarView: View {
    let asset: AgentAvatarAsset
    var size: CGFloat = CoreHubTokens.Layout.sessionAvatar
    var streaming = false
    @State private var spin = false

    var body: some View {
        Image(asset.rawValue)
            .resizable()
            .scaledToFill()
            .frame(width: size, height: size)
            .clipShape(Circle())
            .overlay(Circle().stroke(Color.white, lineWidth: 1))
            .overlay {
                if streaming {
                    Circle()
                        .stroke(AngularGradient(colors: [.red, .orange, .yellow, .green, .blue, .purple, .red], center: .center), lineWidth: 2)
                        .padding(-2)
                        .rotationEffect(.degrees(spin ? 360 : 0))
                        .animation(.linear(duration: 1.6).repeatForever(autoreverses: false), value: spin)
                        .onAppear { spin = true }
                }
            }
            .accessibilityLabel(asset.label)
    }
}
