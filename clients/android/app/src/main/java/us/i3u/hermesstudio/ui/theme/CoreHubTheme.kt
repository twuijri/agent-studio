package us.i3u.hermesstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Maps [CoreHubTokens] onto Material 3 so every existing Material component
 * (top bars, text fields, menus, sheets) renders in the Core Hub palette.
 *
 * Role mapping (light → dark follows the token table):
 * - background / surfaceContainerLowest = bg.primary
 * - surface / surfaceContainer = bg.card; surfaceContainerLow = bg.sidebar
 * - surfaceContainerHigh = bg.secondary; surfaceContainerHighest = bg.cardHover
 * - surfaceVariant = bg.secondary; onSurfaceVariant = text.secondary
 * - primary = accent; onPrimary = text.onAccent; primaryContainer = accent @ 12 %
 * - outline = border; outlineVariant = border.light
 * - error = error; errorContainer = error @ 6 %; tertiary = info
 */
private fun CoreHubTokens.Palette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = textOnAccent,
        primaryContainer = selected,
        onPrimaryContainer = textPrimary,
        inversePrimary = if (isDark) CoreHubTokens.Light.accent else CoreHubTokens.Dark.accent,
        secondary = textSecondary,
        onSecondary = textOnAccent,
        secondaryContainer = bgSecondary,
        onSecondaryContainer = textPrimary,
        tertiary = info,
        onTertiary = textOnAccent,
        tertiaryContainer = info.copy(alpha = CoreHubTokens.Alpha.SELECTED),
        onTertiaryContainer = textPrimary,
        background = bgPrimary,
        onBackground = textPrimary,
        surface = bgCard,
        onSurface = textPrimary,
        surfaceVariant = bgSecondary,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        inverseSurface = if (isDark) CoreHubTokens.Light.bgCard else CoreHubTokens.Dark.bgCard,
        inverseOnSurface = if (isDark) CoreHubTokens.Light.textPrimary else CoreHubTokens.Dark.textPrimary,
        error = error,
        onError = textOnAccent,
        errorContainer = errorSurface,
        onErrorContainer = error,
        outline = border,
        outlineVariant = borderLight,
        scrim = androidx.compose.ui.graphics.Color.Black,
        surfaceBright = bgCardHover,
        surfaceDim = bgSidebar,
        surfaceContainer = bgCard,
        surfaceContainerHigh = bgSecondary,
        surfaceContainerHighest = bgCardHover,
        surfaceContainerLow = bgSidebar,
        surfaceContainerLowest = bgPrimary,
    )
}

/**
 * Material text styles carrying the spec ramp:
 * bodyLarge = base 14/1.6, bodyMedium = 13, bodySmall = 12, titleLarge = 16/600,
 * titleMedium = 14/600, titleSmall = 13/500, labelLarge = 13, labelMedium = 12,
 * labelSmall = 11 (meta). Headlines stay for onboarding and dialogs.
 */
private val CoreHubTypography: Typography = with(CoreHubTokens.Type) {
    val defaults = Typography()
    Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = sans),
        displayMedium = defaults.displayMedium.copy(fontFamily = sans),
        displaySmall = defaults.displaySmall.copy(fontFamily = sans, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = sans),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = sans, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = sans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = defaults.titleLarge.copy(fontFamily = sans, fontSize = title, lineHeight = 22.sp, fontWeight = titleWeight),
        titleMedium = defaults.titleMedium.copy(fontFamily = sans, fontSize = navItem, lineHeight = 20.sp, fontWeight = titleWeight),
        titleSmall = defaults.titleSmall.copy(fontFamily = sans, fontSize = navTab, lineHeight = 18.sp, fontWeight = selectedWeight),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = sans, fontSize = base, lineHeight = bodyLineHeight),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = sans, fontSize = navTab, lineHeight = 20.sp),
        bodySmall = defaults.bodySmall.copy(fontFamily = sans, fontSize = author, lineHeight = 18.sp),
        labelLarge = defaults.labelLarge.copy(fontFamily = sans, fontSize = navTab, lineHeight = 18.sp, fontWeight = selectedWeight),
        labelMedium = defaults.labelMedium.copy(fontFamily = sans, fontSize = author, lineHeight = 16.sp, fontWeight = selectedWeight),
        labelSmall = defaults.labelSmall.copy(fontFamily = sans, fontSize = meta, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    )
}

/** Text styles the spec names that have no Material role. */
object CoreHubTextStyles {
    val groupHeader: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.groupHeader,
        lineHeight = 14.sp,
        fontWeight = CoreHubTokens.Type.groupHeaderWeight,
        letterSpacing = CoreHubTokens.Type.groupHeaderLetterSpacing,
    )
    /** Conversation-switch label: group-header size, no tracking. */
    val segmentLabel: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.segmentLabel,
        lineHeight = 13.sp,
    )
    val categoryTag: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.categoryTag,
        lineHeight = 14.sp,
    )
    val meta: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.meta,
        lineHeight = 16.sp,
    )
    val sessionTitle: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.sessionTitle,
        lineHeight = 18.sp,
    )
    val message: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.message,
        lineHeight = CoreHubTokens.Type.messageLineHeight,
    )
    val code: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.mono,
        fontSize = CoreHubTokens.Type.code,
        lineHeight = CoreHubTokens.Type.codeLineHeight,
    )
    val thinking: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.thinking,
        lineHeight = 19.sp,
        fontStyle = FontStyle.Italic,
    )

    /** Composer and other text inputs: never below 16 sp on phones. */
    val input: TextStyle = TextStyle(
        fontFamily = CoreHubTokens.Type.sans,
        fontSize = CoreHubTokens.Type.inputMin,
        lineHeight = 24.sp,
    )
}

private val CoreHubShapes = Shapes(
    extraSmall = RoundedCornerShape(CoreHubTokens.Radius.small),
    small = RoundedCornerShape(CoreHubTokens.Radius.medium),
    medium = RoundedCornerShape(CoreHubTokens.Radius.bubble),
    large = RoundedCornerShape(CoreHubTokens.Radius.card),
    extraLarge = RoundedCornerShape(CoreHubTokens.Radius.composer),
)

/**
 * Applies the Core Hub design system. [appearance] is the value kept in
 * Settings: "system" (default), "light" or "dark".
 */
@Composable
fun CoreHubTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val dark = when (appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val palette = if (dark) CoreHubTokens.Dark else CoreHubTokens.Light
    val selection = TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.textSelection)
    CompositionLocalProvider(
        LocalCoreHubPalette provides palette,
        LocalTextSelectionColors provides selection,
    ) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = CoreHubTypography,
            shapes = CoreHubShapes,
            content = content,
        )
    }
}
