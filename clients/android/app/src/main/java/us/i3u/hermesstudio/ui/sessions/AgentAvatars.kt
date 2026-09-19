package us.i3u.hermesstudio.ui.sessions

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.SessionSummary
import us.i3u.hermesstudio.ui.theme.CoreHubTokens

/**
 * The runtime logo shown beside a session, mirroring
 * `packages/client/src/utils/chat-agent-avatar.ts`.
 */
data class ChatAgentAvatar(val label: String, @DrawableRes val drawable: Int)

object ChatAgentAvatars {
    val hermes = ChatAgentAvatar("Hermes", R.drawable.agent_hermes)
    val ekko = ChatAgentAvatar("Ekko", R.drawable.agent_ekko)
    val claude = ChatAgentAvatar("Claude", R.drawable.agent_claude_code)
    val codex = ChatAgentAvatar("Codex", R.drawable.agent_codex)
    val pi = ChatAgentAvatar("Pi", R.drawable.agent_pi)
    val grok = ChatAgentAvatar("Grok", R.drawable.agent_grok)
    val opencode = ChatAgentAvatar("OpenCode", R.drawable.agent_opencode)
    val deepseek = ChatAgentAvatar("DeepSeek Harness", R.drawable.agent_deepseek)

    /** Same decision table as the web: runtime id first, then the session source. */
    fun forRuntime(runtimeId: String?, source: String? = null): ChatAgentAvatar {
        return when (runtimeId.orEmpty().trim().lowercase()) {
            "ekko-agent", "ekko_agent", "ekko" -> ekko
            "claude", "claude-code" -> claude
            "codex" -> codex
            "pi" -> pi
            "grok" -> grok
            "dsh" -> deepseek
            "opencode" -> opencode
            else -> if (source == "coding_agent") claude else hermes
        }
    }

    fun forSession(session: SessionSummary?): ChatAgentAvatar =
        if (session == null) ekko else forRuntime(session.agentId, session.source)
}

/**
 * 18 px circle with a 1 px white border; while the session is streaming the
 * border becomes the web's animated rainbow ring.
 */
@Composable
fun AgentAvatar(
    avatar: ChatAgentAvatar,
    modifier: Modifier = Modifier,
    size: Dp = CoreHubTokens.Metrics.agentAvatar,
    streaming: Boolean = false,
) {
    Box(modifier = modifier.size(size).clip(CircleShape)) {
        if (streaming) {
            val transition = rememberInfiniteTransition(label = "agent-ring")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                label = "agent-ring-angle",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .rotate(angle)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77),
                                Color(0xFF4D96FF), Color(0xFFB66DFF), Color(0xFFFF6B6B),
                            ),
                        ),
                        CircleShape,
                    ),
            )
        } else {
            Box(Modifier.fillMaxSize().border(1.dp, Color.White, CircleShape))
        }
        Image(
            painter = painterResource(avatar.drawable),
            contentDescription = avatar.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(CircleShape),
        )
    }
}
