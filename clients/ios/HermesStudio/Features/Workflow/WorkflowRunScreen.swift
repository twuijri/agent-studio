import SwiftUI

/// `.workflowRun` pushed by value: the run `store.openRun` selected, with its
/// own live-status socket. Inside the workflow screen the run rows still push
/// the run view directly, sharing that screen's socket.
struct WorkflowRunScreen: View {
    @EnvironmentObject private var store: AppStore
    @StateObject private var live = WorkflowLiveStatuses()

    var body: some View {
        if let workflow = store.selectedWorkflow, let runID = store.selectedWorkflowRunID {
            WorkflowRunView(workflow: workflow, runID: runID, live: live)
        } else {
            WorkflowRootView()
        }
    }
}
