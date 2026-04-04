package ro.infotb.modern.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import ro.infotb.modern.data.model.TransportType

val BusColor = Color(0xFF1E88E5)
val TramColor = Color(0xFFE53935)
val CableCarColor = Color(0xFF43A047)
val SubwayColor = Color(0xFFF57C00)
val RealtimeColor = Color(0xFF43A047)
val ScheduledColor = Color(0xFF7E8CA0)

val LocalMuted = compositionLocalOf { Color(0xFF9E9E9E) }

fun transportColor(type: TransportType): Color = when (type) {
    TransportType.BUS -> BusColor
    TransportType.TRAM -> TramColor
    TransportType.CABLE_CAR -> CableCarColor
    TransportType.SUBWAY -> SubwayColor
}

fun parseColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val colorLong = cleaned.toLong(16)
    return Color(colorLong or 0xFF000000)
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF526070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF6A5778),
    onTertiary = Color.White,
    background = Color(0xFFFAFAFD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFAFAFD),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F6),
    surfaceContainer = Color(0xFFEDEDF0),
    surfaceContainerHigh = Color(0xFFE7E8EB),
    surfaceContainerHighest = Color(0xFFE2E2E5),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1C20),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E)
)

@Composable
fun StbTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val mutedColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)

    SystemAppearance(isDark)

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        CompositionLocalProvider(
            LocalMuted provides mutedColor,
            content = content
        )
    }
}
