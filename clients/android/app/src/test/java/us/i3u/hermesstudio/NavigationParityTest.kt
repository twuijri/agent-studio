package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The two phones implement one navigation map (`docs/mobile/NAVIGATION.md`).
 *
 * Android's registry is `navigation/NavDestination.kt`; iOS's is
 * `clients/ios/HermesStudio/Core/NavDestination.swift`, written by the iOS
 * work from the same contract. Everything here is read out of the sources
 * and the contract, so moving one side without the other fails the build:
 *
 *  (a) both enums declare the same case names;
 *  (b) every `nav_*` label is the same English and the same Arabic on both;
 *  (c) every term in the contract's naming table is a `nav_*` value;
 *  (d) every destination has exactly one primary entry, drawn from the
 *      registry's own lists in one place each;
 *  (e) no `Screen` exists without a destination, and no destination names a
 *      screen that does not exist.
 *
 * The iOS files may not exist yet when this runs on a branch that only
 * carries the Android half; those checks are then skipped, loudly.
 */
class NavigationParityTest {

    private val src = File("src/main/java/us/i3u/hermesstudio")
    private val registry = File(src, "navigation/NavDestination.kt").readText()
    private val viewModel = File(src, "AppViewModel.kt").readText()
    private val contract = File("../../../docs/mobile/NAVIGATION.md").readText()
    private val iosRegistry = File("../../ios/HermesStudio/Core/NavDestination.swift")
    private val iosEnglish = File("../../ios/HermesStudio/Resources/en.lproj/Localizable.strings")
    private val iosArabic = File("../../ios/HermesStudio/Resources/ar.lproj/Localizable.strings")

    /** The 37 cases the contract's registry has, in declaration order. */
    private val expectedCases = listOf(
        "newChat", "search", "deviceConnections", "agentManager", "models", "chat", "groupChat", "workflow", "history",
        "settings", "logs", "usage", "performance", "skillsUsage", "theme", "pets", "profiles", "conversation", "room",
        "workflowDetail", "workflowRun", "agentHermes", "agentEkko", "agentCoding", "jobs", "kanban", "channels", "skills",
        "plugins", "presets", "mcp", "memory", "journey", "hermesSettings", "ekkoSettings", "codingAgentSettings", "globalAgent", "files",
    )

    /** `caseName(R.string.nav_key, setOf(Screen.A, Screen.B))` → (case, key, screens). */
    private data class Case(val name: String, val key: String, val screens: List<String>)

    private val androidCases: List<Case> = Regex("""^\s{4}(\w+)\(R\.string\.(nav_\w+)(?:,\s*setOf\(([^)]*)\))?\)""", RegexOption.MULTILINE)
        .findAll(registry.substringAfter("enum class NavDestination").substringBefore("companion object"))
        .map { match ->
            Case(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3].split(",").map { it.trim().removePrefix("Screen.") }.filter { it.isNotBlank() },
            )
        }
        .toList()

    private fun androidStrings(path: String): Map<String, String> {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as org.w3c.dom.Element
            element.getAttribute("name") to element.textContent
        }
    }

    /** `"nav_key" = "value";` lines of a `.strings` file. */
    private fun iosStrings(file: File): Map<String, String> =
        Regex(""""(nav_\w+)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;""").findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\\"", "\"") }

    private fun snake(camel: String) = camel.replace(Regex("([a-z0-9])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2].lowercase()}" }

    @Test
    fun theAndroidRegistryIsTheContractsRegistry() {
        assertEquals("NavDestination cases, in order", expectedCases, androidCases.map { it.name })
        androidCases.forEach { case ->
            assertEquals("label key of ${case.name}", "nav_${snake(case.name)}", case.key)
        }
    }

    @Test
    fun bothPhonesDeclareTheSameDestinations() {
        assumeTrue("iOS NavDestination.swift does not exist yet; the iOS half of the contract is pending", iosRegistry.isFile)
        val swift = iosRegistry.readText().substringAfter("enum NavDestination")
        val body = swift.substringAfter("{")
        val iosCases = Regex("""^\s*case\s+([\w\s,]+?)\s*$""", RegexOption.MULTILINE).findAll(body)
            .flatMap { match -> match.groupValues[1].split(",").map { it.trim() }.filter { it.isNotBlank() } }
            .map { it.substringBefore("=").trim() }
            .toList()
        assertEquals("iOS and Android must declare the same NavDestination cases", androidCases.map { it.name }.toSet(), iosCases.toSet())
    }

    @Test
    fun everyNavLabelReadsTheSameOnBothPhones() {
        assumeTrue("iOS Localizable.strings do not exist yet", iosEnglish.isFile && iosArabic.isFile)
        val iosEn = iosStrings(iosEnglish)
        val iosAr = iosStrings(iosArabic)
        assumeTrue("iOS has no nav_* keys yet; the iOS half of the contract is pending", iosEn.isNotEmpty())
        val en = androidStrings("src/main/res/values/strings.xml")
        val ar = androidStrings("src/main/res/values-ar/strings.xml")
        androidCases.forEach { case ->
            assertEquals("English ${case.key}", en[case.key], iosEn[case.key])
            assertEquals("Arabic ${case.key}", ar[case.key], iosAr[case.key])
        }
    }

    @Test
    fun everyTermOfTheNamingTableIsANavLabel() {
        val en = androidStrings("src/main/res/values/strings.xml")
        val ar = androidStrings("src/main/res/values-ar/strings.xml")
        val navKeys = androidCases.map { it.key }.toSet()
        val englishValues = en.filterKeys { it in navKeys }.values.toSet()
        val arabicValues = ar.filterKeys { it in navKeys }.values.toSet()
        val table = contract.substringAfter("المصطلحات المعتمدة").substringBefore("## ١)")
        // The table wraps across lines inside the Markdown; a term never does.
        val pairs = Regex("""`([^`]+)`/\s*«([^»]+)»""").findAll(table)
            .map { it.groupValues[1] to it.groupValues[2].replace(Regex("\\s+"), " ").trim() }.toList()
        assertTrue("the contract's naming table was not found", pairs.size >= 17)
        pairs.forEach { (english, arabic) ->
            assertTrue("English term \"$english\" is not a nav_* value", english in englishValues)
            assertTrue("Arabic term \"$arabic\" is not a nav_* value", arabic in arabicValues)
        }
        // Both label files carry every registry key, non-blank.
        navKeys.forEach { key ->
            assertTrue("missing English $key", en[key]?.isNotBlank() == true)
            assertTrue("missing Arabic $key", ar[key]?.isNotBlank() == true)
        }
    }

    /** The lists the registry publishes, parsed from its source. */
    private fun registryList(name: String): List<String> =
        Regex("""val $name[^=]*=\s*listOf\(([^)]*)\)""").find(registry)!!.groupValues[1]
            .split(",").map { it.trim() }.filter { it.isNotBlank() }

    private fun agentSections(kind: String): List<String> =
        Regex("""AgentKind\.$kind -> listOf\(([^)]*)\)""").find(registry)!!.groupValues[1]
            .split(",").map { it.trim() }.filter { it.isNotBlank() }

    private val uiSources: Map<String, String> = src.walkTopDown()
        .filter { it.extension == "kt" && it.name != "AppViewModel.kt" && it.name != "NavDestination.kt" }
        .associate { it.relativeTo(src).path to it.readText() }

    private fun uiCount(needle: String) = uiSources.values.sumOf { text -> Regex.escape(needle).toRegex().findAll(text).count() }

    @Test
    fun everyDestinationHasExactlyOnePrimaryEntry() {
        // Menu-level destinations come from the registry lists, in the contract's order …
        assertEquals(listOf("newChat", "search", "deviceConnections", "agentManager", "models"), registryList("rail"))
        assertEquals(listOf("chat", "groupChat", "workflow", "history"), registryList("segments"))
        assertEquals(listOf("logs", "usage", "performance", "skillsUsage", "theme", "pets", "profiles"), registryList("settingsTools"))
        assertEquals(listOf("jobs", "kanban", "channels", "skills", "plugins", "mcp", "memory", "journey", "hermesSettings"), agentSections("Hermes"))
        assertEquals(listOf("memory", "skills", "mcp", "ekkoSettings"), agentSections("BuiltIn"))
        assertEquals(listOf("skills", "mcp", "codingAgentSettings"), agentSections("Coding"))
        // The DeepSeek Harness alone prepends Plugins · Presets (`CodingAgentConfigSidebar.vue:19-24`).
        assertEquals(listOf("plugins", "presets"), registryList("dshSections"))
        assertTrue(registry.contains("AgentKind.Coding -> listOf(skills, mcp, codingAgentSettings).let { if (agentId == DSH_AGENT_ID) dshSections + it else it }"))
        // … and no destination sits in two of them (Skills, MCP and Memory
        // exist once per agent, which is one entry each under that agent).
        val appLevel = registryList("rail") + registryList("segments") + registryList("settingsTools")
        assertEquals("a destination must have one app-level entry", appLevel.size, appLevel.toSet().size)
        listOf("Hermes", "BuiltIn", "Coding").forEach { kind ->
            val sections = agentSections(kind)
            assertEquals("$kind lists a section twice", sections.size, sections.toSet().size)
            assertTrue("$kind sections must not repeat an app-level entry", sections.none { it in appLevel })
        }
        assertTrue("dsh sections must not repeat an app-level entry", registryList("dshSections").none { it in appLevel })
        assertTrue("Presets exists under dsh only", "presets" !in appLevel && listOf("Hermes", "BuiltIn", "Coding").none { "presets" in agentSections(it) })

        // Each list is drawn by exactly one composable.
        assertEquals("the rail is drawn once, in the drawer", 1, uiCount("NavDestination.rail"))
        assertEquals("the Tools section is drawn once, in Settings", 1, uiCount("NavDestination.settingsTools"))
        assertEquals("agent sections are drawn once, under the agent", 1, uiCount("NavDestination.agentSections("))
        listOf("chat", "groupChat", "workflow", "history").forEach { segment ->
            assertEquals("segment $segment is one switch item", 1, uiCount("NavDestination.$segment.labelKey"))
        }
        // The card is the only door into an agent; the footer gear the only one into Settings.
        assertEquals(1, uiCount("viewModel.openAgent("))
        assertEquals(1, uiCount("viewModel.openSettings()"))
        // Files: a profile card's "Edit config" only. Global Agent: the banner only (search results open sessions).
        assertEquals(1, uiCount("viewModel.openProfileConfig("))
        assertEquals(1, uiCount("viewModel.openGlobalAgent()"))

        // Nothing opens these screens behind the registry's back.
        listOf(
            "openLogs()", "openUsage(", "openPerformance()", "openSkillsUsage(", "openTheme()", "openPets()", "openProfiles()",
            "openCronJobs()", "openKanban()", "openChannels()", "openSkills(", "openPlugins()", "openMcp(", "openHermesMemory()",
            "openJourney()", "openHermesSettings(", "openEkkoMemory()", "openEkkoSkills()", "openEkkoMcp()", "openEkkoSettings()",
            "openAgentSettings(", "openDshPlugins()", "openDshPresets()", "openConnections()", "openAgentManager()", "openModels()", "openSearch()", "openInsights", "openAppearance",
            "openSettingsGroup", "openSettingsPage", "openAgentRuntimes", "openEkkoHub", "openWebhooks", "openRuntimeVersions",
        ).forEach { call ->
            val offenders = uiSources.filterValues { it.contains("viewModel.$call") || it.contains("vm.$call") }.keys
                // A screen may refresh itself with its own opener (Connections, Ekko settings).
                .filterNot { (call == "openConnections()" || call == "openLogs()") && it.endsWith("StudioWorkspaceScreens.kt") }
                .filterNot { call == "openEkkoSettings()" && it.endsWith("EkkoScreens.kt") }
                // The one secondary entry the web owns: Settings › Models says
                // "Open the Models page" (NAVIGATION.md §2), because the key form
                // and the Models page share a word.
                .filterNot { call == "openModels()" && it.endsWith("SettingsScreen.kt") }
            assertEquals("$call must be reached through the registry only", emptySet<String>(), offenders.toSet())
        }
    }

    @Test
    fun everyScreenHasADestinationAndEveryDestinationExists() {
        val screens = viewModel.substringAfter("enum class Screen {").substringBefore("}")
            .split(",").map { it.trim().substringBefore("//").trim() }.filter { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() } }
        val covered = androidCases.flatMap { it.screens }.toSet()
        // System screens, and children only ever reached from their parent list.
        val children = setOf("Loading", "Onboarding", "Login", "CronJob", "CronHistory", "KanbanTask", "Channel", "Skill")
        assertEquals("screens without a destination", emptySet<String>(), (screens.toSet() - children - covered))
        assertEquals("destinations naming a screen that does not exist", emptySet<String>(), covered - screens.toSet())
        // Every screen `AppContent` can show is a Screen value, and every Screen value is shown.
        val activity = File(src, "MainActivity.kt").readText().substringAfter("private fun AppContent").substringBefore("// ── login")
        screens.forEach { screen -> assertTrue("AppContent does not render Screen.$screen", activity.contains("Screen.$screen")) }
        // The screens of the old, duplicated destinations are gone for good.
        listOf("SettingsPage", "SettingsGroup", "Insights", "AgentRuntimes", "EkkoHub", "Webhooks", "RuntimeVersions", "Appearance", "AgentHub")
            .forEach { old -> assertFalse("Screen.$old must not come back", old in screens) }
        assertFalse(File(src, "ui/navigation/SettingsDrawerScreen.kt").exists())
        assertFalse(File(src, "AgentRuntimeScreen.kt").exists())
    }
}
