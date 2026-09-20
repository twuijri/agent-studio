package us.i3u.hermesstudio.ui.chat

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.AudioLevelMeter
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.SpeechLanguages
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.ui.theme.CoreHub
import us.i3u.hermesstudio.ui.theme.CoreHubTokens

/**
 * The row the pills turn into while dictating: ✕ · waveform · ■ · ↑.
 *
 * Modelled on the Claude app's recording mode, and the same strip as the iOS
 * client's. The ✕ sits at the leading edge and the ↑ at the trailing edge in
 * both layout directions — the Row mirrors on its own under RTL — while the
 * waveform between them is a timeline and stays pinned LTR, newest bar on
 * the right. The three controls are the same 30 dp circles the composer
 * already uses, so a TalkBack user meets the same three actions a sighted
 * one does, and the waveform describes the take's language and level.
 */
@Composable
internal fun RecordingStripRow(
    state: UiState,
    viewModel: AppViewModel,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = CoreHub.palette
    val levels by viewModel.dictationLevels.collectAsStateWithLifecycle()
    val percent = ((levels.lastOrNull() ?: 0f) * 100).roundToInt()
    val language = takeLanguageName(state)
    val recording = if (language != null) {
        stringResource(R.string.composer_recording_in, language)
    } else {
        stringResource(R.string.composer_recording_label)
    }
    val description = recording +
        stringResource(R.string.speech_language_separator) +
        stringResource(R.string.composer_recording_level, percent)
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StripButton(
            label = stringResource(R.string.composer_dictation_cancel),
            hint = stringResource(R.string.composer_dictation_cancel_hint),
            background = palette.bgCard,
            border = BorderStroke(1.dp, palette.borderLight),
            onClick = onCancel,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
        }
        RecordingWaveform(levels = levels, description = description, modifier = Modifier.weight(1f))
        StripButton(
            label = stringResource(R.string.composer_dictation_stop),
            hint = stringResource(R.string.composer_dictation_stop_hint),
            background = palette.bgCard,
            border = BorderStroke(1.dp, palette.borderLight),
            onClick = onStop,
        ) {
            Box(modifier = Modifier.size(12.dp).background(palette.textPrimary, RoundedCornerShape(2.dp)))
        }
        StripButton(
            label = stringResource(R.string.composer_send),
            hint = null,
            background = palette.accent,
            border = null,
            onClick = onSend,
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = palette.textOnAccent, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * The language the running take is listening in, by its endonym: what the
 * engine reported it detected, else what it was started with. Null when no
 * language was named at all, and the strip then says only "Recording".
 */
internal fun takeLanguageName(state: UiState): String? =
    state.detectedLanguage.ifBlank { state.takeLanguage }.takeIf { it.isNotBlank() }?.let(SpeechLanguages::isolatedEndonym)

/**
 * One of the strip's 30 dp circles. [label] is what TalkBack names it and
 * [hint] what it says the tap does — the iOS accessibility hint, carried
 * here as the click action's label.
 */
@Composable
private fun StripButton(
    label: String,
    hint: String?,
    background: Color,
    border: BorderStroke?,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(CoreHubTokens.Metrics.composerButton)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .semantics { contentDescription = label }
            .clickable(onClickLabel = hint, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * The live waveform: one bar per frame of the level history, newest last on
 * the right, drawn in the accent. A bar that heard nothing is a dot at 40 %
 * so the strip still reads as a strip while the room is quiet.
 *
 * Pinned LTR whatever the interface direction: it is a timeline, and time
 * does not mirror. The Row around it still does, so under RTL the newest
 * bar sits beside the ✕ rather than the ■.
 */
@Composable
internal fun RecordingWaveform(levels: List<Float>, description: String, modifier: Modifier = Modifier) {
    val palette = CoreHub.palette
    val full = palette.accent
    val idle = palette.accent.copy(alpha = CoreHubTokens.Alpha.WAVEFORM_IDLE)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = modifier
                .height(CoreHubTokens.Metrics.waveformHeight)
                .padding(horizontal = CoreHubTokens.Metrics.waveformPaddingH)
                .semantics { contentDescription = description },
        ) {
            val count = levels.size
            if (count == 0) return@Canvas
            val slot = size.width / count
            val barWidth = min(CoreHubTokens.Metrics.waveformBar.toPx(), slot * 0.5f)
            val dot = max(CoreHubTokens.Metrics.waveformDot.toPx(), barWidth)
            levels.forEachIndexed { index, level ->
                val centre = slot * index + slot / 2f
                val height = max(dot, level * size.height)
                val active = level >= AudioLevelMeter.IDLE_THRESHOLD
                drawRoundRect(
                    color = if (active) full else idle,
                    topLeft = Offset(centre - barWidth / 2f, (size.height - height) / 2f),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }
    }
}

/**
 * Whether the owner has turned animations off in the system settings
 * (Developer options → Animator duration scale, or the accessibility
 * "remove animations" switch, which sets the same scale). Compose does not
 * read it on its own, so the composer's toolbar transition asks here.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
