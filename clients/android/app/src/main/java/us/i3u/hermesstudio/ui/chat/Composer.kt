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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun Composer(
    state: UiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    viewModel: AppViewModel,
) {
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<ComposerSheet?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    var composerExpanded by rememberSaveable { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readAndAttach(context, it, viewModel) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readAndAttach(context, it, viewModel) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        captureUri = null
        if (saved && uri != null) readAndAttach(context, uri, viewModel, fallbackName = "photo.jpg")
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = newCaptureUri(context)
            captureUri = uri
            takePhoto.launch(uri)
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceInput() else viewModel.reportMicrophoneDenied()
    }
    // The field keeps its own selection so dictation can land at the caret and
    // an interim hypothesis can be replaced in place while the user speaks.
    var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    LaunchedEffect(draft) {
        if (field.text != draft) field = TextFieldValue(draft, TextRange(draft.length))
    }
    var voiceAnchor by remember { mutableStateOf<Int?>(null) }
    var voiceLength by remember { mutableStateOf(0) }
    LaunchedEffect(state.voiceSegment) {
        val segment = state.voiceSegment ?: return@LaunchedEffect
        val anchor = voiceAnchor ?: field.selection.end.coerceIn(0, field.text.length)
        val edit = applyVoiceSegment(field.text, anchor, voiceLength, segment.text, segment.kind)
        field = TextFieldValue(edit.text, TextRange(edit.caret))
        onDraftChange(edit.text)
        if (segment.kind == VoiceSegmentKind.Partial) {
            voiceAnchor = anchor
            voiceLength = edit.segmentLength
        } else {
            voiceAnchor = null
            voiceLength = 0
        }
        viewModel.consumeVoiceSegment(segment.serial)
    }
    LaunchedEffect(state.voice) {
        // A take that ended without a final segment leaves nothing to replace.
        if (state.voice != VoiceStatus.Listening) {
            voiceAnchor = null
            voiceLength = 0
        }
    }

    when (sheet) {
        ComposerSheet.Options -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            OptionsSheet(
                state = state,
                onCamera = {
                    sheet = null
                    askCamera.launch(Manifest.permission.CAMERA)
                },
                onGallery = {
                    sheet = null
                    pickImage.launch("image/*")
                },
                onDocument = {
                    sheet = null
                    pickFile.launch("*/*")
                },
                onModel = {
                    viewModel.loadModels()
                    sheet = ComposerSheet.Model
                },
                onReasoning = { sheet = ComposerSheet.Reasoning },
            )
        }

        ComposerSheet.Model -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = stringResource(R.string.sheet_model),
                loading = state.loadingModels,
                rows = state.models.map { option ->
                    PickerRow(
                        label = option.id,
                        detail = option.provider,
                        selected = option.id == state.sessionModel,
                    ) {
                        viewModel.selectModel(option)
                        sheet = null
                    }
                },
            )
        }

        ComposerSheet.Reasoning -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PickerSheet(
                title = stringResource(R.string.sheet_reasoning),
                loading = false,
                rows = REASONING_LEVELS.map { (value, label) ->
                    PickerRow(
                        label = stringResource(label),
                        detail = if (value.isBlank()) stringResource(R.string.reasoning_use_profile) else null,
                        selected = value == state.reasoningEffort,
                    ) {
                        viewModel.setReasoningEffort(value)
                        sheet = null
                    }
                },
            )
        }

        null -> Unit
    }

    if (!composerExpanded && draft.isBlank() && state.attachments.isEmpty() && state.voice == VoiceStatus.Idle) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(CoreHubTokens.Radius.composer),
            color = CoreHub.palette.bgComposer,
            contentColor = CoreHub.palette.textPrimary,
            tonalElevation = 0.dp,
            shadowElevation = CoreHubTokens.Shadow.composer,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { composerExpanded = true }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.composer_expand))
                }
                Text(
                    stringResource(R.string.composer_hint),
                    modifier = Modifier.weight(1f).clickable { composerExpanded = true }.padding(vertical = 10.dp),
                    style = CoreHubTextStyles.input,
                    color = CoreHub.palette.textMuted,
                )
                ComposerActionButton(state, draft, onSend, viewModel) {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 7.dp),
        shape = RoundedCornerShape(CoreHubTokens.Radius.composer),
        color = CoreHub.palette.bgComposer,
        contentColor = CoreHub.palette.textPrimary,
        tonalElevation = 0.dp,
        shadowElevation = if (fieldFocused) CoreHubTokens.Shadow.composerFocused else CoreHubTokens.Shadow.composer,
    ) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (state.attachments.isNotEmpty() || state.attaching) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.attachments.forEach { file ->
                    AssistChip(
                        onClick = { viewModel.removeAttachment(file) },
                        label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove)) },
                    )
                }
                if (state.attaching) AssistChip(onClick = {}, label = { Text(stringResource(R.string.composer_uploading)) })
            }
        }

        if (state.voice != VoiceStatus.Idle) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state.voice) {
                    VoiceStatus.Listening -> {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(if (state.voiceViaServer) R.string.composer_recording else R.string.composer_listening),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.cancelVoiceInput() }) { Text(stringResource(R.string.action_cancel)) }
                    }
                    VoiceStatus.Transcribing -> {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.composer_transcribing),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    VoiceStatus.Error -> {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            state.error ?: stringResource(R.string.composer_voice_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.resetVoice() }) { Text(stringResource(R.string.action_dismiss)) }
                    }
                    VoiceStatus.Idle -> Unit
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = { value ->
                    field = value
                    if (value.text != draft) onDraftChange(value.text)
                },
                placeholder = { Text(stringResource(R.string.composer_hint), style = CoreHubTextStyles.input, color = CoreHub.palette.textMuted) },
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if (fieldFocused && !it.isFocused && draft.isBlank() && state.attachments.isEmpty()) {
                        composerExpanded = false
                    }
                    fieldFocused = it.isFocused
                },
                // Never below 16 sp on phones; dir=auto per DESIGN-SPEC.
                textStyle = CoreHubTextStyles.input.copy(color = CoreHub.palette.textPrimary, textDirection = TextDirection.Content),
                maxLines = 5,
                shape = RoundedCornerShape(CoreHubTokens.Radius.bubble),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CoreHub.palette.bgInput,
                    unfocusedContainerColor = CoreHub.palette.bgInput,
                    focusedBorderColor = CoreHub.palette.accent,
                    unfocusedBorderColor = CoreHub.palette.inputBorder,
                    cursorColor = CoreHub.palette.accent,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.size(CoreHubTokens.Metrics.composerButton),
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, CoreHub.palette.inputBorder),
            ) {
                IconButton(onClick = { sheet = ComposerSheet.Options }, enabled = !state.sending) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.composer_more), modifier = Modifier.size(18.dp))
                }
            }
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToolbarChip(
                    icon = Icons.Filled.Person,
                    label = state.openSession?.profile ?: state.activeProfile.ifBlank { "default" },
                    onClick = {},
                )
                ToolbarChip(
                    icon = Icons.Filled.Psychology,
                    label = reasoningLabel(state.reasoningEffort),
                ) { sheet = ComposerSheet.Reasoning }
                ToolbarChip(
                    icon = Icons.Filled.ModelTraining,
                    label = state.sessionModel ?: stringResource(R.string.sheet_model),
                ) {
                    viewModel.loadModels()
                    sheet = ComposerSheet.Model
                }
                if (state.speaking) {
                    AssistChip(
                        onClick = { viewModel.stopSpeaking() },
                        label = { Text(stringResource(R.string.voice_stop_reply)) },
                        leadingIcon = { Icon(Icons.Filled.Stop, null, Modifier.size(15.dp)) },
                    )
                }
                ContextUsage(state)
            }
            ComposerActionButton(state, draft, onSend, viewModel) {
                askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    }
}

@Composable
internal fun ContextUsage(state: UiState) {
    val ratio = if (state.contextWindow > 0) {
        (state.contextTokens.toFloat() / state.contextWindow.toFloat()).coerceIn(0f, 1f)
    } else 0f
    // 11 sp muted; amber above 80 %; bar 42×4 on phones (DESIGN-SPEC context indicator).
    val color = if (ratio > CoreHubTokens.Metrics.contextWarnRatio) CoreHubTokens.Metrics.contextWarn else CoreHub.palette.textMuted
    Column(modifier = Modifier.widthIn(min = 84.dp, max = 122.dp)) {
        Text(
            if (state.loadingContext) stringResource(R.string.context_loading)
            else if (state.contextWindow > 0) stringResource(
                R.string.context_usage,
                compactNumber(state.contextTokens),
                compactNumber(state.contextWindow),
            ) else stringResource(R.string.context_unknown),
            style = CoreHubTextStyles.meta.copy(textDirection = TextDirection.Ltr),
            color = color,
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.width(CoreHubTokens.Metrics.contextBarWidth).height(CoreHubTokens.Metrics.contextBarHeight).clip(RoundedCornerShape(CoreHubTokens.Radius.pill)),
            color = color,
            trackColor = CoreHub.palette.borderLight,
        )
    }
}

internal fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(Locale.US, value / 1_000.0)
    else -> value.toString()
}.replace(".0", "")

internal enum class ComposerSheet { Options, Model, Reasoning }

internal val REASONING_LEVELS = listOf(
    "" to R.string.reasoning_default,
    "low" to R.string.reasoning_low,
    "medium" to R.string.reasoning_medium,
    "high" to R.string.reasoning_high,
    "xhigh" to R.string.reasoning_extra_high,
)

internal val VOICE_INPUT_MODES = listOf(
    Store.VOICE_INPUT_DEVICE to R.string.voice_input_device,
    Store.VOICE_INPUT_SERVER to R.string.voice_input_server,
)

@Composable
internal fun voiceInputLabel(mode: String): String = stringResource(
    VOICE_INPUT_MODES.firstOrNull { it.first == mode }?.second ?: R.string.voice_input_device,
)

internal val APPEARANCE_LEVELS = listOf(
    "system" to R.string.appearance_system,
    "light" to R.string.appearance_light,
    "dark" to R.string.appearance_dark,
)

@Composable
internal fun reasoningLabel(effort: String): String = stringResource(
    REASONING_LEVELS.firstOrNull { it.first == effort }?.second ?: R.string.reasoning_default,
)

@Composable
internal fun appearanceLabel(appearance: String): String = stringResource(
    APPEARANCE_LEVELS.firstOrNull { it.first == appearance }?.second ?: R.string.appearance_system,
)

internal const val PHONE_REPOSITORY_URL = "https://github.com/twuijri/hermes-studio-mobile"
internal const val STUDIO_REPOSITORY_URL = "https://github.com/EKKOLearnAI/hermes-studio"

@Composable
internal fun ComposerActionButton(
    state: UiState,
    draft: String,
    onSend: () -> Unit,
    viewModel: AppViewModel,
    onRecord: () -> Unit,
) {
    val hasPayload = draft.isNotBlank() || state.attachments.isNotEmpty()
    val listening = state.voice == VoiceStatus.Listening
    val active = hasPayload || listening || state.sending
    val palette = CoreHub.palette
    val background = if (active) palette.accent else palette.bgSecondary
    val tint = if (active) palette.textOnAccent else palette.textPrimary

    // 30 px circle, accent while there is something to send; a square stop while streaming.
    Box(
        modifier = Modifier
            .size(CoreHubTokens.Metrics.composerButton)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            listening -> IconButton(onClick = { viewModel.stopVoiceInput() }) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = stringResource(if (state.voiceViaServer) R.string.composer_stop else R.string.composer_stop_listening),
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            state.voice == VoiceStatus.Transcribing -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            state.sending -> IconButton(onClick = { viewModel.stopRun() }) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.conversation_stop), tint = tint, modifier = Modifier.size(20.dp))
            }
            hasPayload -> IconButton(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.composer_send), tint = tint, modifier = Modifier.size(20.dp))
            }
            else -> IconButton(onClick = onRecord) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.composer_record),
                    tint = if (state.voice == VoiceStatus.Error) MaterialTheme.colorScheme.error else tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun ToolbarChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // Pill (radius 999) on the input border colour; labels are capped like the web's model pill.
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CoreHubTokens.Radius.pill))
            .background(palette.segmentTrack)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Text("⌄", style = CoreHubTextStyles.meta, color = palette.textMuted)
    }
}

/** The "+" sheet: attachments first, then the per-conversation controls. */
@Composable
internal fun OptionsSheet(
    state: UiState,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDocument: () -> Unit,
    onModel: () -> Unit,
    onReasoning: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        SheetTitle(stringResource(R.string.sheet_add))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachOption(Icons.Filled.PhotoCamera, stringResource(R.string.sheet_camera), onCamera)
            AttachOption(Icons.Filled.Image, stringResource(R.string.sheet_gallery), onGallery)
            AttachOption(Icons.AutoMirrored.Filled.InsertDriveFile, stringResource(R.string.sheet_file), onDocument)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SheetTitle(stringResource(R.string.sheet_conversation))
        SheetRow(
            icon = Icons.Filled.ModelTraining,
            label = stringResource(R.string.sheet_model),
            detail = state.sessionModel ?: stringResource(R.string.sheet_profile_default),
            onClick = onModel,
        )
        SheetRow(
            icon = Icons.Filled.Psychology,
            label = stringResource(R.string.sheet_reasoning),
            detail = reasoningLabel(state.reasoningEffort),
            onClick = onReasoning,
        )
    }
}

internal data class PickerRow(
    val label: String,
    val detail: String?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun PickerSheet(title: String, loading: Boolean, rows: List<PickerRow>) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        SheetTitle(title)
        if (loading) LoadingRow()
        if (!loading && rows.isEmpty()) {
            Text(
                stringResource(R.string.sheet_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = row.onClick)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    row.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.selected) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_selected))
                }
            }
        }
    }
}

@Composable
internal fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
internal fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Cache-backed target for a camera capture, shared through the FileProvider. */
internal fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Reads a picked document through the content resolver and hands it to the upload. */
internal fun readAndAttach(
    context: Context,
    uri: Uri,
    viewModel: AppViewModel,
    fallbackName: String? = null,
) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: if (fallbackName?.endsWith(".jpg") == true) "image/jpeg" else "application/octet-stream"
    var name = fallbackName ?: "attachment"
    runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index) ?: name
        }
    }
    val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    if (bytes == null || bytes.isEmpty()) return
    viewModel.attach(bytes, name, mime)
}

