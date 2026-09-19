package us.i3u.hermesstudio

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared dark palette: visually aligned with iPhone while using Material roles. */
private val StudioDarkColors = darkColorScheme(
    primary = Color(0xFFEEEEEB), onPrimary = Color(0xFF191A1A),
    primaryContainer = Color(0xFF262828), onPrimaryContainer = Color(0xFFF2F2EF),
    secondary = Color(0xFFB8BCBA), onSecondary = Color(0xFF181919),
    secondaryContainer = Color(0xFF202121), onSecondaryContainer = Color(0xFFE7E9E8),
    tertiary = Color(0xFF8FCBA5),
    background = Color(0xFF121313), onBackground = Color(0xFFF1F2F1),
    surface = Color(0xFF181919), onSurface = Color(0xFFF1F2F1),
    surfaceVariant = Color(0xFF262828), onSurfaceVariant = Color(0xFFA1A5A3),
    surfaceContainerLowest = Color(0xFF121313), surfaceContainerLow = Color(0xFF181919),
    surfaceContainer = Color(0xFF1E1F1F), surfaceContainerHigh = Color(0xFF202121),
    surfaceContainerHighest = Color(0xFF262828), outline = Color(0xFF383A39),
    outlineVariant = Color(0xFF2D2F2E), error = Color(0xFFEF8C87),
    errorContainer = Color(0xFF351F1F), onErrorContainer = Color(0xFFFFDAD7),
)

private val StudioLightColors = lightColorScheme(
    primary = Color(0xFF333333),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1F1F1),
    onPrimaryContainer = Color(0xFF202121),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF00677A),
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF191A1A),
    surface = Color.White, onSurface = Color(0xFF191A1A),
    surfaceVariant = Color(0xFFF1F1F1), onSurfaceVariant = Color(0xFF626765),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F0F0), surfaceContainerHighest = Color(0xFFF1F1F1),
    outline = Color(0xFFE0E0E0), outlineVariant = Color(0xFFEBEBEB),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

private val StudioTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
)

@Composable
fun HermesTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val dark = when (appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) StudioDarkColors else StudioLightColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}

/** Studio shows a short clock for today and a date for older rows. */
fun formatStamp(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val millis = raw.toLongOrNull()
    if (millis != null) {
        val normalized = if (millis < 100_000_000_000L) millis * 1000 else millis
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { timeInMillis = normalized }
        val sameDay = now.get(java.util.Calendar.ERA) == then.get(java.util.Calendar.ERA) &&
            now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
        return android.text.format.DateFormat.format(if (sameDay) "h:mm a" else "yyyy-MM-dd", normalized).toString()
    }
    // ISO 8601: keep the clock when the day is today, otherwise the date.
    val date = raw.take(10)
    val time = raw.drop(11).take(5)
    val today = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString()
    return if (date == today && time.isNotBlank()) time else date
}
