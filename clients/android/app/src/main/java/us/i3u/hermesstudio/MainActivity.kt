package us.i3u.hermesstudio

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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
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
import us.i3u.hermesstudio.ui.agents.*
import us.i3u.hermesstudio.ui.chat.*
import us.i3u.hermesstudio.ui.groups.*
import us.i3u.hermesstudio.ui.navigation.*
import us.i3u.hermesstudio.ui.models.*
import us.i3u.hermesstudio.ui.sessions.*
import us.i3u.hermesstudio.ui.settings.*
import us.i3u.hermesstudio.ui.workflows.*
import us.i3u.hermesstudio.ui.theme.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Applies the language chosen in Settings before any screen is built. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
private fun App(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Start and resume are the same moment to Android, so one observer covers
    // both. The throttle and the metered rule live in the view model, which is
    // where they can be tested; here the app only says "we are visible again".
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.checkForUpdates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CoreHubTheme(appearance = state.appearance) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppContent(state, viewModel)
        }
    }
}

@Composable
private fun AppContent(state: UiState, viewModel: AppViewModel) {

    // The system back gesture belongs to the app while there is somewhere to
    // go back to. The Chat root lets it fall through and close the app; the
    // other three segments return to Chat first, as the drawer would.
    when (state.screen) {
        Screen.Chats, Screen.Loading, Screen.Onboarding, Screen.Login -> Unit
        Screen.Groups, Screen.Workflows, Screen.History -> BackHandler { viewModel.showTab(Tab.Chat) }
        else -> BackHandler { viewModel.back() }
    }

    when (state.screen) {
        Screen.Loading -> LoadingScreen(
            baseUrl = state.baseUrl,
            error = state.error,
            busy = state.busy,
            onRetry = { viewModel.retrySession() },
            onSignOut = { viewModel.signOut() },
        )
        Screen.Onboarding -> OnboardingScreen(
            languageAction = { LanguageAction(state, viewModel) },
            onDone = { viewModel.finishOnboarding() },
        )
        Screen.Login -> LoginScreen(state, viewModel)
        // The four sections of the conversation switch share the drawer shell.
        Screen.Chats, Screen.Conversation -> HomeShell(state, viewModel) { openDrawer -> ConversationScreen(state, viewModel, onMenu = openDrawer) }
        Screen.Groups -> HomeShell(state, viewModel) { openDrawer -> GroupsScreen(state, viewModel, onMenu = openDrawer) }
        Screen.Workflows -> HomeShell(state, viewModel) { openDrawer -> WorkflowsScreen(state, viewModel, onMenu = openDrawer) }
        Screen.History -> HomeShell(state, viewModel) { openDrawer -> HistoryScreen(state, viewModel, onMenu = openDrawer) }
        Screen.Room -> RoomScreen(state, viewModel)
        Screen.Workflow -> WorkflowScreen(state, viewModel)
        Screen.WorkflowRun -> WorkflowRunScreen(state, viewModel)
        // The primary rail (NAVIGATION.md §1).
        Screen.Connections -> ConnectionsScreen(state, viewModel)
        Screen.AgentManager -> AgentManagerScreen(state, viewModel)
        Screen.Models -> ModelsScreen(state, viewModel)
        // Settings and its Tools (§2).
        Screen.Settings -> SettingsScreen(state, viewModel)
        Screen.Logs -> LogsScreen(state, viewModel)
        Screen.Usage -> UsageScreen(state, viewModel)
        Screen.Performance -> PerformanceScreen(state, viewModel)
        Screen.SkillsUsage -> SkillsUsageScreen(state, viewModel)
        Screen.Theme -> ThemeScreen(state, viewModel)
        Screen.Pets -> PetsScreen(state, viewModel)
        Screen.Profiles -> ProfilesScreen(state, viewModel)
        // Under an agent card (§4).
        Screen.Agent -> AgentScreen(state, viewModel)
        Screen.CronJobs -> CronJobsScreen(state, viewModel)
        Screen.CronJob -> CronJobEditorScreen(state, viewModel)
        Screen.CronHistory -> CronHistoryScreen(state, viewModel)
        Screen.Kanban -> KanbanScreen(state, viewModel)
        Screen.KanbanTask -> KanbanTaskScreen(state, viewModel)
        Screen.Channels -> ChannelsScreen(state, viewModel)
        Screen.Channel -> ChannelScreen(state, viewModel)
        Screen.Skills -> SkillsScreen(state, viewModel)
        Screen.Skill -> SkillScreen(state, viewModel)
        Screen.Plugins -> PluginsScreen(state, viewModel)
        Screen.Mcp -> McpScreen(state, viewModel)
        Screen.Memory -> HermesMemoryScreen(state, viewModel)
        Screen.Journey -> JourneyScreen(state, viewModel)
        Screen.HermesSettings -> HermesSettingsScreen(state, viewModel)
        Screen.EkkoMemory -> EkkoMemoryScreen(state, viewModel)
        Screen.EkkoSkills -> EkkoSkillsScreen(state, viewModel)
        Screen.EkkoMcp -> EkkoMcpScreen(state, viewModel)
        Screen.EkkoSettings -> EkkoSettingsScreen(state, viewModel)
        Screen.AgentSettings -> AgentSettingsScreen(state, viewModel)
        Screen.DshPlugins -> DshPluginsScreen(state, viewModel)
        Screen.DshPresets -> DshPresetsScreen(state, viewModel)
        // No menu entry, as on the web: search results and the pending banner
        // reach the Global Agent; a profile card's "Edit config" reaches Files.
        Screen.GlobalAgent -> GlobalAgentScreen(state, viewModel)
        Screen.Files -> FilesScreen(state, viewModel)
    }
}

// ── login ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(state: UiState, viewModel: AppViewModel) {
    var url by rememberSaveable { mutableStateOf(state.baseUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val scanPrompt = stringResource(R.string.login_scan_prompt)
    val scanQr = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.loginWithQr(it) }
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanQr.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(scanPrompt)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        } else {
            viewModel.reportCameraDenied()
        }
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.app_name),
                actions = { LanguageAction(state, viewModel) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { askCamera.launch(Manifest.permission.CAMERA) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (state.busy) R.string.login_submitting else R.string.login_scan_qr))
            }
            Text(
                stringResource(R.string.login_scan_qr_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.login_or_password),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.login_server_label)) },
                placeholder = { Text(stringResource(R.string.login_server_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { viewModel.login(url, username, password) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.busy) R.string.login_submitting else R.string.login_submit))
            }
            Text(
                stringResource(R.string.login_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── profiles ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProfilesScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    var avatarTarget by remember { mutableStateOf<String?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri ?: return@rememberLauncherForActivityResult; val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult; val mime = context.contentResolver.getType(uri) ?: "image/png"; avatarTarget?.let { viewModel.updateProfileAvatar(it, "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)) } }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri ?: return@rememberLauncherForActivityResult; val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult; viewModel.importProfile(bytes, uri.lastPathSegment ?: "profile.tar.gz") }
    var confirmSignOut by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<Profile?>(null) }
    var rename by remember { mutableStateOf<Profile?>(null) }
    var confirmDelete by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }

    manage?.let { profile ->
        ModalBottomSheet(
            onDismissRequest = { manage = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            SheetTitle(profile.name)
            ManageSheet(
                onRename = {
                    manage = null
                    rename = profile
                },
                onDelete = {
                    manage = null
                    confirmDelete = profile
                },
            )
            TextButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { manage = null; viewModel.openProfileConfig(profile.name) }) {
                Text(stringResource(R.string.profile_edit_config), Modifier.fillMaxWidth())
            }
            TextButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { manage = null; viewModel.restartProfile(profile.name) }) {
                Text(stringResource(R.string.profile_restart), Modifier.fillMaxWidth())
            }
            TextButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { manage = null; viewModel.switchActiveProfile(profile.name) }) { Text(stringResource(R.string.profile_make_active), Modifier.fillMaxWidth()) }
            TextButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { manage = null; viewModel.downloadProfile(profile.name) }) { Text(stringResource(R.string.profile_export), Modifier.fillMaxWidth()) }
            TextButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { avatarTarget = profile.name; manage = null; avatarPicker.launch("image/*") }) { Text(stringResource(R.string.profile_avatar), Modifier.fillMaxWidth()) }
            TextButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { manage = null; viewModel.clearProfileAvatar(profile.name) }) { Text(stringResource(R.string.profile_avatar_clear), Modifier.fillMaxWidth()) }
        }
    }
    rename?.let { profile ->
        TextPromptDialog(
            title = stringResource(R.string.profiles_rename_title),
            initial = profile.name,
            hint = profile.name,
            action = stringResource(R.string.action_rename),
            onConfirm = { viewModel.renameProfile(profile.name, it) },
            onDismiss = { rename = null },
        )
    }
    confirmDelete?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.profiles_delete_title, profile.name),
            body = stringResource(R.string.profiles_delete_body),
            action = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteProfile(profile.name) },
            onDismiss = { confirmDelete = null },
        )
    }
    if (creating) {
        TextPromptDialog(
            title = stringResource(R.string.profiles_new),
            initial = "",
            hint = stringResource(R.string.profiles_new_hint),
            action = stringResource(R.string.action_create),
            onConfirm = { viewModel.createProfile(it) },
            onDismiss = { creating = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_sign_out_title),
            body = stringResource(R.string.confirm_sign_out_body),
            action = stringResource(R.string.action_sign_out),
            onConfirm = { viewModel.signOut() },
            onDismiss = { confirmSignOut = false },
        )
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.nav_profiles),
                subtitle = state.account?.let { stringResource(R.string.profiles_signed_in, it) },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { importPicker.launch("*/*") }) { Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.profile_import), tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profiles_new), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.refreshProfiles() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { confirmSignOut = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.action_sign_out), tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = StudioHorizontalPadding,
                end = StudioHorizontalPadding,
                top = 12.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) item { LoadingRow() }
            state.error?.let { message -> item { ErrorNote(message) { viewModel.dismissError() } } }
            if (state.profiles.isNotEmpty()) {
                item {
                    StudioGroupedCard {
                        state.profiles.forEachIndexed { index, profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { viewModel.selectProfile(profile.name) },
                                onLongClick = { manage = profile },
                            )
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(profile.name, profile.avatar, size = 52.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                profile.model ?: stringResource(R.string.profiles_no_model),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.profileRuntimeStatuses[profile.name]?.let { status ->
                                Text(stringResource(R.string.profile_runtime_status, status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (profile.name == state.activeProfile) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50.dp),
                            ) {
                                Text(
                                    stringResource(R.string.profiles_active),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                            if (index != state.profiles.lastIndex) StudioCardDivider(startIndent = 80)
                        }
                    }
                }
            }
        }
    }
}

private enum class ConfirmAction { SignOut, RestartGateway }

/** One text field, one button: rename a thing, or name a new one. */
@Composable
internal fun TextPromptDialog(
    title: String,
    initial: String,
    hint: String,
    action: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = {
                    onDismiss()
                    onConfirm(value.trim())
                },
            ) { Text(action) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The rename / delete pair, shared by conversations, profiles and rooms. */
@Composable
private fun ManageSheet(
    onRename: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        onRename?.let {
            SheetRow(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.action_rename),
                detail = "",
                onClick = it,
            )
        }
        SheetRow(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.action_delete),
            detail = "",
            onClick = onDelete,
        )
    }
}

/** Stands between a stray tap and something that cannot be undone. */
@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    action: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            ) { Text(action) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Language is reachable before sign-in on purpose: someone who cannot read the
 * sign-in form cannot get to Settings to fix that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSheet(state: UiState, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        PickerSheet(
            title = stringResource(R.string.settings_language),
            loading = false,
            rows = APP_LANGUAGES.map { option ->
                PickerRow(
                    label = AppLocale.labelFor(context, option),
                    detail = null,
                    selected = option.tag == state.language,
                ) {
                    onDismiss()
                    viewModel.setLanguage(option.tag)
                    // Resources are resolved when the activity is built, so rebuild it.
                    activity?.recreate()
                }
            },
        )
    }
}

@Composable
internal fun LanguageAction(state: UiState, viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (open) LanguageSheet(state, viewModel) { open = false }

    TextButton(onClick = { open = true }) {
        Icon(
            Icons.Filled.Language,
            contentDescription = stringResource(R.string.settings_language),
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            AppLocale.currentEndonym(context, state.language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AccountSettings(state: UiState, viewModel: AppViewModel) {
    SettingsRow(
        icon = Icons.Filled.Person,
        label = stringResource(R.string.settings_account),
        value = state.account ?: stringResource(R.string.settings_account_unknown),
    )
    AccountStudioSettings(state, viewModel)
}

/** The agent knobs, with gateway auto-start where Studio keeps it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AgentSettings(state: UiState, viewModel: AppViewModel) {
    val agent = state.agentSettings
    val policy = state.autoStart
    var editing by remember { mutableStateOf<String?>(null) }
    var enforcementSheet by remember { mutableStateOf(false) }
    var policySheet by remember { mutableStateOf(false) }

    editing?.let { key ->
        val current = when (key) {
            "max_turns" -> agent?.maxTurns
            "gateway_timeout" -> agent?.gatewayTimeout
            else -> agent?.restartDrainTimeout
        }
        TextPromptDialog(
            title = stringResource(
                when (key) {
                    "max_turns" -> R.string.agent_max_turns
                    "gateway_timeout" -> R.string.agent_gateway_timeout
                    else -> R.string.agent_drain_timeout
                },
            ),
            initial = current?.toString().orEmpty(),
            hint = "",
            action = stringResource(R.string.action_save),
            onConfirm = { typed -> typed.toIntOrNull()?.let { viewModel.setAgentValue(key, it) } },
            onDismiss = { editing = null },
        )
    }
    if (enforcementSheet) {
        ModalBottomSheet(
            onDismissRequest = { enforcementSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = stringResource(R.string.agent_tool_enforcement),
                loading = false,
                rows = TOOL_ENFORCEMENT.map { (value, label) ->
                    PickerRow(label = stringResource(label), detail = null, selected = value == agent?.toolEnforcement) {
                        enforcementSheet = false
                        viewModel.setAgentValue("tool_use_enforcement", value)
                    }
                },
            )
        }
    }
    if (policySheet && policy != null) {
        ModalBottomSheet(
            onDismissRequest = { policySheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = stringResource(R.string.agent_policy),
                loading = false,
                rows = listOf(
                    PickerRow(
                        label = stringResource(R.string.agent_policy_all),
                        detail = null,
                        selected = policy.include == null,
                    ) {
                        policySheet = false
                        viewModel.setAutoStart(policy.copy(include = null))
                    },
                    PickerRow(
                        label = stringResource(R.string.agent_policy_include),
                        detail = null,
                        selected = policy.include != null,
                    ) {
                        policySheet = false
                        viewModel.setAutoStart(
                            policy.copy(
                                include = policy.include ?: listOf(state.activeProfile),
                                exclude = emptyList(),
                            ),
                        )
                    },
                ),
            )
        }
    }

    SettingsRow(
        icon = Icons.Filled.Repeat,
        label = stringResource(R.string.agent_max_turns),
        value = agent?.maxTurns?.toString() ?: stringResource(R.string.agent_unset),
        onClick = { editing = "max_turns" },
    )
    SettingsRow(
        icon = Icons.Filled.Timer,
        label = stringResource(R.string.agent_gateway_timeout),
        value = agent?.gatewayTimeout?.toString() ?: stringResource(R.string.agent_unset),
        onClick = { editing = "gateway_timeout" },
    )
    SettingsRow(
        icon = Icons.Filled.HourglassBottom,
        label = stringResource(R.string.agent_drain_timeout),
        value = agent?.restartDrainTimeout?.toString() ?: stringResource(R.string.agent_unset),
        onClick = { editing = "restart_drain_timeout" },
    )
    SettingsRow(
        icon = Icons.AutoMirrored.Filled.Rule,
        label = stringResource(R.string.agent_tool_enforcement),
        value = stringResource(
            TOOL_ENFORCEMENT.firstOrNull { it.first == agent?.toolEnforcement }?.second ?: R.string.agent_tool_auto,
        ),
        onClick = { enforcementSheet = true },
    )

    SettingsSection(stringResource(R.string.agent_autostart_title))
    Text(
        stringResource(R.string.agent_autostart_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    SettingsRow(
        icon = Icons.Filled.PowerSettingsNew,
        label = stringResource(R.string.settings_auto_start),
        value = stringResource(
            if (policy?.enabled == true) R.string.settings_auto_start_on else R.string.settings_auto_start_off,
        ),
        trailing = {
            Switch(
                checked = policy?.enabled == true,
                onCheckedChange = { on -> policy?.let { viewModel.setAutoStart(it.copy(enabled = on)) } },
            )
        },
    )
    if (policy?.enabled == true) {
        if (state.activeProfile.ifBlank { "default" } == "default") {
            SettingsRow(
                icon = Icons.Filled.AccountTree,
                label = stringResource(R.string.agent_management),
                value = stringResource(R.string.agent_management_note),
                trailing = {
                    Switch(
                        checked = policy.management == "unified",
                        onCheckedChange = { unified ->
                            viewModel.setAutoStart(
                                policy.copy(management = if (unified) "unified" else "per_profile"),
                            )
                        },
                    )
                },
            )
        }
        SettingsRow(
            icon = Icons.Filled.Groups,
            label = stringResource(R.string.agent_policy),
            value = stringResource(
                if (policy.include == null) R.string.agent_policy_all else R.string.agent_policy_include,
            ),
            onClick = { policySheet = true },
        )
        policy.include?.let { included ->
            Text(
                stringResource(R.string.agent_policy_profiles),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.profiles.forEach { profile ->
                    val chosen = profile.name in included
                    AssistChip(
                        onClick = {
                            val next = if (chosen) included - profile.name else included + profile.name
                            viewModel.setAutoStart(policy.copy(include = next))
                        },
                        label = { Text(profile.name) },
                        leadingIcon = if (chosen) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (policy.include == null) {
            Text(
                stringResource(R.string.agent_excluded_profiles),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.profiles.forEach { profile ->
                    val excluded = profile.name in policy.exclude
                    AssistChip(
                        onClick = {
                            val next = if (excluded) policy.exclude - profile.name else policy.exclude + profile.name
                            viewModel.setAutoStart(policy.copy(exclude = next))
                        },
                        label = { Text(profile.name) },
                        leadingIcon = if (excluded) {
                            { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceSettings(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    var languageSheet by remember { mutableStateOf(false) }
    var appearanceSheet by remember { mutableStateOf(false) }
    var reasoningSheet by remember { mutableStateOf(false) }
    var voiceSheet by remember { mutableStateOf(false) }
    var voiceOutputSheet by remember { mutableStateOf(false) }
    var speechLanguageSheet by remember { mutableStateOf(false) }
    val language = APP_LANGUAGES.firstOrNull { it.tag == state.language } ?: APP_LANGUAGES.first()

    // Core Hub keeps TTS settings per profile, so the row has to show this
    // profile's voice before the sheet is ever opened — and the profile that
    // matters is the one the chat runs under, which an open conversation can
    // set without the drawer's active profile moving at all.
    LaunchedEffect(state.chatProfile) {
        viewModel.loadVoiceSettings()
        viewModel.loadSpeechLanguages()
    }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) viewModel.setAppLogo(bytes)
    }

    if (languageSheet) LanguageSheet(state, viewModel) { languageSheet = false }
    if (appearanceSheet) {
        ModalBottomSheet(onDismissRequest = { appearanceSheet = false }, sheetState = rememberModalBottomSheetState()) {
            PickerSheet(
                title = stringResource(R.string.settings_appearance),
                loading = false,
                rows = APPEARANCE_LEVELS.map { (value, label) ->
                    PickerRow(label = stringResource(label), detail = null, selected = state.appearance == value) {
                        appearanceSheet = false
                        viewModel.setAppearance(value)
                        activity?.recreate()
                    }
                },
            )
        }
    }
    if (reasoningSheet) {
        ModalBottomSheet(onDismissRequest = { reasoningSheet = false }, sheetState = rememberModalBottomSheetState()) {
            PickerSheet(
                title = stringResource(R.string.settings_reasoning),
                loading = false,
                rows = REASONING_LEVELS.map { (value, label) ->
                    PickerRow(
                        label = stringResource(label),
                        detail = if (value.isBlank()) stringResource(R.string.reasoning_use_profile) else null,
                        selected = state.reasoningEffort == value,
                    ) {
                        reasoningSheet = false
                        viewModel.setReasoningEffort(value)
                    }
                },
            )
        }
    }
    if (voiceSheet) {
        ModalBottomSheet(onDismissRequest = { voiceSheet = false }, sheetState = rememberModalBottomSheetState()) {
            PickerSheet(
                title = stringResource(R.string.settings_voice_input),
                loading = false,
                rows = VOICE_INPUT_MODES.map { (value, label) ->
                    PickerRow(
                        label = stringResource(label),
                        detail = stringResource(if (value == Store.VOICE_INPUT_SERVER) R.string.voice_input_server_note else R.string.voice_input_device_note),
                        selected = state.voiceInput == value,
                    ) {
                        voiceSheet = false
                        viewModel.setVoiceInput(value)
                    }
                },
            )
        }
    }
    if (speechLanguageSheet) SpeechLanguageSheet(state, viewModel) { speechLanguageSheet = false }
    if (voiceOutputSheet) {
        ModalBottomSheet(onDismissRequest = { voiceOutputSheet = false }, sheetState = rememberModalBottomSheetState()) {
            PickerSheet(
                title = stringResource(R.string.settings_voice_output),
                loading = state.loadingVoiceSettings,
                rows = voiceOutputRows(state) { choice ->
                    voiceOutputSheet = false
                    viewModel.setVoiceOutput(choice)
                },
            )
            state.voiceSettingsError?.let { failure ->
                Text(
                    stringResource(R.string.voice_output_unreadable, failure),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    SettingsRow(
        icon = Icons.Filled.DisplaySettings,
        label = stringResource(R.string.settings_appearance),
        value = appearanceLabel(state.appearance),
        onClick = { appearanceSheet = true },
    )
    SettingsRow(
        icon = Icons.Filled.Language,
        label = stringResource(R.string.settings_language),
        value = AppLocale.labelFor(context, language),
        onClick = { languageSheet = true },
    )
    SettingsRow(
        icon = Icons.Filled.Psychology,
        label = stringResource(R.string.settings_reasoning),
        value = reasoningLabel(state.reasoningEffort),
        onClick = { reasoningSheet = true },
    )
    Text(
        stringResource(R.string.settings_reasoning_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    SettingsRow(
        icon = Icons.Filled.Mic,
        label = stringResource(R.string.settings_voice_input),
        value = voiceInputLabel(state.voiceInput),
        onClick = { voiceSheet = true },
    )
    Text(
        stringResource(R.string.settings_voice_input_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    SettingsRow(
        icon = Icons.Filled.Translate,
        label = stringResource(R.string.settings_speech_language),
        value = speechLanguageLabel(state, Locale.getDefault().toLanguageTag()),
        onClick = {
            viewModel.loadSpeechLanguages()
            speechLanguageSheet = true
        },
    )
    Text(
        stringResource(R.string.settings_speech_language_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    SettingsRow(
        icon = Icons.Filled.RecordVoiceOver,
        label = stringResource(R.string.settings_voice_output),
        value = voiceOutputLabel(state),
        onClick = {
            // The owner may have just changed the provider in the web client.
            viewModel.loadVoiceSettings(force = true)
            voiceOutputSheet = true
        },
    )
    Text(
        stringResource(R.string.settings_voice_output_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    LogoRow(
        value = stringResource(
            when {
                AppLogo.isCustom -> R.string.settings_logo_custom
                AppLogo.image != null -> R.string.settings_logo_server
                else -> R.string.settings_logo_missing
            },
        ),
        onClick = { pickLogo.launch("image/*") },
    )
    if (AppLogo.isCustom) {
        SettingsRow(
            icon = Icons.Filled.Refresh,
            label = stringResource(R.string.settings_logo_reset),
            value = stringResource(R.string.settings_logo_reset_note),
            onClick = { viewModel.resetAppLogo() },
        )
    }
    Text(
        stringResource(R.string.settings_logo_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
internal fun AboutSettings(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    SettingsRow(
        icon = Icons.Filled.PhoneAndroid,
        label = stringResource(R.string.settings_phone_name),
        value = BuildConfig.VERSION_NAME,
    )
    // "Check for updates", with the installed build above it and the result of
    // the last check — including "you are up to date" and every failure with
    // its own reason — as the row's own value.
    SettingsRow(
        icon = Icons.Filled.SystemUpdate,
        label = stringResource(R.string.settings_update),
        value = if (state.update.checking) {
            stringResource(R.string.update_checking)
        } else {
            val outcome = updateOutcomeText(state.update)
            if (state.update.checkedAt > 0L) {
                stringResource(
                    R.string.update_last_checked,
                    outcome,
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                        state.update.checkedAt,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                )
            } else {
                outcome
            }
        },
        onClick = { viewModel.checkForUpdates(manual = true) },
    )
    Text(
        stringResource(R.string.settings_update_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    SettingsRow(
        icon = Icons.Filled.Info,
        label = stringResource(R.string.settings_version),
        value = stringResource(R.string.footer_version, state.serverVersion ?: BuildConfig.VERSION_NAME),
    )
    SettingsRow(
        icon = Icons.Filled.Dns,
        label = stringResource(R.string.settings_address),
        value = state.baseUrl.ifBlank { stringResource(R.string.settings_address_missing) },
    )
    SettingsRow(
        icon = painterResource(R.drawable.ic_github),
        label = stringResource(R.string.settings_phone_github),
        value = PHONE_REPOSITORY_URL,
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PHONE_REPOSITORY_URL))) },
    )
    SettingsRow(
        icon = painterResource(R.drawable.ic_github),
        label = stringResource(R.string.settings_studio_github),
        value = STUDIO_REPOSITORY_URL,
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(STUDIO_REPOSITORY_URL))) },
    )
    Text(
        stringResource(R.string.settings_about_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

private val TOOL_ENFORCEMENT = listOf(
    "auto" to R.string.agent_tool_auto,
    "always" to R.string.agent_tool_always,
    "never" to R.string.agent_tool_never,
)

/** Every channel Hermes can speak on, and whether it is ready. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelsScreen(state: UiState, viewModel: AppViewModel) {
    val known = state.serverConfig?.channels.orEmpty().associateBy { it.platform }
    val listed = CHANNELS.map { spec -> spec to known[spec.platform] }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.nav_channels),
                subtitle = state.activeProfile.ifBlank { null },
                onBack = { viewModel.back() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = StudioHorizontalPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.savingSetting) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }

            StudioGroupedCard {
                listed.forEachIndexed { index, (spec, status) ->
                    val connected = status?.configured == true && status.enabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openChannel(spec.platform) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Icon(
                                painter = painterResource(spec.iconRes),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.padding(10.dp).size(28.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(spec.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    when {
                                        connected -> R.string.channel_connected
                                        status?.configured == true -> R.string.channel_off
                                        else -> R.string.channel_missing
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (connected) Color(0xFF30D158)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != listed.lastIndex) StudioCardDivider(startIndent = 76)
                }
            }

            Text(
                stringResource(R.string.channel_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

/** One channel: its credentials, and whether Hermes answers on it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelScreen(state: UiState, viewModel: AppViewModel) {
    val platform = state.openChannel ?: return
    val context = LocalContext.current
    val spec = channelSpec(platform)
    val status = state.serverConfig?.channels.orEmpty().firstOrNull { it.platform == platform }
    val values = remember(platform, status?.values) {
        mutableStateMapOf<String, String>().apply {
            putAll(status?.values.orEmpty())
            spec.fields.filter { it.kind == ChannelFieldKind.Toggle }.forEach { field ->
                putIfAbsent(field.path, field.defaultEnabled.toString())
            }
        }
    }
    val revealed = remember(platform) { mutableStateMapOf<String, Boolean>() }
    var enabled by remember(platform, status?.enabled) { mutableStateOf(status?.enabled ?: true) }
    var confirmClear by remember(platform) { mutableStateOf(false) }
    var openedQrId by remember(platform) { mutableStateOf("") }

    LaunchedEffect(state.weixinQr.id, state.weixinQr.url) {
        val qr = state.weixinQr
        if (platform == "weixin" && qr.id.isNotBlank() && qr.url.isNotBlank() && openedQrId != qr.id) {
            openedQrId = qr.id
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qr.url)))
            }.onFailure { viewModel.showToolError(it) }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.channel_clear_title, spec.label),
            body = stringResource(R.string.channel_clear_body),
            action = stringResource(R.string.channel_clear),
            onConfirm = { viewModel.clearChannel(platform) },
            onDismiss = { confirmClear = false },
        )
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = spec.label,
                subtitle = stringResource(
                    when {
                        status?.configured == true && status.enabled -> R.string.channel_connected
                        status?.configured == true -> R.string.channel_off
                        else -> R.string.channel_missing
                    },
                ),
                onBack = { viewModel.back() },
                leading = {
                    Icon(
                        painter = painterResource(spec.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.Unspecified,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            if (state.savingSetting) LoadingRow()
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }

            if (spec.exclusive) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFF9500).copy(alpha = 0.14f),
                ) {
                    Text(
                        stringResource(R.string.channel_exclusive_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB340),
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            if (platform == "weixin") {
                val qrStatus = when (state.weixinQr.status) {
                    "loading" -> R.string.channel_qr_loading
                    "waiting" -> R.string.channel_qr_waiting
                    "scanned" -> R.string.channel_qr_scanned
                    "confirmed" -> R.string.channel_qr_confirmed
                    "expired" -> R.string.channel_qr_expired
                    "error" -> R.string.channel_qr_error
                    else -> R.string.channel_qr_ready
                }
                SettingsRow(
                    icon = Icons.Filled.Cable,
                    label = stringResource(R.string.channel_qr_link),
                    value = stringResource(qrStatus),
                    onClick = viewModel::startWeixinQr,
                    trailing = if (state.weixinQr.status == "loading" || state.weixinQr.status == "waiting" || state.weixinQr.status == "scanned") {
                        { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                    } else null,
                )
            }

            if (spec.fields.none {
                    it.target == ChannelFieldTarget.Credentials && it.path == "enabled"
                }
            ) {
                SettingsRow(
                    icon = painterResource(spec.iconRes),
                    label = stringResource(R.string.channel_enabled),
                    value = stringResource(R.string.channel_enabled_note),
                    trailing = {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    },
                )
            }

            @Composable
            fun section(target: ChannelFieldTarget, title: Int) {
                val fields = spec.fields.filter { it.target == target }
                if (fields.isEmpty()) return
                SettingsSection(stringResource(title))
                fields.forEach { field ->
                    val label = stringResource(field.labelRes)
                    val hint = field.hintRes?.let { stringResource(it) }.orEmpty()
                    when (field.kind) {
                        ChannelFieldKind.Toggle -> SettingsRow(
                            icon = if (target == ChannelFieldTarget.Credentials) Icons.Filled.VpnLock else Icons.Filled.Tune,
                            label = label,
                            value = hint,
                            trailing = {
                                Switch(
                                    checked = values[field.path].toBoolean(),
                                    onCheckedChange = { values[field.path] = it.toString() },
                                )
                            },
                        )
                        ChannelFieldKind.Text,
                        ChannelFieldKind.Secret,
                        ChannelFieldKind.CommaList,
                        -> {
                            val secret = field.kind == ChannelFieldKind.Secret
                            val visible = revealed[field.path] == true
                            OutlinedTextField(
                                value = values[field.path].orEmpty(),
                                onValueChange = { values[field.path] = it },
                                label = { Text(label) },
                                supportingText = hint.takeIf(String::isNotBlank)?.let { { Text(it) } },
                                placeholder = field.placeholder.takeIf(String::isNotBlank)?.let { placeholder ->
                                    { Text(placeholder) }
                                },
                                trailingIcon = if (secret) {
                                    {
                                        IconButton(onClick = { revealed[field.path] = !visible }) {
                                            Icon(
                                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = stringResource(
                                                    if (visible) R.string.channel_hide_secret else R.string.channel_show_secret,
                                                ),
                                            )
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            section(ChannelFieldTarget.Credentials, R.string.channel_credentials_section)
            section(ChannelFieldTarget.Configuration, R.string.channel_behavior_section)

            Button(
                onClick = { viewModel.saveChannel(platform, values.toMap(), enabled) },
                enabled = !state.savingSetting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.channel_save))
            }

            if (status?.configured == true && spec.supportsCredentialClear) {
                SettingsRow(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.channel_clear),
                    value = stringResource(R.string.channel_clear_body),
                    onClick = { confirmClear = true },
                )
            }

            Text(
                stringResource(R.string.channel_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
internal fun SettingsSection(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
internal fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    SettingsRowContent(
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        label = label,
        value = value,
        onClick = onClick,
        trailing = trailing,
    )
}

/** Settings row for Studio/channel vector assets that are not Material icons. */
@Composable
internal fun SettingsRow(
    icon: Painter,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    SettingsRowContent(
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        label = label,
        value = value,
        onClick = onClick,
        trailing = trailing,
    )
}

@Composable
private fun SettingsRowContent(
    leading: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StudioHorizontalPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Settings row that previews the current app mark instead of an icon. */
@Composable
private fun LogoRow(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StudioHorizontalPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppMark(size = 34.dp, corner = 10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_logo), style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
internal fun NoticeNote(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

/**
 * The in-app update, as a card in the same stack as the other notices above
 * the composer.
 *
 * Deliberately not a dialog: a new test build is never urgent enough to stand
 * between the owner and the conversation he opened the app for. It names the
 * version and what changed, downloads with visible progress that can be
 * cancelled, and only then offers to install. When Android has not allowed
 * this app to install packages, it says so and opens the right settings screen
 * instead of failing quietly.
 */
@Composable
internal fun UpdateNote(update: UpdateUiState, viewModel: AppViewModel) {
    val release = update.release ?: return
    val context = LocalContext.current
    val palette = CoreHub.palette
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.reportInstallResult(result.resultCode == Activity.RESULT_OK)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(CoreHubTokens.Radius.card),
        colors = CardDefaults.cardColors(containerColor = palette.bgSecondary),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(CoreHubTokens.Metrics.actionButton),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.update_available_title),
                        style = CoreHubTextStyles.sessionTitle.copy(fontWeight = CoreHubTokens.Type.titleWeight),
                        color = palette.textPrimary,
                    )
                    Text(
                        if (release.sizeBytes > 0L) {
                            stringResource(
                                R.string.update_notice_version,
                                release.versionName,
                                android.text.format.Formatter.formatShortFileSize(context, release.sizeBytes),
                            )
                        } else {
                            stringResource(R.string.update_notice_version_only, release.versionName)
                        },
                        style = CoreHubTextStyles.meta,
                        color = palette.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                release.notes.ifBlank { stringResource(R.string.update_notice_no_notes) },
                // Release notes are content, not UI chrome: they follow their
                // own direction so an Arabic note reads correctly in an English
                // interface and the other way round.
                style = CoreHubTextStyles.meta.copy(textDirection = TextDirection.Content),
                color = palette.textSecondary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )

            if (update.downloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { update.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accent,
                    trackColor = palette.segmentTrack,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.update_progress, update.percent),
                    style = CoreHubTextStyles.meta,
                    color = palette.textMuted,
                )
            } else if (update.readyApkPath != null) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.update_ready), style = CoreHubTextStyles.meta, color = palette.textMuted)
            } else if (update.outcome.isUpdateFailure) {
                Spacer(Modifier.height(6.dp))
                Text(
                    updateOutcomeText(update),
                    style = CoreHubTextStyles.meta,
                    color = palette.error,
                )
            }
            if (update.needsInstallPermission) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.update_permission_explain),
                    style = CoreHubTextStyles.meta,
                    color = palette.textSecondary,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                when {
                    update.downloading -> TextButton(onClick = { viewModel.cancelUpdateDownload() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    update.needsInstallPermission -> TextButton(
                        onClick = { context.startActivity(AppUpdater.unknownSourcesIntent(context)) },
                    ) { Text(stringResource(R.string.update_open_settings)) }
                    else -> TextButton(onClick = { viewModel.dismissUpdateNotice() }) {
                        Text(stringResource(R.string.update_later))
                    }
                }
                val ready = update.readyApkPath
                if (ready != null) {
                    Button(
                        enabled = !update.downloading,
                        onClick = {
                            if (!AppUpdater.canInstall(context)) {
                                viewModel.reportInstallPermissionNeeded()
                            } else {
                                installer.launch(AppUpdater.installIntent(context, File(ready)))
                            }
                        },
                    ) { Text(stringResource(R.string.update_install)) }
                } else {
                    Button(enabled = !update.downloading, onClick = { viewModel.downloadUpdate() }) {
                        Text(stringResource(R.string.update_download))
                    }
                }
            }
        }
    }
}

/** True for the outcomes that are a failure rather than a result. */
internal val UpdateOutcome.isUpdateFailure: Boolean
    get() = this !in setOf(UpdateOutcome.Never, UpdateOutcome.UpToDate, UpdateOutcome.UpdateFound)

/** The last outcome in words, with whatever detail it carries. */
@Composable
internal fun updateOutcomeText(update: UpdateUiState): String =
    if (update.outcome.takesDetail && update.outcomeDetail.isNotBlank()) {
        stringResource(update.outcome.messageRes, update.outcomeDetail)
    } else if (update.outcome.takesDetail) {
        // A detail-taking outcome with nothing to say still has to render.
        stringResource(update.outcome.messageRes, "—")
    } else {
        stringResource(update.outcome.messageRes)
    }

// ── shared pieces ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyNote(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.height(22.dp).width(22.dp))
    }
}

@Composable
internal fun ErrorNote(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}
