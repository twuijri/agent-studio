import XCTest
@testable import HermesStudio

/// The pure logic behind the screens the navigation refactor added or
/// split: the search sheet, the Performance snapshot, the
/// skills target, and the STT/auxiliary payloads of the Models page.
final class NavigationScreensTests: XCTestCase {

    // MARK: - Search sheet (`SessionSearchModal.vue`)

    func testSearchShowsRecentSessionsUntilThereIsAQuery() {
        let recent = [SessionSummary(["id": "a", "title": "Alpha", "preview": "first words"]), SessionSummary(["id": "b", "title": "Beta"])]
        let results = [SessionSearchResult(["id": "c", "title": "Gamma", "snippet": "hit"])]
        XCTAssertEqual(SessionSearchModel.items(query: "  ", recent: recent, results: results).map(\.id), ["a", "b"])
        XCTAssertEqual(SessionSearchModel.items(query: "", recent: recent, results: results).first?.snippet, "first words")
        XCTAssertEqual(SessionSearchModel.items(query: "ga", recent: recent, results: results).map(\.id), ["c"])
        XCTAssertFalse(SessionSearchModel.hasQuery("\n"))
        XCTAssertTrue(SessionSearchModel.hasQuery("x"))
    }

    func testGlobalAgentResultsOpenTheGlobalAgentConversation() {
        XCTAssertTrue(SessionSearchModel.opensGlobalAgent(SessionSummary(["id": "g", "source": "global_agent"])))
        XCTAssertFalse(SessionSearchModel.opensGlobalAgent(SessionSummary(["id": "c", "source": "cli"])))
        XCTAssertEqual(SessionSearchModel.sourceLabel("global_agent"), NavDestination.globalAgent.title)
        XCTAssertEqual(SessionSearchModel.sourceLabel("weixin"), "WeChat")
        XCTAssertEqual(SessionSearchModel.sourceLabel("api_server"), "API Server")
        XCTAssertEqual(SessionSearchModel.sourceLabel("custom"), "custom")
    }

    func testSearchTitleFallsBackToPreviewThenId() {
        XCTAssertEqual(SessionSearchModel.title(of: SessionSummary(["id": "s1", "title": "  ", "preview": "hello"])), "hello")
        XCTAssertEqual(SessionSearchModel.title(of: SessionSummary(["id": "s2", "title": " ", "preview": " "])), "s2")
        XCTAssertEqual(SessionSearchModel.recentLimit, 8)
        XCTAssertEqual(SessionSearchModel.resultLimit, 10)
    }

    // MARK: - Performance (`GET /api/studio/performance/runtime`)

    func testPerformanceSnapshotReadsTheRuntimePayload() {
        let snapshot = PerformanceSnapshot([
            "timestamp": 1_700_000_000_000,
            "system": ["platform": "linux", "arch": "x64", "uptimeSeconds": 90_061, "cpuCount": 4, "cpuPercent": 12.5, "memoryPercent": 40.25],
            "bridge": [
                "reachable": true,
                "broker": ["running": true, "ready": true],
                "workers": [
                    ["pid": 41, "profile": "main", "running": true, "cpuPercent": 3.5, "memoryRssBytes": 2_048, "sessionCount": 3, "runningSessionCount": 1],
                    ["pid": 42, "profile": "ops", "running": false, "cpuPercent": 0, "memoryRssBytes": 0, "sessionCount": 0, "runningSessionCount": 0, "error": "exited"],
                ],
                "totalWorkerMemoryRssBytes": 2_048,
            ],
            "sessions": ["active": 3, "running": 1, "byProfile": ["main": 2, "ops": 1]],
        ])
        XCTAssertEqual(snapshot.platform, "linux")
        XCTAssertEqual(snapshot.cpuCount, 4)
        XCTAssertEqual(snapshot.cpuPercent, 12.5)
        XCTAssertEqual(snapshot.memoryPercent, 40.25)
        XCTAssertTrue(snapshot.bridgeReachable)
        XCTAssertTrue(snapshot.brokerRunning)
        XCTAssertEqual(snapshot.workers.count, 2)
        XCTAssertEqual(snapshot.runningWorkers, 1)
        XCTAssertEqual(snapshot.workers.last?.error, "exited")
        XCTAssertEqual(snapshot.activeSessions, 3)
        XCTAssertEqual(snapshot.sessionsByProfile.map(\.profile), ["main", "ops"])
        XCTAssertEqual(PerformanceFormat.duration(snapshot.uptimeSeconds), "1d 1h")
        XCTAssertEqual(PerformanceFormat.duration(3_720), "1h 2m")
        XCTAssertEqual(PerformanceFormat.duration(59), "0m")
    }

    /// The empty snapshot the server returns after a read failure has no
    /// `cpuPercent`; it must show as a dash, not as 0 %.
    func testPerformanceSnapshotKeepsMissingPercentagesNil() {
        let snapshot = PerformanceSnapshot(["system": ["platform": "darwin", "cpuPercent": NSNull()], "bridge": ["reachable": false, "error": "down"], "sessions": [:]])
        XCTAssertNil(snapshot.cpuPercent)
        XCTAssertNil(snapshot.memoryPercent)
        XCTAssertEqual(PerformanceFormat.percent(snapshot.cpuPercent), "—")
        XCTAssertEqual(snapshot.bridgeError, "down")
        XCTAssertEqual(snapshot.workers, [])
    }

    func testUsageFormatCompactsLargeNumbers() {
        XCTAssertEqual(UsageFormat.compact(999), "999")
        XCTAssertEqual(UsageFormat.compact(1_500), "1.5K")
        XCTAssertEqual(UsageFormat.compact(2_300_000), "2.3M")
    }

    // MARK: - Skills per agent (`api/hermes/skills.ts` `target`)

    func testSkillsTargetQueryIsOmittedForHermes() {
        XCTAssertEqual(APIClient.skillsTargetQuery("hermes"), "")
        XCTAssertEqual(APIClient.skillsTargetQuery(""), "")
        XCTAssertEqual(APIClient.skillsTargetQuery("claude"), "&target=claude")
        XCTAssertEqual(APIClient.skillsTargetQuery("DSH"), "&target=dsh")
    }

    // MARK: - Models page tabs

    func testSttSettingsAcceptBothEnvelopesAndNullActiveProvider() {
        let settings = SttSettings(["settings": [["provider": "openai", "settings": ["model": "whisper-1", "language": "ar"], "secrets": ["apiKey": "[stored]"]], ["provider": "bogus"]], "activeProvider": NSNull()])
        XCTAssertEqual(settings.providers.map(\.provider), ["openai"])
        XCTAssertEqual(settings.providers.first?.detail, "whisper-1 · ar")
        XCTAssertEqual(settings.providers.first?.hasStoredKey, true)
        XCTAssertNil(settings.activeProvider)
        XCTAssertEqual(settings.selectableProviders, ["openai", "browser", "local"])

        let active = SttSettings(["providers": [], "activeProvider": "groq"])
        XCTAssertEqual(active.activeProvider, "groq")
        XCTAssertEqual(active.selectableProviders, ["browser", "local", "groq"])
    }

    func testAuxiliaryModelsReadTasksAndTheirConfiguration() {
        let models = AuxiliaryModels(["tasks": [["key": "vision", "label": "Vision"], ["key": "", "label": "dropped"]], "auxiliary": ["vision": ["provider": "openai", "model": "gpt-4o"]]])
        XCTAssertEqual(models.tasks.map(\.id), ["vision"])
        XCTAssertEqual(models.provider(of: "vision"), "openai")
        XCTAssertEqual(models.model(of: "vision"), "gpt-4o")
        XCTAssertEqual(models.model(of: "missing"), "")
    }

    func testModelEnsembleSlotLabelJoinsProviderModelAndEffort() {
        XCTAssertEqual(ModelEnsemblesView.slotLabel(["provider": "openai", "model": "gpt-5", "reasoning_effort": "high"]), "openai/gpt-5 · high")
        XCTAssertEqual(ModelEnsemblesView.slotLabel(["model": "solo"]), "solo")
    }

    func testDshPresetAndPluginFlagsDecode() {
        let preset = DshPreset(["id": "default", "trust": "system", "isDefault": true, "broken": NSNull()])
        XCTAssertEqual(preset.name, "default")
        XCTAssertTrue(preset.isDefault)
        XCTAssertEqual(preset.broken, "")
        XCTAssertEqual(DshPluginsView.enabledLabel("conditional"), String(localized: "Conditional"))
        XCTAssertEqual(DshPluginsView.enabledLabel(true), String(localized: "Enabled"))
        XCTAssertEqual(DshPluginsView.enabledLabel(nil), String(localized: "Disabled"))
    }
}
