package us.i3u.hermesstudio.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.OutlinedTextFieldDefaults
import us.i3u.hermesstudio.*
import us.i3u.hermesstudio.ui.chat.*
import us.i3u.hermesstudio.ui.navigation.*
import us.i3u.hermesstudio.ui.sessions.*
import us.i3u.hermesstudio.ui.settings.*
import us.i3u.hermesstudio.ui.theme.*

/** The avatar Studio shows for a profile, or null when it is not loaded yet. */
internal fun UiState.avatarOf(profile: String?): AvatarSpec? {
    val name = profile?.ifBlank { null } ?: activeProfile
    return profiles.firstOrNull { it.name == name }?.avatar
}

// ── conversation ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(state: UiState, viewModel: AppViewModel, onMenu: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var actionLine by remember { mutableStateOf<ChatLine?>(null) }
    var replyingTo by remember { mutableStateOf<ChatLine?>(null) }
    val conversationKey = state.openSession?.id ?: "new"
    var reachedInitialBottom by remember(conversationKey) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var clarification by rememberSaveable(state.pendingRunAction?.id) { mutableStateOf("") }

    state.pendingRunAction?.let { action ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(if (action.kind == RequiredAction.Approval) R.string.run_approval_title else R.string.run_clarification_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(action.prompt.ifBlank { stringResource(if (action.kind == RequiredAction.Approval) R.string.run_requires_approval else R.string.run_requires_clarification) })
                    if (action.kind == RequiredAction.Approval && action.options.count { it != "deny" } > 1) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            action.options.filter { it != "deny" }.forEach { choice ->
                                AssistChip(
                                    onClick = { viewModel.resolveRunAction(choice) },
                                    label = { Text(stringResource(when (choice) {
                                        "session" -> R.string.approval_session
                                        "always" -> R.string.approval_always
                                        else -> R.string.approval_once
                                    })) },
                                )
                            }
                        }
                    }
                    if (action.kind == RequiredAction.Clarification) {
                        if (action.options.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                action.options.forEach { choice ->
                                    AssistChip(onClick = { clarification = choice }, label = { Text(choice) })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = clarification,
                            onValueChange = { clarification = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.run_clarification_answer)) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = action.kind == RequiredAction.Approval || clarification.isNotBlank(),
                    onClick = { viewModel.resolveRunAction(if (action.kind == RequiredAction.Approval) action.options.firstOrNull() ?: "once" else clarification.trim()) },
                ) { Text(stringResource(if (action.kind == RequiredAction.Approval) R.string.action_approve else R.string.action_send)) }
            },
            dismissButton = if (action.kind == RequiredAction.Approval) {
                { TextButton(onClick = { viewModel.resolveRunAction(action.options.firstOrNull { it == "deny" } ?: "deny") }) { Text(stringResource(R.string.action_reject)) } }
            } else null,
        )
    }

    // A run continues in Studio after the mobile stream is detached. Reload
    // the server history whenever the app returns to the foreground so a reply
    // completed while the user was away is shown immediately.
    DisposableEffect(lifecycleOwner, conversationKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.openSession != null) {
                viewModel.refreshConversation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(conversationKey, state.loadingHistory, state.lines.size) {
        if (!state.loadingHistory && state.lines.isNotEmpty()) {
            val last = state.lines.lastIndex
            if (!reachedInitialBottom) {
                // A huge offset is intentionally clamped by LazyColumn to the
                // real end, including when the final message is taller than the
                // viewport. Animation from the first message made old chats
                // appear to open at the top.
                listState.scrollToItem(last, Int.MAX_VALUE / 2)
                reachedInitialBottom = true
            } else {
                listState.animateScrollToItem(last, Int.MAX_VALUE / 2)
            }
        }
    }

    val profile = state.openSession?.profile ?: state.activeProfile
    val avatar = state.avatarOf(profile)
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.loadingHistory,
        onRefresh = { viewModel.refreshConversation() },
    )
    actionLine?.let { line ->
        ModalBottomSheet(
            onDismissRequest = { actionLine = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            SheetTitle(stringResource(R.string.message_actions))
            TextButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = {
                    clipboard.setText(AnnotatedString(line.text))
                    actionLine = null
                },
            ) { Text(stringResource(R.string.message_copy), modifier = Modifier.fillMaxWidth()) }
            TextButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = { replyingTo = line; actionLine = null },
            ) { Text(stringResource(R.string.message_reply), modifier = Modifier.fillMaxWidth()) }
            TextButton(
                enabled = !state.sending,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = { actionLine = null; viewModel.send("/fork") },
            ) { Text(stringResource(R.string.message_fork), modifier = Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(18.dp))
        }
    }
    Scaffold(
        // The composer applies the IME inset itself. Scaffold's default system
        // bottom inset would otherwise be added above the keyboard as a second,
        // empty navigation-bar-sized strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { ChatHeader(state, viewModel, onMenu) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().pullRefresh(pullRefreshState),
            ) {
                if (state.lines.isEmpty() && !state.loadingHistory) {
                    Text(
                        stringResource(
                            R.string.conversation_empty,
                            profile.ifBlank { stringResource(R.string.conversation_your_agent) },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                LazyColumn(
                    state = listState,
                        modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.lines) { line ->
                        MessageBubble(
                            line = line,
                            profile = profile.ifBlank { "default" },
                            avatar = avatar,
                            onActions = { actionLine = line },
                            onDownload = { file ->
                                viewModel.downloadChatFile(file, profile.ifBlank { "default" })
                            },
                        )
                    }
                }
                }
                PullRefreshIndicator(
                    refreshing = state.loadingHistory,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                if (reachedInitialBottom && listState.canScrollForward && state.lines.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(46.dp)
                            .clickable {
                                scope.launch {
                                    listState.animateScrollToItem(state.lines.lastIndex, Int.MAX_VALUE / 2)
                                }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 5.dp,
                        shadowElevation = 5.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.conversation_jump_latest),
                            )
                        }
                    }
                }
            }

            if (state.sending && state.lines.none { it.streaming }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp))
                    Text(
                        state.activity?.let { stringResource(R.string.conversation_tool, it) }
                            ?: stringResource(R.string.conversation_thinking),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (state.queuedRuns.isNotEmpty() || state.backgroundAgentRuns.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.queuedRuns.forEach { queued ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.queued_run_summary, queued.position, queued.preview), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                IconButton(onClick = { viewModel.insertQueuedRun(queued.id) }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.queued_run_insert)) }
                                IconButton(onClick = { viewModel.cancelQueuedRun(queued.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.queued_run_cancel)) }
                            }
                        }
                    }
                    state.backgroundAgentRuns.forEach { agent ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(stringResource(R.string.background_agent_summary, agent.label, agent.status), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }

            replyingTo?.let { quoted ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.message_replying), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(quoted.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { replyingTo = null }) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel)) }
                    }
                }
            }
            Composer(
                state = state,
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    viewModel.send(replyingTo?.let { quoteForReply(it.text, draft) } ?: draft)
                    draft = ""
                    replyingTo = null
                },
                viewModel = viewModel,
            )
        }
    }
}

/**
 * The chat header, per DESIGN-SPEC: hamburger, title 16/600 (dir=auto) in the
 * app bar, the workspace chip (folder icon 12, 11 sp muted, last path segment)
 * and the runtime chip under it, and the ⋯ actions menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatHeader(state: UiState, viewModel: AppViewModel, onMenu: () -> Unit) {
    val palette = CoreHub.palette
    var menuOpen by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var workspace by remember { mutableStateOf(false) }
    val session = state.openSession
    session?.let { open ->
        if (rename) TextPromptDialog(
            title = stringResource(R.string.chats_rename_title),
            initial = open.title,
            hint = open.title,
            action = stringResource(R.string.action_rename),
            onConfirm = { viewModel.renameSession(open, it); rename = false },
            onDismiss = { rename = false },
        )
        if (workspace) TextPromptDialog(
            title = stringResource(R.string.session_workspace),
            initial = open.workspace.orEmpty(),
            hint = "/workspace",
            action = stringResource(R.string.action_save),
            onConfirm = { viewModel.setSessionWorkspace(open, it); workspace = false },
            onDismiss = { workspace = false },
        )
    }
    TopAppBar(
        title = {
            Column {
                Text(
                    session?.title ?: stringResource(R.string.action_new_chat),
                    style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    workspaceChipLabel(session?.workspace)?.let { label -> HeaderChip(label, CoreHubIcons.Folder) }
                    HeaderChip(state.selectedRuntime.name, null)
                }
            }
        },
        navigationIcon = { MenuButton(onMenu) },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(CoreHubIcons.More, stringResource(R.string.message_actions), tint = palette.textSecondary) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_refresh)) }, onClick = { menuOpen = false; viewModel.refreshConversation() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_new_chat)) }, onClick = { menuOpen = false; viewModel.startNewConversation() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.message_fork)) }, enabled = !state.sending, onClick = { menuOpen = false; viewModel.send("/fork") })
                    if (session != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuOpen = false; rename = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.session_workspace)) }, onClick = { menuOpen = false; workspace = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.session_export)) }, onClick = { menuOpen = false; viewModel.exportSession(session) })
                        DropdownMenuItem(text = { Text(stringResource(if (session.archived) R.string.session_unarchive else R.string.session_archive)) }, onClick = { menuOpen = false; viewModel.archiveSession(session) })
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bgPrimary, scrolledContainerColor = palette.bgPrimary),
    )
}

/** Workspace chip: folder icon 12, 11/16 muted, bg text @ 5 %, padding 2×8, radius 4. */
@Composable
private fun HeaderChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .background(palette.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(CoreHubTokens.Radius.tag))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(CoreHubTokens.Metrics.workspaceIcon))
        Text(
            label,
            style = CoreHubTextStyles.meta.copy(textDirection = TextDirection.Content),
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MessageBubble(
    line: ChatLine,
    profile: String? = null,
    avatar: AvatarSpec? = null,
    onActions: (() -> Unit)? = null,
    onDownload: ((ChatFileLink) -> Unit)? = null,
) {
    val parsed = remember(line.text, onDownload != null) {
        if (onDownload == null) ParsedChatMessage(line.text, emptyList()) else parseChatMessage(line.text)
    }
    val alignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val hasThinking = !line.fromUser && (
        line.streaming || line.reasoning?.isNotBlank() == true || line.tools.isNotEmpty()
    )
    val wide = !line.fromUser || hasThinking || parsed.files.isNotEmpty()
    val palette = CoreHub.palette
    val container = when {
        line.isError -> palette.errorSurface
        line.fromUser -> palette.msgUser
        else -> palette.msgAssistant
    }
    // user: max 75 %; assistant: max 80 % (DESIGN-SPEC message row).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        val maxBubble = maxWidth * (if (line.fromUser) 0.75f else 0.80f)
        Row(
            modifier = (if (wide) Modifier.fillMaxWidth() else Modifier).widthIn(max = if (wide) androidx.compose.ui.unit.Dp.Unspecified else maxBubble),
            verticalAlignment = if (wide) Alignment.Top else Alignment.Bottom,
        ) {
            // The agent's picture rides with its own replies, the way Studio
            // shows it in the transcript.
            if (!line.fromUser && !profile.isNullOrBlank()) {
                ProfileAvatar(profile, avatar, size = CoreHubTokens.Metrics.messageAvatar)
                Spacer(Modifier.width(8.dp))
            }
            Card(
                modifier = (if (wide) Modifier.weight(1f) else Modifier).combinedClickable(
                    enabled = onActions != null,
                    onClick = { onActions?.invoke() },
                    onLongClick = { onActions?.invoke() },
                ),
                shape = RoundedCornerShape(CoreHubTokens.Radius.bubble),
                colors = CardDefaults.cardColors(
                    containerColor = container,
                    contentColor = if (line.isError) palette.error else palette.textPrimary,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    line.sender?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    }
                    if (hasThinking) ThinkingTimeline(line)
                    if (parsed.text.isNotBlank()) {
                        if (line.fromUser) {
                            Text(text = parsed.text, style = CoreHubTextStyles.message.copy(textDirection = TextDirection.Content))
                        } else {
                            ChatMarkdownText(text = parsed.text)
                        }
                    }
                    parsed.files.forEach { file ->
                        ChatFileCard(file = file, onDownload = { onDownload?.invoke(file) })
                    }
                    val stamp = formatStamp(line.timestamp)
                    if (stamp.isNotBlank()) {
                        Text(stamp, style = CoreHubTextStyles.meta, color = palette.textMuted)
                    }
                }
            }
        }
    }
}

internal fun quoteForReply(quoted: String, reply: String): String {
    val excerpt = quoted.trim().lineSequence().take(8).joinToString("\n") { "> $it" }
    return listOf(excerpt, reply.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
}

@Composable
internal fun ChatFileCard(file: ChatFileLink, onDownload: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDownload),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    file.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (file.fileName != file.label) {
                    Text(
                        file.fileName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = TextDirection.Ltr,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.download_action),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun ThinkingTimeline(line: ChatLine) {
    var expandedOverride by rememberSaveable(line.startedAtMillis) { mutableStateOf<Boolean?>(null) }
    val hasDetails = line.tools.isNotEmpty() || !line.reasoning.isNullOrBlank()
    val expanded = expandedOverride ?: line.streaming
    val nowMillis = timelineNow(line)
    val elapsed = line.startedAtMillis?.let { formatElapsed(nowMillis - it) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasDetails) Modifier.clickable { expandedOverride = !expanded }
                    else Modifier,
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (line.streaming) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.thinking_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            elapsed?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (hasDetails) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.thinking_collapse else R.string.thinking_expand,
                    ),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                line.tools.forEach { tool -> ToolStepRow(tool, nowMillis) }
                line.reasoning?.takeIf { it.isNotBlank() }?.let { reasoning ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(9.dp),
                    ) {
                        Text(
                            reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ToolStepRow(tool: ChatToolStep, nowMillis: Long) {
    val seconds = tool.durationSeconds ?: if (tool.status == ToolRunStatus.Running) {
        (nowMillis - tool.startedAtMillis).coerceAtLeast(0) / 1000.0
    } else {
        null
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(9.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tool.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                tool.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = TextDirection.Ltr,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            seconds?.let {
                Text(
                    formatToolDuration(it),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (tool.status) {
                ToolRunStatus.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                )
                ToolRunStatus.Done -> Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.tool_status_done),
                    modifier = Modifier.size(17.dp),
                    tint = androidx.compose.ui.graphics.Color(0xFF67C650),
                )
                ToolRunStatus.Error -> Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.tool_status_failed),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun timelineNow(line: ChatLine): Long {
    var now by remember(line.startedAtMillis, line.finishedAtMillis) {
        mutableLongStateOf(line.finishedAtMillis ?: System.currentTimeMillis())
    }
    LaunchedEffect(line.streaming, line.finishedAtMillis) {
        if (!line.streaming) {
            now = line.finishedAtMillis ?: System.currentTimeMillis()
            return@LaunchedEffect
        }
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return line.finishedAtMillis ?: now
}

internal fun formatElapsed(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes == 0) "${seconds}s" else "${minutes}m${seconds.toString().padStart(2, '0')}s"
}

internal fun formatToolDuration(seconds: Double): String = when {
    seconds < 10 -> String.format(Locale.US, "%.1fs", seconds)
    seconds < 60 -> "${seconds.toInt()}s"
    else -> "${(seconds / 60).toInt()}m${(seconds.toInt() % 60).toString().padStart(2, '0')}s"
}

