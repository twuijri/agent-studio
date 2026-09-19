package us.i3u.hermesstudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The segmented bar picks what the drawer's list shows; it is not a set of
 * four links out of the drawer.
 *
 * On iOS `AppStore.switchMode` swaps the list under the bar and leaves the
 * drawer open (only History, a page of its own, closes it). Android used to
 * wrap the same call in `go { }`, which closed the drawer, and then always
 * rendered the session list underneath whichever segment was selected — so
 * "Group Chat" shut the drawer, jumped to the Groups page, and still showed
 * chat sessions if you opened the drawer again. None of that is visible in a
 * unit-test run, so it is pinned here.
 */
class DrawerSegmentBehaviourTest {

    private val src = File("src/main/java/us/i3u/hermesstudio")
    private val drawer = File(src, "ui/navigation/CoreHubDrawer.kt").readText()
    private val shell = File(src, "ui/navigation/HomeShell.kt").readText()
    private val viewModel = File(src, "AppViewModel.kt").readText()

    /** Chat and Group Chat and Workflow keep the drawer open; History closes it. */
    @Test
    fun theSegmentSwitchesTheListWithoutClosingTheDrawer() {
        assertTrue(
            "the segment must not be wrapped in go { }, which closes the drawer",
            drawer.contains("if (tab == Tab.History) go { viewModel.showTab(tab) } else viewModel.showTab(tab)"),
        )
        assertFalse("the old closing handler is gone", drawer.contains("{ tab -> go { viewModel.showTab(tab) } }"))
    }

    /** Each segment shows its own list, in place, under the same header. */
    @Test
    fun eachSegmentRendersItsOwnList() {
        listOf(
            "Tab.Group -> DrawerRoomList(",
            "Tab.Workflow -> DrawerWorkflowList(",
            "Tab.Chat, Tab.History -> SessionListPane(",
        ).forEach { branch -> assertTrue("the drawer list is missing: $branch", drawer.contains(branch)) }
        // A row is still what navigates.
        assertTrue(drawer.contains("go { viewModel.openRoom(room) }"))
        assertTrue(drawer.contains("go { viewModel.openWorkflow(workflow) }"))
        assertTrue(drawer.contains("go { viewModel.showTab(Tab.Chat); viewModel.openSession(session) }"))
    }

    /**
     * Every section builds its own `HomeShell`, so a shell-local open flag
     * was thrown away the moment the segment changed the screen.
     */
    @Test
    fun theOpenFlagLivesOnTheViewModelNotInTheShell() {
        assertTrue(viewModel.contains("val drawerOpen: Boolean = false"))
        assertTrue(viewModel.contains("fun setDrawerOpen(open: Boolean)"))
        assertTrue(shell.contains("open = state.drawerOpen"))
        assertTrue(shell.contains("onOpenChange = viewModel::setDrawerOpen"))
        assertFalse("a shell-local flag resets on every navigation", shell.contains("rememberSaveable { mutableStateOf(false) }"))
    }

    /** The header over the list names the section the segment selected. */
    @Test
    fun theSectionHeaderFollowsTheSelectedSegment() {
        assertTrue(drawer.contains("DrawerSectionHeader(stringResource(R.string.segment_group_chat), state.rooms.size)"))
        assertTrue(drawer.contains("DrawerSectionHeader(stringResource(R.string.segment_workflow), state.workflows.size)"))
        // Group Chat carries its own two actions, as it does on iOS.
        assertTrue(drawer.contains("DrawerSectionAction(CoreHubIcons.NewChat, stringResource(R.string.groups_new))"))
        assertTrue(drawer.contains("DrawerSectionAction(CoreHubIcons.Link, stringResource(R.string.room_join_title))"))
    }
}
