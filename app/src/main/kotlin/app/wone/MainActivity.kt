package app.wone

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.wone.ui.AirMouseScreen
import app.wone.ui.GamepadEditScreen
import app.wone.ui.GamepadScreen
import app.wone.ui.HomeShell
import app.wone.ui.IntroScreen
import app.wone.ui.KeyboardScreen
import app.wone.ui.PairingScreen
import app.wone.ui.SplashScreen
import app.wone.ui.Pal
import app.wone.ui.Screen
import app.wone.ui.TrackpadScreen
import app.wone.ui.WTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        val app = wone
        val loaded by app.settings.flow.collectAsState(initial = null)
        val s = loaded
        if (s == null) { Box(Modifier.fillMaxSize().background(Pal.bg)); return }

        var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val view = LocalView.current

        LaunchedEffect(screen) {
            requestedOrientation = if (screen.landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            // Modes hide the system bars for room; a swipe from the edge brings them back.
            val c = WindowCompat.getInsetsController(window, view)
            if (screen.landscape) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else c.show(WindowInsetsCompat.Type.systemBars())
            if (screen.landscape && s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        BackHandler(enabled = screen != Screen.Home && screen != Screen.Intro && screen != Screen.Splash) {
            screen = when (screen) {
                Screen.GamepadEdit -> Screen.Gamepad
                else -> Screen.Home
            }
        }

        WTheme(s) {
            val nav: (Screen) -> Unit = { screen = it }
            when (screen) {
                Screen.Splash -> SplashScreen { screen = if (s.onboardingDone) Screen.Home else Screen.Intro }
                Screen.Intro -> IntroScreen { scope.launch { app.settings.update { it.copy(onboardingDone = true) } }; screen = Screen.Home }
                Screen.Home, Screen.Settings -> HomeShell(screen, nav)
                Screen.Pairing -> PairingScreen(nav)
                Screen.Keyboard -> KeyboardScreen(nav)
                Screen.Trackpad -> TrackpadScreen(nav)
                Screen.Air -> AirMouseScreen(nav)
                Screen.Gamepad -> GamepadScreen(nav)
                Screen.GamepadEdit -> GamepadEditScreen(nav)
            }
        }
    }
}
