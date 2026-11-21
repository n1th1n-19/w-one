package app.wone.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons on a 24 unit grid, one consistent weight. */
private fun icon(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (d in paths) {
        b.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

object Icons {
    val keyboard = icon(
        "keyboard",
        "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 14h10",
    )
    val trackpad = icon(
        "trackpad",
        "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M2 15h20M12 15v5",
    )
    val airMouse = icon(
        "airMouse",
        "M12 3a5 5 0 0 1 5 5v8a5 5 0 0 1-10 0V8a5 5 0 0 1 5-5z",
        "M12 3v6M7 9h10",
    )
    val gamepad = icon(
        "gamepad",
        "M7 7h10a5 5 0 0 1 5 5l-.7 4.2a2.6 2.6 0 0 1-4.6 1.2L15.5 16h-7l-1.2 1.4a2.6 2.6 0 0 1-4.6-1.2L2 12a5 5 0 0 1 5-5z",
        "M7 10v4M5 12h4M16 11h.01M18 13h.01",
    )
    val sliders = icon("sliders", "M4 7h9M17 7h3M4 17h3M11 17h9M13 5v4M7 15v4")
    val back = icon("back", "M15 5l-7 7 7 7")
    val chevron = icon("chevron", "M6 9l6 6 6-6")
    val close = icon("close", "M6 6l12 12M18 6L6 18")
    val light = icon("light", "M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5V15h8v-1.5A6 6 0 0 0 12 3z")
    val bluetooth = icon("bluetooth", "M7 7l10 10-5 5V2l5 5L7 17")
    val home = icon("home", "M4 11l8-7 8 7M6 10v10h12V10")
    val edit = icon("edit", "M4 20h4L19 9l-4-4L4 16v4zM13 7l4 4")
    val plus = icon("plus", "M12 5v14M5 12h14")
    val check = icon("check", "M5 12l5 5L20 7")
    val up = icon("up", "M6 15l6-6 6 6")
    val down = icon("down", "M6 9l6 6 6-6")
}
