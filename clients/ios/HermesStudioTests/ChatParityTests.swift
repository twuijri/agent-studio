import Foundation
import XCTest
@testable import HermesStudio

/// M3: socket event mapping, the stream reducer, tool/thinking/context
/// formatting, chunked uploads, media links and the location payload.
final class ChatParityTests: XCTestCase {
    private let sender = "Hermes"

    private func fixedNow(_ offset: TimeInterval = 0) -> Date { Date(timeIntervalSince1970: 1_800_000_000 + offset) }

    // MARK: Socket contract

    func testHandshakeRegistersPhoneAsMobileTarget() throws {
        let url = try XCTUnwrap(SocketIOConnection.handshakeURL(baseURL: "https://hub.example", profile: "main", platform: "ios"))
        XCTAssertEqual(url.scheme, "wss")
        XCTAssertEqual(url.path, "/socket.io/")
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        XCTAssertEqual(items.first { $0.name == "EIO" }?.value, "4")
        XCTAssertEqual(items.first { $0.name == "transport" }?.value, "websocket")
        XCTAssertEqual(items.first { $0.name == "profile" }?.value, "main")
        XCTAssertEqual(items.first { $0.name == "platform" }?.value, "ios")
    }

    func testToolEventsCarryPreviewArgumentsOutputAndTruncation() {
        let started = ToolEvent.parse(event: "tool.started", json: ["tool_call_id": "c1", "tool": "terminal", "arguments": ["command": "ls -la\n/tmp"], "preview": "ls -la"])
        XCTAssertEqual(started.id, "c1")
        XCTAssertEqual(started.name, "terminal")
        XCTAssertEqual(started.status, .running)
        XCTAssertEqual(started.detail, "ls -la")
        XCTAssertTrue(started.arguments?.contains("ls -la") == true)

        let completed = ToolEvent.parse(event: "tool.completed", json: ["tool_call_id": "c1", "name": "terminal", "output": "done", "duration": 1.25, "output_truncated": true, "output_original_length": 9000, "preview_truncated": true])
        XCTAssertEqual(completed.status, .done)
        XCTAssertEqual(completed.output, "done")
        XCTAssertEqual(completed.duration, 1.25)
        XCTAssertTrue(completed.outputTruncated)
        XCTAssertEqual(completed.outputOriginalLength, 9000)
        XCTAssertTrue(completed.previewTruncated)

        let failed = ToolEvent.parse(event: "tool.failed", json: ["tool_call_id": "c2", "tool": "python", "error": "boom"])
        XCTAssertEqual(failed.status, .error)
        XCTAssertEqual(failed.output, "boom")

        let flagged = ToolEvent.parse(event: "tool.completed", json: ["tool_call_id": "c3", "tool": "web", "is_error": true, "output": "404"])
        XCTAssertEqual(flagged.status, .error)
    }

    func testServerEventsMapToStreamEvents() throws {
        let usage = ChatSocket.events(for: "usage.updated", json: ["inputTokens": 10, "outputTokens": 5, "contextTokens": 42_000], sessionID: "s")
        guard case let .usage(tokens, window) = try XCTUnwrap(usage.first) else { return XCTFail("usage expected") }
        XCTAssertEqual(tokens, 42_000)
        XCTAssertNil(window)

        let settings = ChatSocket.events(for: "session.settings.updated", json: ["model": "gpt-5", "provider": "openai", "reasoning_effort": "high", "push_enabled": false], sessionID: "s")
        guard case let .settingsUpdated(update) = try XCTUnwrap(settings.first) else { return XCTFail("settings expected") }
        XCTAssertEqual(update.model, "gpt-5")
        XCTAssertEqual(update.reasoningEffort, "high")
        XCTAssertEqual(update.pushEnabled, false)

        let location = ChatSocket.events(for: "location.requested", json: ["location_request_id": "L1", "session_id": "s", "purpose": "weather", "accuracy": "precise", "timeout_ms": 20_000], sessionID: "s")
        guard case let .locationRequested(request) = try XCTUnwrap(location.first) else { return XCTFail("location expected") }
        XCTAssertEqual(request.id, "L1")
        XCTAssertEqual(request.accuracy, "precise")
        XCTAssertEqual(request.timeoutMs, 20_000)

        let peer = ChatSocket.events(for: "run.peer_user_message", json: ["message": ["role": "user", "content": "from the web", "timestamp": 1_800_000_000]], sessionID: "s")
        guard case let .peerMessage(role, content, timestamp) = try XCTUnwrap(peer.first) else { return XCTFail("peer expected") }
        XCTAssertEqual(role, "user")
        XCTAssertEqual(content, "from the web")
        XCTAssertNotNil(timestamp)

        let compression = ChatSocket.events(for: "compression.started", json: ["message_count": 40, "token_count": 120_000], sessionID: "s")
        guard case let .compression(phase, messages, tokens) = try XCTUnwrap(compression.first) else { return XCTFail("compression expected") }
        XCTAssertEqual(phase, "started"); XCTAssertEqual(messages, 40); XCTAssertEqual(tokens, 120_000)

        let calendar = ChatSocket.events(for: "calendar.requested", json: ["calendar_request_id": "C1"], sessionID: "s")
        guard case let .deviceRequested(kind, id) = try XCTUnwrap(calendar.first) else { return XCTFail("device request expected") }
        XCTAssertEqual(kind, "calendar"); XCTAssertEqual(id, "C1")

        let command = ChatSocket.events(for: "session.command", json: ["command": "fork", "ok": true, "message": "Forked"], sessionID: "s")
        guard case let .sessionCommand(result) = try XCTUnwrap(command.first) else { return XCTFail("command expected") }
        XCTAssertTrue(result.terminal, "terminal defaults to true unless the server sends false")
        let live = ChatSocket.events(for: "session.command", json: ["command": "compress", "ok": true, "terminal": false], sessionID: "s")
        guard case let .sessionCommand(liveResult) = try XCTUnwrap(live.first) else { return XCTFail("command expected") }
        XCTAssertFalse(liveResult.terminal)

        let interim = ChatSocket.events(for: "message.interim", json: ["text": "partial", "already_streamed": true], sessionID: "s")
        XCTAssertTrue(interim.isEmpty, "already streamed interim text must not duplicate deltas")
    }

    func testApprovalChoicesAcceptStringsAndObjects() {
        let strings = ChatInteraction(event: "approval.requested", payload: ["approval_id": "a1", "command": "rm -rf build", "choices": ["once", "session"], "allow_permanent": false, "remaining_timeout_ms": 45_000])
        XCTAssertEqual(strings.kind, .approval)
        XCTAssertEqual(strings.choices, ["once", "session"])
        XCTAssertEqual(strings.prompt, "rm -rf build")
        XCTAssertEqual(strings.remainingSeconds, 45)

        let objects = ChatInteraction(event: "approval.requested", payload: ["approval_id": "a2", "description": "Run tests", "choices": [["value": "once", "label": "Once"], ["value": "always"]]])
        XCTAssertEqual(objects.choices, ["once", "always"])
        XCTAssertEqual(objects.prompt, "Run tests")

        let defaults = ChatInteraction(event: "approval.requested", payload: ["approval_id": "a3", "allow_permanent": false])
        XCTAssertEqual(defaults.choices, ["once", "session"], "no permanent choice when the server forbids it")

        let clarify = ChatInteraction(event: "clarify.requested", payload: ["clarify_id": "q1", "question": "Which branch?", "initial_response": "main", "response_mode": "text"])
        XCTAssertEqual(clarify.kind, .clarify)
        XCTAssertEqual(clarify.id, "q1")
        XCTAssertEqual(clarify.initialResponse, "main")
    }

    func testContentBlocksOmitEmptyMediaType() throws {
        let blocks = try XCTUnwrap(ChatSocket.content("hi", [Upload(name: "a.png", path: "/up/a.png", mime: "image/png"), Upload(name: "b.bin", path: "/up/b.bin", mime: "")]) as? [JSON])
        XCTAssertEqual(blocks.count, 3)
        XCTAssertEqual(blocks[0].string("type"), "text")
        XCTAssertEqual(blocks[1].string("type"), "image")
        XCTAssertEqual(blocks[1].string("media_type"), "image/png")
        XCTAssertEqual(blocks[2].string("type"), "file")
        XCTAssertNil(blocks[2]["media_type"])
        XCTAssertEqual(ChatSocket.content("plain", []) as? String, "plain")
    }

    func testRunPayloadHonoursPushOverride() {
        let session = SessionSummary(["id": "s1", "profile": "main", "push_enabled": true])
        let payload = ChatSocket.runPayload(profile: "main", sessionID: "s1", input: "x", attachments: [], reasoningEffort: nil, model: nil, provider: nil, session: session, pushEnabled: false)
        XCTAssertFalse(payload.bool("push_enabled", default: true))
    }

    // MARK: Reducer

    func testDeltasBuildTheStreamingReply() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "hello", attachments: [], sender: sender, now: fixedNow())
        XCTAssertEqual(state.lines.count, 2)
        XCTAssertEqual(state.lines[0].kind, .user)
        XCTAssertTrue(state.isRunning)
        ChatRunReducer.apply(.started(fixedNow(1)), to: &state, sender: sender, now: fixedNow(1))
        ChatRunReducer.apply(.interim("partial answer"), to: &state, sender: sender, now: fixedNow(1))
        XCTAssertEqual(state.lines[1].displayText, "partial answer")
        ChatRunReducer.apply(.reasoning("think "), to: &state, sender: sender, now: fixedNow(2))
        ChatRunReducer.apply(.text("Hel"), to: &state, sender: sender, now: fixedNow(3))
        ChatRunReducer.apply(.text("lo"), to: &state, sender: sender, now: fixedNow(4))
        XCTAssertEqual(state.lines[1].text, "Hello")
        XCTAssertEqual(state.lines[1].interim, "", "interim text disappears once deltas arrive")
        XCTAssertEqual(state.lines[1].thinkingStartedAt, fixedNow(2))
        XCTAssertEqual(state.lines[1].thinkingEndedAt, fixedNow(3))
        ChatRunReducer.apply(.usage(contextTokens: 1200, contextWindow: 8000), to: &state, sender: sender, now: fixedNow(5))
        ChatRunReducer.apply(.completed(output: "ignored", reasoning: "", interrupted: false), to: &state, sender: sender, now: fixedNow(6))
        XCTAssertFalse(state.isRunning)
        XCTAssertFalse(state.lines[1].isStreaming)
        XCTAssertEqual(state.lines[1].text, "Hello", "streamed text wins over the final output")
        XCTAssertEqual(state.contextTokens, 1200)
        XCTAssertEqual(state.contextWindow, 8000)
    }

    func testCompletionOutputFillsAnEmptyReply() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.completed(output: "final", reasoning: "why", interrupted: false), to: &state, sender: sender, now: fixedNow(1))
        XCTAssertEqual(state.lines[1].text, "final")
        XCTAssertEqual(state.lines[1].reasoning, "why")
    }

    func testToolsUpsertAndFinishWithDurations() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.tool(ToolEvent.parse(event: "tool.started", json: ["tool_call_id": "t1", "tool": "terminal", "preview": "ls"])), to: &state, sender: sender, now: fixedNow(1))
        ChatRunReducer.apply(.tool(ToolEvent.parse(event: "tool.started", json: ["tool_call_id": "t2", "tool": "read_file"])), to: &state, sender: sender, now: fixedNow(2))
        ChatRunReducer.apply(.tool(ToolEvent.parse(event: "tool.completed", json: ["tool_call_id": "t1", "tool": "terminal", "output": "a b c"])), to: &state, sender: sender, now: fixedNow(4))
        let tools = state.lines[1].tools
        XCTAssertEqual(tools.count, 2)
        XCTAssertEqual(tools[0].status, .done)
        XCTAssertEqual(tools[0].output, "a b c")
        XCTAssertEqual(tools[0].duration ?? 0, 3, accuracy: 0.01)
        XCTAssertEqual(tools[1].status, .running)
        ChatRunReducer.apply(.completed(output: "ok", reasoning: "", interrupted: false), to: &state, sender: sender, now: fixedNow(5))
        XCTAssertEqual(state.lines[1].tools[1].status, .done, "a run that completes closes its running tools")
    }

    func testAbortCompletedInterruptsToolsAndDropsEmptyReply() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.tool(ToolEvent.parse(event: "tool.started", json: ["tool_call_id": "t1", "tool": "terminal"])), to: &state, sender: sender, now: fixedNow(1))
        ChatRunReducer.apply(.abort(phase: "started"), to: &state, sender: sender, now: fixedNow(2))
        XCTAssertEqual(state.abortPhase, "started")
        ChatRunReducer.apply(.abort(phase: "completed"), to: &state, sender: sender, now: fixedNow(3))
        XCTAssertNil(state.abortPhase)
        XCTAssertFalse(state.isRunning)
        XCTAssertEqual(state.lines[1].tools[0].status, .interrupted)
        XCTAssertFalse(state.lines[1].isStreaming)

        var empty = ChatStreamState()
        ChatRunReducer.beginRun(&empty, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.abort(phase: "completed"), to: &empty, sender: sender, now: fixedNow(1))
        XCTAssertEqual(empty.lines.count, 1, "an empty streaming reply is removed on abort")
    }

    func testFailureBecomesVisibleErrorLine() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.failed("Bridge unavailable", retryable: false), to: &state, sender: sender, now: fixedNow(1))
        XCTAssertEqual(state.lines[1].kind, .error)
        XCTAssertEqual(state.lines[1].text, "Bridge unavailable")
        XCTAssertFalse(state.isRunning)

        var partial = ChatStreamState()
        ChatRunReducer.beginRun(&partial, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.text("half"), to: &partial, sender: sender, now: fixedNow(1))
        ChatRunReducer.apply(.failed("timeout", retryable: false), to: &partial, sender: sender, now: fixedNow(2))
        XCTAssertEqual(partial.lines.count, 3)
        XCTAssertEqual(partial.lines[1].text, "half")
        XCTAssertEqual(partial.lines[2].kind, .error)
    }

    func testInteractionsAppearInlineAndResolve() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.requiresAction(ChatInteraction(event: "approval.requested", payload: ["approval_id": "a1", "command": "npm test"])), to: &state, sender: sender, now: fixedNow(1))
        XCTAssertEqual(state.lines.last?.kind, .interaction)
        XCTAssertEqual(state.pendingInteractions.map(\.id), ["a1"])
        XCTAssertTrue(state.lines[1].isStreaming, "the reply keeps streaming while the approval waits")
        ChatRunReducer.apply(.actionResolved(id: "a1", choice: "session"), to: &state, sender: sender, now: fixedNow(2))
        XCTAssertTrue(state.pendingInteractions.isEmpty)
        XCTAssertEqual(state.lines.last?.interaction?.resolution, "session")
    }

    func testPeerMessagesQueueAndSettingsUpdateState() {
        var state = ChatStreamState()
        ChatRunReducer.apply(.peerMessage(role: "user", content: "sent from the web", timestamp: fixedNow()), to: &state, sender: sender, now: fixedNow())
        XCTAssertEqual(state.lines.last?.kind, .user)
        ChatRunReducer.apply(.queued([QueuedRun(["queue_id": "q1", "content": "later"])]), to: &state, sender: sender)
        XCTAssertEqual(state.queued.first?.id, "q1")
        ChatRunReducer.apply(.queueInsertion(id: "q1", phase: "requesting"), to: &state, sender: sender)
        XCTAssertEqual(state.queueInsertionID, "q1")
        ChatRunReducer.apply(.queueInsertion(id: "q1", phase: "cancelled"), to: &state, sender: sender)
        XCTAssertEqual(state.queueInsertionID, "")
        ChatRunReducer.apply(.settingsUpdated(SessionSettingsUpdate(["model": "claude", "provider": "anthropic", "reasoning_effort": "low", "push_enabled": true])), to: &state, sender: sender)
        XCTAssertEqual(state.model, "claude")
        XCTAssertEqual(state.reasoningEffort, "low")
        XCTAssertEqual(state.pushEnabled, true)
        ChatRunReducer.apply(.compression(phase: "started", messageCount: 3, tokenCount: 900), to: &state, sender: sender)
        XCTAssertEqual(state.compression?.phase, "started")
        ChatRunReducer.apply(.titleUpdated("Renamed"), to: &state, sender: sender)
        XCTAssertEqual(state.title, "Renamed")
    }

    func testTerminalSessionCommandEndsTheRun() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "/fork", attachments: [], sender: sender, now: fixedNow())
        XCTAssertEqual(state.lines[0].kind, .command)
        ChatRunReducer.apply(.sessionCommand(SessionCommandResult(["command": "fork", "ok": true, "message": "Forked into a new session", "terminal": true])), to: &state, sender: sender, now: fixedNow(1))
        XCTAssertFalse(state.isRunning)
        XCTAssertEqual(state.lines.count, 2, "the empty streaming reply is replaced by the system notice")
        XCTAssertEqual(state.lines[1].kind, .system)
        ChatRunReducer.apply(.sessionCommand(SessionCommandResult(["command": "x", "ok": false, "message": "Unknown command"])), to: &state, sender: sender, now: fixedNow(2))
        XCTAssertEqual(state.lines.last?.kind, .error)
    }

    func testResumeAppliesCompletionRecoveredWhileDisconnected() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "q", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.resumed(isWorking: false, completion: ResumeCompletion(output: "recovered", reasoning: "")), to: &state, sender: sender, now: fixedNow(1))
        XCTAssertEqual(state.lines[1].text, "recovered")
        XCTAssertFalse(state.isRunning)

        var working = ChatStreamState()
        ChatRunReducer.apply(.resumed(isWorking: true, completion: nil), to: &working, sender: sender, now: fixedNow())
        XCTAssertTrue(working.isRunning)
        XCTAssertEqual(working.lines.count, 1)
        XCTAssertTrue(working.lines[0].isStreaming)
    }

    func testQueuedRunStartsANewReplyAfterCompletion() {
        var state = ChatStreamState()
        ChatRunReducer.beginRun(&state, text: "first", attachments: [], sender: sender, now: fixedNow())
        ChatRunReducer.apply(.completed(output: "one", reasoning: "", interrupted: false), to: &state, sender: sender, now: fixedNow(1))
        ChatRunReducer.apply(.started(fixedNow(2)), to: &state, sender: sender, now: fixedNow(2))
        ChatRunReducer.apply(.text("two"), to: &state, sender: sender, now: fixedNow(3))
        XCTAssertEqual(state.lines.count, 3)
        XCTAssertEqual(state.lines[1].text, "one")
        XCTAssertEqual(state.lines[2].text, "two")
    }

    // MARK: Formatting

    func testToolSummaryJoinsThreeUniqueNames() {
        let tools = ["terminal", "terminal", "read_file", "web_search", "python"].enumerated().map { ToolStep(id: "\($0.offset)", name: $0.element, status: .done) }
        let summary = ToolSummary(tools: tools)
        XCTAssertEqual(summary.count, 5)
        XCTAssertEqual(summary.names, "terminal · read_file · web_search · +1")
        XCTAssertFalse(summary.hasError)
        XCTAssertFalse(summary.isActive)
        let running = ToolSummary(tools: [ToolStep(id: "a", name: "x", status: .running), ToolStep(id: "b", name: "y", status: .error)])
        XCTAssertTrue(running.isActive)
        XCTAssertTrue(running.hasError)
        XCTAssertEqual(ToolSummary(tools: [ToolStep(id: "a", name: "x", status: .done)]).names, "x")
    }

    func testThinkingDurationMatchesWebFormat() {
        XCTAssertEqual(ThinkingFormat.duration(4.9), "4s")
        XCTAssertEqual(ThinkingFormat.duration(60), "1m")
        XCTAssertEqual(ThinkingFormat.duration(80), "1m 20s")
        var line = ChatLine(text: "", fromUser: false, timestamp: fixedNow(), isStreaming: true)
        XCTAssertNil(ThinkingFormat.observed(for: line))
        line.thinkingStartedAt = fixedNow(10)
        XCTAssertEqual(ThinkingFormat.observed(for: line, now: fixedNow(25)) ?? 0, 15, accuracy: 0.001)
        line.thinkingEndedAt = fixedNow(18)
        XCTAssertEqual(ThinkingFormat.observed(for: line, now: fixedNow(99)) ?? 0, 8, accuracy: 0.001)
        XCTAssertEqual(ThinkingFormat.characterCount("مرحبا"), 5)
    }

    func testReferenceQuoteLimitsToEightLines() {
        let quoted = (1...10).map(String.init).joined(separator: "\n")
        let composed = ReferenceQuote.compose(quoted: quoted, reply: "reply")
        XCTAssertTrue(composed.hasPrefix("> 1\n> 2"))
        XCTAssertTrue(composed.contains("> 8\n> …"))
        XCTAssertFalse(composed.contains("> 9"))
        XCTAssertTrue(composed.hasSuffix("\n\nreply"))
        XCTAssertEqual(ReferenceQuote.compose(quoted: "a", reply: ""), "> a")
    }

    func testContextIndicatorFormat() {
        XCTAssertEqual(ContextUsageFormat.tokens(45_000), "45.0k")
        XCTAssertEqual(ContextUsageFormat.tokens(1_200_000), "1.2M")
        XCTAssertEqual(ContextUsageFormat.tokens(512), "512")
        XCTAssertEqual(ContextUsageFormat.label(used: 45_000, limit: 256_000, remainingWord: "remaining"), "45.0k / 256.0k · remaining 211.0k")
        XCTAssertEqual(ContextUsageFormat.label(used: 45_000, limit: 256_000, remainingWord: "متبقٍ"), "45.0k / 256.0k · متبقٍ 211.0k")
        XCTAssertFalse(ContextUsageFormat.isWarning(used: 200, limit: 1000))
        XCTAssertTrue(ContextUsageFormat.isWarning(used: 801, limit: 1000))
        XCTAssertEqual(ContextUsageFormat.ratio(used: 5, limit: 0), 0)
    }

    func testReasoningEffortOptionsMatchTheWeb() {
        XCTAssertEqual(ReasoningEffortOption.allCases.map(\.rawValue), ["none", "minimal", "low", "medium", "high", "xhigh", "max"])
        XCTAssertEqual(ReasoningEffortOption.label(for: "unknown-value"), "unknown-value")
    }

    // MARK: Uploads, media, location

    func testAppUploadIDsAndChunksFollowTheServerLimits() {
        let id = AppUploadPlan.makeID()
        XCTAssertTrue(AppUploadPlan.isValidID(id))
        XCTAssertEqual(id.count, 32)
        XCTAssertFalse(AppUploadPlan.isValidID("short"))
        XCTAssertFalse(AppUploadPlan.isValidID("has space in it"))
        let ranges = AppUploadPlan.chunks(size: 600_000, maxChunkBytes: 262_144)
        XCTAssertEqual(ranges.count, 3)
        XCTAssertEqual(ranges[0], 0..<262_144)
        XCTAssertEqual(ranges[2], 524_288..<600_000)
        XCTAssertEqual(AppUploadPlan.chunks(size: 100, maxChunkBytes: 1_000_000).first, 0..<100, "never above 256 KiB even if the server allows more")
        XCTAssertTrue(AppUploadPlan.chunks(size: 0, maxChunkBytes: 262_144).isEmpty)
        let session = AppUploadSession(["id": "abcdefgh", "nextOffset": 0, "maxChunkBytes": 262_144])
        XCTAssertEqual(session.maxChunkBytes, 262_144)
        XCTAssertEqual(AppUploadSession(["id": "x"]).maxChunkBytes, AppUploadPlan.defaultChunkBytes)
    }

    func testDeviceLinksAndMediaKinds() throws {
        let link = try XCTUnwrap(DeviceFileLink.parse("device://phone-1/Users/me/clip.MOV"))
        XCTAssertEqual(link.deviceID, "phone-1")
        XCTAssertEqual(link.path, "/Users/me/clip.MOV")
        XCTAssertNil(DeviceFileLink.parse("device://bad id/x"))
        XCTAssertNil(DeviceFileLink.parse("device://phone"))
        XCTAssertEqual(MediaKind.classify(path: "/a/b.mp4"), .video)
        XCTAssertEqual(MediaKind.classify(path: "/a/b.M4A"), .audio)
        XCTAssertEqual(MediaKind.classify(path: "/a/b.txt", mime: "audio/wav"), .audio)
        XCTAssertEqual(MediaKind.classify(path: "/a/b.pdf"), .file)
        let links = ChatFiles.links(in: "[Clip](device://phone-1/tmp/clip.mp4) and [Doc](/srv/report.pdf)")
        XCTAssertEqual(links.map(\.path), ["device://phone-1/tmp/clip.mp4", "/srv/report.pdf"])
        XCTAssertTrue(links[0].isDeviceFile)
        XCTAssertEqual(links[0].mediaKind, .video)
        XCTAssertEqual(ChatFiles.fileName(for: links[0]), "clip.mp4")
        XCTAssertFalse(links[1].isDeviceFile)
    }

    func testHistoryMessagesKeepAttachmentsAndReasoning() {
        let message = Message(["id": "m1", "role": "user", "content": [["type": "text", "text": "see"], ["type": "image", "name": "a.png", "path": "/up/a.png", "media_type": "image/png"]], "reasoning": "r"])
        XCTAssertEqual(message.content, "see")
        XCTAssertEqual(message.attachments.map(\.name), ["a.png"])
        XCTAssertEqual(message.reasoning, "r")
        XCTAssertEqual(ChatLine.kind(forRole: "command"), .command)
        XCTAssertEqual(ChatLine.kind(forRole: "assistant"), .assistant)
        XCTAssertEqual(ChatLine.kind(forRole: "system"), .system)
    }

    func testLocationResponsePayloadMatchesContract() {
        let payload = LocationResponsePayload.success(sessionID: "s", requestID: "L1", latitude: 24.71, longitude: 46.67, accuracyMeters: -1, timestamp: fixedNow())
        XCTAssertEqual(payload.string("status"), "success")
        XCTAssertEqual(payload.string("location_request_id"), "L1")
        let location = payload.object("location")
        XCTAssertEqual(location.double("latitude"), 24.71, accuracy: 0.0001)
        XCTAssertEqual(location.string("coordinateSystem"), "wgs84")
        XCTAssertEqual(location.double("accuracyMeters"), 0, "negative accuracy is clamped")
        XCTAssertEqual(location.int("timestamp"), 1_800_000_000_000)
        XCTAssertNil(location["altitudeMeters"])
        XCTAssertEqual(LocationResponsePayload.denied(sessionID: "s", requestID: "L1").string("status"), "denied")
        let failure = LocationResponsePayload.error(sessionID: "s", requestID: "L1", code: "location_timeout", message: "slow")
        XCTAssertEqual(failure.object("error").string("code"), "location_timeout")
    }
}
