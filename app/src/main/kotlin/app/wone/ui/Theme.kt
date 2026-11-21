package app.wone.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.wone.R
import app.wone.data.Settings
import app.wone.data.ThemeMode

/** Neutral Material 3 schemes with no hue: used on older phones and when dynamic colour is off. */
private val NeutralDark = darkColorScheme(
    primary = Color(0xFFC4C7C5), onPrimary = Color(0xFF2D3030),
    primaryContainer = Color(0xFF444746), onPrimaryContainer = Color(0xFFE3E3E3),
    secondary = Color(0xFFC4C7C5), onSecondary = Color(0xFF2D3030),
    secondaryContainer = Color(0xFF3A3D3F), onSecondaryContainer = Color(0xFFE3E3E3),
    tertiary = Color(0xFFC4C7C5), onTertiary = Color(0xFF2D3030),
    background = Color(0xFF131314), onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314), onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746), onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color(0xFF0E0E0F), surfaceContainerLow = Color(0xFF1B1B1C),
    surfaceContainer = Color(0xFF1E1F20), surfaceContainerHigh = Color(0xFF282A2C), surfaceContainerHighest = Color(0xFF333537),
    outline = Color(0xFF8E918F), outlineVariant = Color(0xFF444746),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

private val NeutralLight = lightColorScheme(
    primary = Color(0xFF444746), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E3E1), onPrimaryContainer = Color(0xFF1F1F1F),
    secondary = Color(0xFF444746), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E3E1), onSecondaryContainer = Color(0xFF1F1F1F),
    tertiary = Color(0xFF444746), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFAFAFA), onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E1), onSurfaceVariant = Color(0xFF444746),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF3F3F3),
    surfaceContainer = Color(0xFFEDEDED), surfaceContainerHigh = Color(0xFFE7E7E7), surfaceContainerHighest = Color(0xFFE1E1E1),
    outline = Color(0xFF747775), outlineVariant = Color(0xFFC4C7C5),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
)

/**
 * The active colours as short names for the custom drawing code (keys, pads, canvases).
 * Set once by [WTheme] from the Material 3 scheme, so everything follows the phone's colours.
 */
object Pal {
    var cs: ColorScheme = NeutralDark
    val bg get() = cs.background
    val surface get() = cs.surfaceContainer
    val raised get() = cs.surfaceContainerHigh
    val key get() = cs.surfaceContainerHighest
    val keyHi get() = cs.secondaryContainer
    val line get() = cs.outlineVariant
    val text get() = cs.onSurface
    val muted get() = cs.onSurfaceVariant
    val faint get() = cs.outline
    val danger get() = cs.error
    val ok get() = cs.primary
    val warn get() = cs.tertiary
    val onAccent get() = cs.onPrimary
}

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Text styles read the colour at use time, so they follow the scheme. */
object Type {
    val title get() = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Pal.text)
    val heading get() = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Pal.text)
    val body get() = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Pal.text)
    val muted get() = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Pal.muted)
    val label get() = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Pal.muted)
    val code get() = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Pal.text)
    val legend get() = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Pal.text)
}

val LocalAccent = compositionLocalOf { Color(0xFFC4C7C5) }
val LocalSettings = compositionLocalOf { Settings() }

@Composable
fun WTheme(settings: Settings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (settings.theme) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> NeutralDark
        else -> NeutralLight
    }
    Pal.cs = scheme
    // The custom drawing code reads Pal directly, so rebuild the tree when the scheme changes.
    key(dark, settings.dynamicColor) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalAccent provides scheme.primary,
                LocalSettings provides settings,
                content = content,
            )
        }
    }
}
