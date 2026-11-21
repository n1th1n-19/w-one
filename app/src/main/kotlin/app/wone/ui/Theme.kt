package app.wone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.wone.R
import app.wone.data.Settings

/** Flat graphite palette. No gradients anywhere; colour carries state or lighting only. */
object Pal {
    val bg = Color(0xFF0E1116)
    val surface = Color(0xFF151A21)
    val raised = Color(0xFF1C222B)
    val key = Color(0xFF232A35)
    val keyHi = Color(0xFF2E3644)
    val line = Color(0xFF2A313C)
    val text = Color(0xFFE6E9EE)
    val muted = Color(0xFF8B94A1)
    val faint = Color(0xFF5B6472)
    val danger = Color(0xFFFF5C7A)
    val ok = Color(0xFF3DDC97)
    val warn = Color(0xFFFFB224)
}

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

object Type {
    val title = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Pal.text)
    val heading = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Pal.text)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Pal.text)
    val muted = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Pal.muted)
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Pal.muted)
    val code = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Pal.text)
    val legend = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Pal.text)
}

val LocalAccent = compositionLocalOf { Pal.ok }
val LocalSettings = compositionLocalOf { Settings() }

@Composable
fun WTheme(settings: Settings, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAccent provides Color(settings.accent),
        LocalSettings provides settings,
        content = content,
    )
}
