package app.wone.ui

import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val landscape: Boolean) {
    Splash(false), Intro(false), Home(false), Settings(false), Pairing(false),
    Keyboard(true), Trackpad(true), Air(true), Gamepad(true), GamepadEdit(true),
}

enum class Mode(val screen: Screen, val title: String, val hint: String, val icon: ImageVector) {
    Keyboard(Screen.Keyboard, "Keyboard", "Mini dev layout", Icons.keyboard),
    Trackpad(Screen.Trackpad, "Trackpad", "Native multitouch", Icons.trackpad),
    Air(Screen.Air, "Air mouse", "Move the phone", Icons.airMouse),
    Gamepad(Screen.Gamepad, "Gamepad", "Custom layouts", Icons.gamepad),
}
