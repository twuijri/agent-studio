import AVFoundation
import XCTest
@testable import HermesStudio

final class HermesStudioTests: XCTestCase {
    func testWorkflowDefinitionKeepsEditableGraph() {
        let item = WorkflowItem(["id": "wf", "name": "Research", "profile": "main", "workspace": "/work", "nodes": [["id": "a", "type": "agent"]], "edges": [["source": "a", "target": "b"]], "viewport": ["x": 1, "y": 2, "zoom": 1]])
        XCTAssertEqual(item.nodes.first?.string("id"), "a")
        XCTAssertEqual(item.edges.first?.string("target"), "b")
        XCTAssertEqual(item.viewport.int("x"), 1)
    }

    func testSessionSummaryKeepsOrganizationAndPushState() {
        let item = SessionSummary(["id": "s", "profile": "main", "workspace": "/repo", "category_id": 9, "push_enabled": false])
        XCTAssertEqual(item.workspace, "/repo")
        XCTAssertEqual(item.categoryID, 9)
        XCTAssertFalse(item.pushEnabled)
    }
    func testClarificationUsesCanonicalPayloadKeys() {
        let payload = ChatSocket.clarificationPayload(sessionID: "s1", clarificationID: "c1", answer: "نعم")
        XCTAssertEqual(payload.string("clarify_id"), "c1")
        XCTAssertEqual(payload.string("response"), "نعم")
        XCTAssertNil(payload["clarification_id"])
        XCTAssertNil(payload["answer"])
    }

    func testCodingAgentRunPreservesGlobalModeAndSessionSettings() {
        let session = SessionSummary(["id": "s1", "profile": "main", "agent": "codex", "source": "coding_agent", "agent_mode": "global", "workspace": "/work", "category_id": 4, "api_mode": "codex_responses", "base_url": "https://ignored.example", "api_key": "ignored", "push_enabled": false])
        let payload = ChatSocket.runPayload(profile: "main", sessionID: "s1", input: "hello", attachments: [], reasoningEffort: nil, model: nil, provider: nil, session: session)
        XCTAssertEqual(payload.string("mode"), "global")
        XCTAssertEqual(payload.string("workspace"), "/work")
        XCTAssertEqual(payload.int("category_id"), 4)
        XCTAssertFalse(payload.bool("push_enabled", default: true))
        XCTAssertNil(payload["api_key"])
    }

    func testAppResumeRestoresCachedMessagePage() {
        let sessionID = "resume-\(UUID().uuidString)"
        _ = ChatSocket.restoredResume(["id": "cache-1", "messages": [["role": "assistant", "content": "cached"]]], sessionID: sessionID)
        let restored = ChatSocket.restoredResume(["id": "cache-1", "messagesCached": true, "isWorking": false], sessionID: sessionID)
        XCTAssertEqual(restored.objects("messages").first?.string("content"), "cached")
        XCTAssertEqual(ChatSocket.cachedResumeID(sessionID), "cache-1")
    }
    func testJourneyGraphUsesCanonicalGraphEnvelope() {
        let graph = JourneyGraph(["profile": "main", "graph": ["nodes": [["id": "skill:a", "label": "Research", "kind": "skill", "useCount": 4]], "edges": [["source": "skill:a", "target": "memory:b"]], "clusters": []]])
        XCTAssertEqual(graph.profile, "main")
        XCTAssertEqual(graph.nodes.first?.useCount, 4)
        XCTAssertEqual(graph.edges.first?.target, "memory:b")
    }

    func testSkillUsageParsesStudioSummary() {
        let usage = SkillUsageStats(["period_days": 30, "summary": ["total_skill_loads": 8, "total_skill_edits": 2, "total_skill_actions": 10, "distinct_skills_used": 3], "top_skills": [["skill": "research", "view_count": 7, "manage_count": 1, "total_count": 8, "percentage": 80]], "by_day": []])
        XCTAssertEqual(usage.days, 30)
        XCTAssertEqual(usage.totalActions, 10)
        XCTAssertEqual(usage.top.first?.id, "research")
    }

    func testWebhookEndpointUsesCanonicalSnakeCaseFields() {
        let endpoint = WebhookEndpoint(["id": "hook-1", "name": "Ops", "url": "https://example.test/hook", "event_types": ["chat.run.completed"], "profiles": ["main"], "enabled": true, "include_content": true, "include_user_content": false, "allow_private_network": false, "max_retries": 3, "runtime": ["state": "idle"]])
        XCTAssertTrue(endpoint.enabled)
        XCTAssertEqual(endpoint.events, ["chat.run.completed"])
        XCTAssertEqual(endpoint.runtime.string("state"), "idle")
    }
    func testSocketUsageSupportsStudioContextFields() {
        let packet = #"42/chat-run,["run.completed",{"contextTokens":24000,"contextWindow":128000}]"#
        let event = ChatSocket.event(packet, namespace: "/chat-run")
        XCTAssertEqual(event?.1.int("contextTokens"), 24_000)
        XCTAssertEqual(event?.1.int("contextWindow"), 128_000)
    }
    func testExtractsStandardStudioFileLink() {
        let links = ChatFiles.links(in: "[Download report](/home/agent/.hermes/profiles/main/workspace/report.pdf)")
        XCTAssertEqual(links.count, 1)
        XCTAssertEqual(links.first?.label, "Download report")
        XCTAssertEqual(links.first?.path, "/home/agent/.hermes/profiles/main/workspace/report.pdf")
    }

    func testExtractsMalformedAgentLink() {
        let text = "[تحميل العرض](</home/agent/.hermes/profiles/main/workspace/deck.pptx>)"
        let links = ChatFiles.links(in: text)
        XCTAssertEqual(links.first?.path, "/home/agent/.hermes/profiles/main/workspace/deck.pptx")
        XCTAssertEqual(ChatFiles.fileName(for: links[0]), "deck.pptx")
    }

    func testStudioAudioContentBlockRendersAsAttachment() throws {
        let parsed = ChatFiles.parse(#"[{"type":"file","name":"voice-1786646557278.m4a","path":"/home/agent/.hermes-web-ui/upload/manager/bbdd9dabb00e962d.m4a","media_type":"audio/mp4"}]"#)

        XCTAssertTrue(parsed.text.isEmpty)
        XCTAssertEqual(parsed.files.count, 1)
        XCTAssertEqual(parsed.files.first?.label, "voice-1786646557278.m4a")
        XCTAssertEqual(parsed.files.first?.path, "/home/agent/.hermes-web-ui/upload/manager/bbdd9dabb00e962d.m4a")
    }

    func testParsesSocketEvent() {
        let packet = #"42/chat-run,["message.delta",{"delta":"hello"}]"#
        let event = ChatSocket.event(packet, namespace: "/chat-run")
        XCTAssertEqual(event?.0, "message.delta")
        XCTAssertEqual(event?.1.string("delta"), "hello")
    }

    func testResumeRecoversAnswerCompletedDuringDisconnect() {
        let payload: JSON = [
            "isWorking": false,
            "messages": [
                ["role": "assistant", "content": "old answer"],
                ["role": "user", "content": "voice attachment"],
                ["role": "assistant", "content": "transcription completed", "reasoning": "audio processed"],
            ],
        ]

        let completion = ChatSocket.completion(fromResume: payload)

        XCTAssertEqual(completion?.output, "transcription completed")
        XCTAssertEqual(completion?.reasoning, "audio processed")
    }

    func testConversationMessageKeepsStudioUnixTimestamp() throws {
        let seconds = 1_786_800_123.0
        let message = Message([
            "id": 42,
            "role": "assistant",
            "content": "older reply",
            "timestamp": seconds,
        ])

        XCTAssertEqual(try XCTUnwrap(message.sentAt).timeIntervalSince1970, seconds, accuracy: 0.001)
        XCTAssertEqual(
            try XCTUnwrap(StudioTimestamp.date(from: "1786800123456")).timeIntervalSince1970,
            1_786_800_123.456,
            accuracy: 0.001
        )
    }

    func testConversationMessageDoesNotInventMissingTimestamp() {
        let message = Message(["role": "assistant", "content": "undated reply"])

        XCTAssertNil(message.sentAt)
    }

    func testDownloadRequestKeepsTokenOutOfTheURL() throws {
        let client = APIClient(baseURL: "https://studio.example", token: "secret")
        let request = try client.downloadRequest(path: "/workspace/file.pdf", name: "file.pdf", profile: "main")
        let url = try XCTUnwrap(request.url)
        XCTAssertEqual(url.path, "/api/studio/files/download")
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let items = Dictionary(uniqueKeysWithValues: try XCTUnwrap(components?.queryItems).map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(items["profile"], "main")
        XCTAssertEqual(items["path"], "/workspace/file.pdf")
        XCTAssertEqual(items["name"], "file.pdf")
        XCTAssertNil(items["token"])
        XCTAssertFalse(url.absoluteString.contains("secret"))
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer secret")
    }

    func testSessionsUseCanonicalStudioPath() {
        XCTAssertEqual(APIClient.sessionsPath(profile: nil), "/api/studio/sessions?limit=80")
        XCTAssertEqual(APIClient.sessionsPath(profile: ""), "/api/studio/sessions?limit=80")
        XCTAssertEqual(APIClient.sessionsPath(profile: "manager"), "/api/studio/sessions?limit=80&profile=manager")
    }

    func testSessionPreservesCanonicalAgentFamily() {
        let session = SessionSummary(["id": "s1", "profile": "main", "coding_agent_id": "claude-code", "source": "coding_agent"])
        XCTAssertEqual(session.agentID, "claude-code")
        XCTAssertEqual(session.agentDisplayName, "Claude Code")
        XCTAssertEqual(session.source, "coding_agent")
        XCTAssertEqual(AgentIdentity.canonicalID("ekko"), "ekko-agent")
    }

    func testAgentRuntimeStatusUsesCanonicalStudioFields() {
        let status = AgentRuntimeStatus(["id": "codex", "installed": true, "source": "user-cli", "version": "1.2.3", "path": "/usr/bin/codex"])
        XCTAssertTrue(status.installed)
        XCTAssertEqual(status.source, "user-cli")
        XCTAssertEqual(status.version, "1.2.3")
    }

    func testCanonicalSessionOrganizationFields() {
        let session = SessionSummary(["id": "s2", "category_id": 7, "is_archived": 1, "preview": "matched text", "message_count": 12])
        XCTAssertEqual(session.categoryID, 7)
        XCTAssertTrue(session.archived)
        XCTAssertEqual(session.preview, "matched text")
        XCTAssertEqual(session.messageCount, 12)
    }

    func testWorkflowRunParsesBlockedApprovalNode() {
        let run = WorkflowRun(["id": "r1", "workflow_id": "w1", "status": "running", "node_sessions": [["id": "n1", "node_id": "review", "status": "blocked", "agent": "codex", "execution_id": "e1"]]])
        XCTAssertEqual(run.nodes.first?.status, "blocked")
        XCTAssertEqual(run.nodes.first?.executionID, "e1")
        XCTAssertEqual(run.nodes.first?.agent, "codex")
    }

    func testEkkoAndProviderCanonicalPayloads() {
        let memory = EkkoMemoryItem(["id": "m1", "title": "Preference", "content": "Arabic", "status": "active", "revision": 4, "tags": ["user"]])
        XCTAssertEqual(memory.revision, 4)
        XCTAssertEqual(memory.tags, ["user"])
        let provider = ProviderSummary(["provider": "groq", "label": "Groq", "models": ["m1", "m2"], "api_key": "stored", "model_refreshable": true])
        XCTAssertTrue(provider.credentialConfigured)
        XCTAssertTrue(provider.refreshable)
        XCTAssertEqual(provider.models.count, 2)
    }

    func testProfileRuntimeStatusParsesBridgeAndGateway() {
        let status = ProfileRuntime(["profile": "main", "bridge": ["running": true], "gateway": ["running": false, "url": "http://127.0.0.1:3000"]])
        XCTAssertTrue(status.bridgeRunning)
        XCTAssertFalse(status.gatewayRunning)
        XCTAssertEqual(status.gatewayURL, "http://127.0.0.1:3000")
    }

    func testStudioFileAndConnectionPayloads() {
        let file = StudioFileItem(["name": "notes.md", "path": "docs/notes.md", "isDir": false, "size": 42])
        XCTAssertEqual(file.path, "docs/notes.md")
        XCTAssertFalse(file.isDirectory)
        let relay = AppRelayInfo(["connected": true, "machineId": "machine", "pairingCode": "123456", "route": "official"])
        XCTAssertTrue(relay.connected)
        XCTAssertEqual(relay.pairingCode, "123456")
    }

    func testStudioDeviceUsesCanonicalDiscoveryFields() {
        let device = StudioDevice(["id": "d1", "computer_name": "Office Mac", "online": true, "inbound_status": "pending", "outbound_status": "none"])
        XCTAssertEqual(device.name, "Office Mac")
        XCTAssertEqual(device.inbound, "pending")
        XCTAssertTrue(device.online)
    }

    func testPeerConnectionUsesCanonicalDeviceFields() {
        let peer = PeerConnection(["id": "peer-1", "computer_name": "Desktop", "url": "https://desktop.local", "role": "client"])
        XCTAssertEqual(peer.name, "Desktop")
        XCTAssertEqual(peer.role, "client")
    }

    func testEkkoSkillDetailKeepsEditableContent() {
        let skill = EkkoSkillItem(["name": "research", "category": "workspace", "source": "profile", "enabled": true, "content": "# Skill"])
        XCTAssertEqual(skill.id, "research")
        XCTAssertEqual(skill.content, "# Skill")
        XCTAssertTrue(skill.enabled)
    }

    func testMarkdownParsesArabicHeadingsListsAndInlineBold() throws {
        let source = "### البريد غير المقروء\n- **635** عاجلة وتتطلب إجراء.\n  - طلبات معلومات.\n\n1. **تنظيف البريد**"
        let blocks = ChatMarkdownParser.parse(source)

        XCTAssertEqual(blocks[0], .heading(level: 3, text: "البريد غير المقروء"))
        XCTAssertEqual(blocks[1], .unordered(indent: 0, text: "**635** عاجلة وتتطلب إجراء."))
        XCTAssertEqual(blocks[2], .unordered(indent: 1, text: "طلبات معلومات."))
        XCTAssertEqual(blocks[3], .ordered(indent: 0, marker: "1.", text: "**تنظيف البريد**"))
        XCTAssertEqual(String(try XCTUnwrap(MarkdownText.attributed(blocks[1].text)).characters), "635 عاجلة وتتطلب إجراء.")
        XCTAssertEqual(MarkdownText.layoutDirection(for: source), .rightToLeft)
    }

    func testEnglishMarkdownKeepsLeftToRightDirection() {
        XCTAssertEqual(MarkdownText.layoutDirection(for: "1. **First task**"), .leftToRight)
    }

    // MARK: - M1: QR pairing and app-token refresh

    func testRefreshPolicyRefreshesWhenLessThanSevenDaysRemain() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let day: TimeInterval = 24 * 60 * 60
        XCTAssertTrue(AppTokenRefreshPolicy.shouldRefresh(expiresAt: now.addingTimeInterval(6 * day), lastRefresh: now.addingTimeInterval(-60), now: now))
        XCTAssertTrue(AppTokenRefreshPolicy.shouldRefresh(expiresAt: now.addingTimeInterval(-60), lastRefresh: now.addingTimeInterval(-60), now: now))
        XCTAssertFalse(AppTokenRefreshPolicy.shouldRefresh(expiresAt: now.addingTimeInterval(8 * day), lastRefresh: now.addingTimeInterval(-60), now: now))
    }

    func testRefreshPolicyRefreshesWhenLastRefreshIsOlderThanOneDay() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let hour: TimeInterval = 60 * 60
        let farExpiry = now.addingTimeInterval(30 * 24 * hour)
        XCTAssertTrue(AppTokenRefreshPolicy.shouldRefresh(expiresAt: farExpiry, lastRefresh: now.addingTimeInterval(-25 * hour), now: now))
        XCTAssertFalse(AppTokenRefreshPolicy.shouldRefresh(expiresAt: farExpiry, lastRefresh: now.addingTimeInterval(-23 * hour), now: now))
        XCTAssertTrue(AppTokenRefreshPolicy.shouldRefresh(expiresAt: farExpiry, lastRefresh: now.addingTimeInterval(hour), now: now), "a future last-refresh means the clock moved; refresh")
    }

    func testRefreshPolicyRefreshesWhenExpiryOrLastRefreshIsUnknown() {
        let now = Date()
        XCTAssertTrue(AppTokenRefreshPolicy.shouldRefresh(expiresAt: nil, lastRefresh: now, now: now))
        XCTAssertTrue(AppTokenRefreshPolicy.shouldRefresh(expiresAt: now.addingTimeInterval(30 * 24 * 3600), lastRefresh: nil, now: now))
    }

    func testPairingQRParsesCanonicalPayload() throws {
        let payload = #"{"type":"hermes-studio.app-connection","version":1,"connection_type":"lan","backend_url":"http://192.168.1.20:3000/","machine_id":"hwui_abc","authorization_code":"c29tZS1jb2Rl","expires_at":1800000600}"#
        let qr = try AppConnectionQR.parse(payload)
        XCTAssertEqual(qr.backendURL, "http://192.168.1.20:3000")
        XCTAssertEqual(qr.machineID, "hwui_abc")
        XCTAssertEqual(qr.authorizationCode, "c29tZS1jb2Rl")
        XCTAssertEqual(qr.connectionType, "lan")
        XCTAssertEqual(try XCTUnwrap(qr.expiresAt).timeIntervalSince1970, 1_800_000_600, accuracy: 0.001)
        XCTAssertFalse(qr.isExpired(now: Date(timeIntervalSince1970: 1_800_000_000)))
        XCTAssertTrue(qr.isExpired(now: Date(timeIntervalSince1970: 1_800_000_601)))
    }

    func testPairingQRRejectsForeignAndIncompletePayloads() {
        XCTAssertThrowsError(try AppConnectionQR.parse("https://example.com/not-json"))
        XCTAssertThrowsError(try AppConnectionQR.parse(#"{"type":"other","backend_url":"http://h:1","authorization_code":"x"}"#)) { error in
            XCTAssertEqual(error as? AppConnectionQR.ParseError, .wrongType("other"))
        }
        XCTAssertThrowsError(try AppConnectionQR.parse(#"{"type":"hermes-studio.app-connection","backend_url":"http://h:1"}"#)) { error in
            XCTAssertEqual(error as? AppConnectionQR.ParseError, .missingField("authorization_code"))
        }
        XCTAssertThrowsError(try AppConnectionQR.parse(#"{"type":"hermes-studio.app-connection","backend_url":"ftp://h","authorization_code":"x"}"#)) { error in
            XCTAssertEqual(error as? AppConnectionQR.ParseError, .invalidURL("ftp://h"))
        }
    }

    func testAppAuthResponseReadsTokenAndConnection() throws {
        let response = try AppAuthResponse(["token": "jwt", "userId": 7, "profiles": ["main", "ops"], "appConnection": ["id": 12, "device_code": "dev", "token_expires_at": 1_800_000_000]])
        XCTAssertEqual(response.token, "jwt")
        XCTAssertEqual(response.userID, 7)
        XCTAssertEqual(response.profiles, ["main", "ops"])
        XCTAssertEqual(response.connectionID, 12)
        XCTAssertEqual(try XCTUnwrap(response.tokenExpiresAt).timeIntervalSince1970, 1_800_000_000, accuracy: 0.001)
        XCTAssertThrowsError(try AppAuthResponse(["userId": 7]))
    }

    func testEpochDateAcceptsSecondsAndMilliseconds() {
        XCTAssertEqual(EpochDate.date(1_800_000_000)?.timeIntervalSince1970, 1_800_000_000)
        XCTAssertEqual(EpochDate.date(1_800_000_000_000)?.timeIntervalSince1970, 1_800_000_000)
        XCTAssertEqual(EpochDate.date("1800000000")?.timeIntervalSince1970, 1_800_000_000)
        XCTAssertNil(EpochDate.date(nil))
        XCTAssertNil(EpochDate.date(0))
    }

    func testAppSessionRecordRoundTripsThroughJSON() throws {
        let record = AppSessionRecord(token: "jwt", tokenExpiresAt: Date(timeIntervalSince1970: 1_800_000_000), connectionID: 3, deviceCode: "dev", lastRefresh: Date(timeIntervalSince1970: 1_799_000_000))
        let data = try JSONEncoder().encode(record)
        XCTAssertEqual(try JSONDecoder().decode(AppSessionRecord.self, from: data), record)
        XCTAssertTrue(record.needsRefresh(now: Date(timeIntervalSince1970: 1_799_600_000)))
        XCTAssertFalse(record.needsRefresh(now: Date(timeIntervalSince1970: 1_799_010_000)))
    }

    @MainActor func testPairingErrorMessagesDistinguishServerStatusCodes() {
        let messages = [400, 401, 403, 409, 410].map { AppStore.pairingErrorMessage(HermesError.http($0, "")) }
        XCTAssertEqual(Set(messages).count, messages.count, "every pairing failure needs its own explanation")
        XCTAssertEqual(AppStore.pairingErrorMessage(HermesError.http(500, "boom")), "HTTP 500: boom")
    }

    // MARK: - M1: server speech contract

    func testSttProfileStatusParsesServerFields() {
        let status = SttProfileStatus(["configured": false, "activeProvider": "browser", "reason": "browser_stt_not_available_for_mcu"])
        XCTAssertFalse(status.configured)
        XCTAssertEqual(status.activeProvider, "browser")
        XCTAssertEqual(status.reason, "browser_stt_not_available_for_mcu")
        XCTAssertFalse(status.message.isEmpty)
        let ready = SttProfileStatus(["configured": true, "activeProvider": "openai", "reason": NSNull()])
        XCTAssertTrue(ready.configured)
        XCTAssertEqual(ready.activeProvider, "openai")
    }

    func testTranscriptionParsesServerResponse() {
        let result = Transcription(["text": " hello there ", "provider": "openai", "model": "whisper-1", "language": "en", "durationMs": 1234])
        XCTAssertEqual(result.text, "hello there")
        XCTAssertEqual(result.provider, "openai")
        XCTAssertEqual(result.model, "whisper-1")
        XCTAssertEqual(result.language, "en")
        XCTAssertEqual(result.durationMs, 1234)
    }

    func testSttFormFieldsAlwaysCarryProviderAndOptionalLanguage() {
        XCTAssertEqual(APIClient.sttFormFields(provider: "openai", language: nil), ["provider": "openai"])
        XCTAssertEqual(APIClient.sttFormFields(provider: "openai", language: " "), ["provider": "openai"])
        XCTAssertEqual(APIClient.sttFormFields(provider: "local", language: "ar"), ["provider": "local", "language": "ar"])
    }

    @MainActor func testVoiceRecorderUsesSixteenKilohertzMonoPCM() {
        let settings = VoiceRecorder.wavSettings
        XCTAssertEqual(settings[AVFormatIDKey] as? Int, Int(kAudioFormatLinearPCM))
        XCTAssertEqual(settings[AVSampleRateKey] as? Double, 16_000)
        XCTAssertEqual(settings[AVNumberOfChannelsKey] as? Int, 1)
        XCTAssertEqual(settings[AVLinearPCMBitDepthKey] as? Int, 16)
        XCTAssertEqual(settings[AVLinearPCMIsFloatKey] as? Bool, false)
    }
}
