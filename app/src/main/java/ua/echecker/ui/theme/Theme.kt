package ua.echecker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette idea: amber = power on, deep navy = power off.
 * The app-specific colours that Material's scheme has no slot for live in [PowerColors].
 */
@Immutable
data class PowerColors(
    /** Fill for "power on" time on timelines. */
    val on: Color,
    /** Fill for outage time on timelines and interval pills. */
    val off: Color,
    val onOff: Color,
    /** Hero card gradient while power is on. */
    val heroOn: List<Color>,
    val onHeroOn: Color,
    /** Hero card gradient while power is off. */
    val heroOff: List<Color>,
    val onHeroOff: Color,
    /** Accent used on top of [heroOff]. */
    val heroOffAccent: Color,
    /** Neutral hero gradient (no data, no schedule). */
    val heroNeutral: List<Color>,
    val onHeroNeutral: Color,
    val nowMarker: Color,
)

private val Amber = Color(0xFFF5B000)
private val AmberLight = Color(0xFFFFD25C)
private val Navy = Color(0xFF232A44)
private val NavyDeep = Color(0xFF11152A)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE09A00),
    onPrimary = Color(0xFF1F1500),
    primaryContainer = Color(0xFFFFE6A3),
    onPrimaryContainer = Color(0xFF3A2800),
    secondary = Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1F2),
    onSecondaryContainer = NavyDeep,
    background = Color(0xFFF5F2EA),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFEDE8DB),
    onSurfaceVariant = Color(0xFF5E5848),
    outline = Color(0xFFD3CBB6),
    outlineVariant = Color(0xFFE6E0D0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFC233),
    onPrimary = Color(0xFF2A1C00),
    primaryContainer = Color(0xFF4A3800),
    onPrimaryContainer = Color(0xFFFFE6A3),
    secondary = Color(0xFFB9C2E6),
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFF2E3654),
    onSecondaryContainer = Color(0xFFDDE1F2),
    background = Color(0xFF0E111C),
    onBackground = Color(0xFFE6E3DA),
    surface = Color(0xFF171B29),
    onSurface = Color(0xFFE6E3DA),
    surfaceVariant = Color(0xFF232838),
    onSurfaceVariant = Color(0xFFAAA697),
    outline = Color(0xFF3A4054),
    outlineVariant = Color(0xFF2A2F40),
)

private val LightPower = PowerColors(
    on = Color(0xFFFFD86B),
    off = Navy,
    onOff = Color.White,
    heroOn = listOf(AmberLight, Amber),
    onHeroOn = Color(0xFF1F1500),
    heroOff = listOf(Color(0xFF2E3758), NavyDeep),
    onHeroOff = Color.White,
    heroOffAccent = AmberLight,
    heroNeutral = listOf(Color(0xFFEDE8DB), Color(0xFFDCD5C2)),
    onHeroNeutral = Color(0xFF1C1B17),
    nowMarker = Color(0xFFD9480F),
)

private val DarkPower = PowerColors(
    on = Color(0xFFE0A21A),
    off = Color(0xFF3A4260),
    onOff = Color(0xFFE6E3DA),
    heroOn = listOf(AmberLight, Color(0xFFE09A00)),
    onHeroOn = Color(0xFF1F1500),
    heroOff = listOf(Color(0xFF2B3354), Color(0xFF151A30)),
    onHeroOff = Color.White,
    heroOffAccent = AmberLight,
    heroNeutral = listOf(Color(0xFF262B3C), Color(0xFF1B1F2D)),
    onHeroNeutral = Color(0xFFE6E3DA),
    nowMarker = Color(0xFFFF7A45),
)

val LocalPowerColors = staticCompositionLocalOf { LightPower }

@Composable
fun EcheckerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPowerColors provides if (dark) DarkPower else LightPower,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

object Power {
    val colors: PowerColors
        @Composable get() = LocalPowerColors.current
}
