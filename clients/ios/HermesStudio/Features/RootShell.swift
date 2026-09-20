import SwiftUI

/// Signed-in root: a navigation bar with a hamburger, the content of the
/// current conversation mode, and the off-canvas drawer (250 ms slide, 40 %
/// scrim, swipe to close, edge swipe to open) — the web's mobile layout.
///
/// One `NavigationStack` bound to `store.path`, one
/// `navigationDestination(for: NavDestination.self)` on the stack's root view
/// (`content`, outside every `List`): every registry case is pushable, and
/// the desktop's nesting (Agent Manager → Hermes → Kanban) is a plain
/// `store.push`.
///
/// **The rule** (found on TestFlight build 41, where Jobs/Kanban/… under
/// Hermes did nothing while "Manage runtime" on the same screen opened):
/// a `NavigationStack(path:)` honours `NavigationLink(value:)` only from a
/// screen that is itself an element of the path. A screen pushed by a
/// view-destination link (`NavigationLink { SomeView() }`) sits *outside*
/// the path; a value link inside it appends to `store.path`, but the stack
/// cannot place the new screen above one it does not track, so the tap is
/// dropped — a view-destination link from that same screen still works,
/// which is the contrast the owner saw. The registration itself was fine:
/// the modifier is on the root view, `.id(store.languageRefresh)` only
/// recreates the stack, and the mode switch inside `RootContentView` is
/// below the modifier. So: a screen that contains value links must be
/// reached by value — `store.show` / `store.push`, or a
/// `NavigationLink(value:)` from a screen that is already in the path —
/// never by a view-destination link. `AgentManagerView`'s cards go through
/// `store.openAgent`, `AgentDetailView` is constructed by `AgentScreenLoader`
/// alone, and `NavigationRulesTests` pins both.
struct RootShell: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.layoutDirection) private var layoutDirection
    /// Finger travel along the closing direction while dragging (≤ 0).
    @State private var drag: CGFloat = 0

    private var sign: CGFloat { layoutDirection == .rightToLeft ? -1 : 1 }

    var body: some View {
        GeometryReader { geometry in
            let width = min(CoreHubTokens.Layout.drawerMaxWidth, geometry.size.width * CoreHubTokens.Layout.drawerWidthFraction)
            let progress: CGFloat = store.drawerOpen ? max(0, 1 + drag / width) : 0
            ZStack(alignment: .leading) {
                NavigationStack(path: $store.path) { content }
                    .id(store.languageRefresh)
                    .accessibilityHidden(store.drawerOpen)

                Color.black
                    .opacity(CoreHubTokens.Alpha.drawerScrim * progress)
                    .ignoresSafeArea()
                    .allowsHitTesting(store.drawerOpen)
                    .onTapGesture { close() }
                    .accessibilityLabel("Close menu")

                SidebarDrawer(close: close)
                    .frame(width: width)
                    .offset(x: sign * (store.drawerOpen ? drag : -width))
                    .simultaneousGesture(closeGesture(width: width))
                    .accessibilityHidden(!store.drawerOpen)

                if !store.drawerOpen {
                    Color.clear
                        .frame(width: CoreHubTokens.Layout.edgeSwipeWidth)
                        .contentShape(Rectangle())
                        .gesture(openGesture)
                }
            }
            .animation(CoreHubTokens.Motion.drawer, value: store.drawerOpen)
        }
        .task { await store.checkHealth() }
        .sheet(isPresented: $store.searchOpen) { SessionSearchSheet().environmentObject(store) }
    }

    private func close() {
        store.drawerOpen = false
        drag = 0
    }

    private func closeGesture(width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                drag = min(0, value.translation.width * sign)
            }
            .onEnded { value in
                let travelled = -value.translation.width * sign
                let flung = -value.predictedEndTranslation.width * sign > width / 2
                if travelled > width / 3 || flung { close() } else { withAnimation(CoreHubTokens.Motion.drawer) { drag = 0 } }
            }
    }

    private var openGesture: some Gesture {
        DragGesture(minimumDistance: 10)
            .onEnded { value in
                if value.translation.width * sign > 24 { store.openDrawer() }
            }
    }

    private var content: some View {
        RootContentView(mode: store.conversationMode)
            .hermesBackground()
            .toolbarBackground(CoreHubTokens.Palette.bgPrimary, for: .navigationBar)
            .navigationDestination(for: NavDestination.self) { destination in
                ShellDestinationView(destination: destination)
            }
            .sheet(item: $store.roomAction) { action in
                switch action {
                case .create: CreateRoomView().environmentObject(store)
                case .join: JoinRoomView().environmentObject(store)
                }
            }
    }
}

/// The root content of one conversation mode: the selected conversation,
/// room or workflow, or the mode's home screen.
struct RootContentView: View {
    @EnvironmentObject private var store: AppStore
    let mode: ConversationMode

    var body: some View {
        switch mode {
        case .chat: ConversationRootView()
        case .group: RoomRootView()
        case .workflow: WorkflowRootView()
        case .history: ChatsView().shellToolbar()
        }
    }
}

/// `.chat` / `.conversation`: the selected session or the empty chat surface.
struct ConversationRootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        if let session = store.selectedSession {
            ConversationView(session: session, embeddedInShell: true).id(session.id)
        } else {
            ChatHomeView().shellToolbar()
        }
    }
}

/// `.groupChat` / `.room`: the selected room or the rooms list.
struct RoomRootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        if let room = store.selectedRoom {
            GroupRoomView(room: room).id(room.id)
        } else {
            GroupsView().shellToolbar()
        }
    }
}

/// `.workflow` / `.workflowDetail`: the selected workflow or the list.
struct WorkflowRootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        if let workflow = store.selectedWorkflow {
            WorkflowDetailView(workflow: workflow).id(workflow.id).shellToolbar()
        } else {
            WorkflowsView().shellToolbar()
        }
    }
}

/// The screen of every `NavDestination`. Exhaustive on purpose: adding a case
/// to the registry without a screen does not compile, and the unit tests
/// construct this view for every case.
struct ShellDestinationView: View {
    @EnvironmentObject private var store: AppStore
    let destination: NavDestination

    var body: some View {
        switch destination {
        case .newChat, .chat, .conversation: ConversationRootView()
        case .search: SessionSearchSheet()
        case .deviceConnections: DeviceConnectionsView()
        case .agentManager: AgentManagerView()
        case .models: ModelsHomeView()
        case .groupChat, .room: RoomRootView()
        case .workflow, .workflowDetail: WorkflowRootView()
        case .workflowRun: WorkflowRunScreen()
        case .history: ChatsView()
        case .settings: SettingsView()
        case .logs: StudioLogsView()
        case .usage: UsageView()
        case .performance: PerformanceView()
        case .skillsUsage: SkillUsageView()
        case .theme: ThemeView()
        case .profiles: ProfilesView()
        case .agentHermes: AgentScreenLoader(agentID: "hermes")
        case .agentEkko: AgentScreenLoader(agentID: "ekko-agent")
        case .agentCoding: AgentScreenLoader(agentID: store.focusedAgentID)
        case .jobs: CronJobsView()
        case .kanban: KanbanView()
        case .channels: ChannelsView()
        case .skills: AgentSkillsScreen(family: family)
        case .plugins: AgentPluginsScreen(family: family)
        case .presets: DshPresetsView()
        case .mcp: AgentMcpScreen(family: family)
        case .memory: AgentMemoryScreen(family: family)
        case .journey: JourneyView()
        case .hermesSettings: HermesSettingsView()
        case .ekkoSettings: EkkoSettingsView()
        case .codingAgentSettings: CodingAgentSettingsView(agentID: store.focusedAgentID)
        case .globalAgent: GlobalAgentView()
        case .files: StudioFilesView()
        }
    }

    private var family: AgentFamily { AgentFamily(agentID: store.focusedAgentID) }
}

/// Empty chat surface shown before a session is chosen.
struct ChatHomeView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        VStack(spacing: 18) {
            AppMark(size: 72)
            Text("Core Hub").font(CoreHubTokens.Typography.titleFont).foregroundStyle(CoreHubTokens.Palette.textPrimary)
            Text("Start a conversation or pick one from the menu.")
                .font(CoreHubTokens.Typography.bodyFont)
                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                .multilineTextAlignment(.center)
            Button { store.startNewChat() } label: {
                HStack(spacing: 8) { CoreHubIconView(icon: .newChat, size: 16); Text(NavDestination.newChat.label) }
            }
            .buttonStyle(CoreHubPillButtonStyle(prominent: true))
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle("Core Hub")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// The hamburger that opens the drawer.
struct DrawerButton: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Button { store.openDrawer() } label: {
            CoreHubIconView(icon: .menu, size: 22)
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                .frame(width: 38, height: 38)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Menu")
    }
}

extension View {
    /// Adds the hamburger to a root screen of the shell.
    func shellToolbar() -> some View {
        toolbar { ToolbarItem(placement: .topBarLeading) { DrawerButton() } }
    }
}

/// Pill button (radius 999) in the accent colour or as an outlined chip.
struct CoreHubPillButtonStyle: ButtonStyle {
    var prominent = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab, weight: .medium))
            .foregroundStyle(prominent ? CoreHubTokens.Palette.textOnAccent : CoreHubTokens.Palette.textPrimary)
            .padding(.horizontal, 14)
            .frame(height: 34)
            .background(prominent ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.bgCard, in: Capsule())
            .overlay(Capsule().stroke(prominent ? Color.clear : CoreHubTokens.Palette.inputBorderIdle, lineWidth: 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
