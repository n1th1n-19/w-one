package app.wone.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spring used for every pressable thing: quick down, small overshoot on release. */
fun <T> pressSpring() = spring<T>(dampingRatio = 0.38f, stiffness = 650f)

/** Reports down and up immediately (no tap delay) so sound and haptics land with the finger. */
fun Modifier.pressable(
    onDown: () -> Unit = {},
    onUp: () -> Unit = {},
    onPressed: (Boolean) -> Unit = {},
): Modifier = composed {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val changed by rememberUpdatedState(onPressed)
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            changed(true); down()
            // Up or cancel both end the press.
            while (true) {
                val e = awaitPointerEvent()
                if (e.changes.none { it.pressed }) break
            }
            changed(false); up()
        }
    }
}

@Composable
fun Tx(text: String, style: TextStyle = Type.body, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = if (color == Color.Unspecified) style else style.copy(color = color))
}

@Composable
fun Ico(icon: ImageVector, tint: Color = Pal.text, size: Dp = 22.dp, modifier: Modifier = Modifier, description: String? = null) {
    Image(
        icon, description, modifier.size(size).let { m -> if (description != null) m.semantics { contentDescription = description } else m },
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Flat panel: solid fill and a hairline border. */
fun Modifier.panel(fill: Color = Pal.surface, radius: Dp = 14.dp, border: Color = Pal.line): Modifier =
    this.clip(RoundedCornerShape(radius)).background(fill).border(1.dp, border, RoundedCornerShape(radius))

/** Button with spring press feedback. [onDown] and [onUp] are for sound and haptics. */
@Composable
fun WButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    onDown: () -> Unit = {},
    onUp: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, pressSpring(), label = "press")
    val accent = LocalAccent.current
    val fill = if (primary) accent else Pal.raised
    val ink = if (primary) Pal.bg else Pal.text
    Box(
        modifier
            .scale(s)
            .alpha(if (enabled) 1f else 0.4f)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .let { if (!primary) it.border(1.dp, Pal.line, RoundedCornerShape(12.dp)) else it }
            .semantics { role = Role.Button }
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        pressed = true; onDown()
                        val ok = tryAwaitRelease()
                        pressed = false; onUp()
                        if (ok) onClick()
                    })
                } else Modifier,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Ico(icon, ink, 20.dp)
            Tx(label, Type.heading.copy(color = ink, fontSize = 15.sp))
        }
    }
}

private fun Modifier.alpha(a: Float) = this.graphicsLayer { alpha = a }

@Composable
fun WSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val accent = LocalAccent.current
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(0.55f, Spring.StiffnessMedium), label = "sw")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) accent else Pal.raised)
            .border(1.dp, if (checked) accent else Pal.line, RoundedCornerShape(14.dp))
            .semantics { role = Role.Switch; contentDescription = label }
            .pointerInput(checked) { detectTapGestures { onChange(!checked) } },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .graphicsLayer { translationX = pos * 20.dp.toPx() }
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) Pal.bg else Pal.muted),
        )
    }
}

@Composable
fun WSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, label: String) {
    val accent = LocalAccent.current
    var width by remember { mutableStateOf(1f) }
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    fun at(x: Float) = onChange(range.start + (x / width).coerceIn(0f, 1f) * (range.endInclusive - range.start))
    Box(
        modifier
            .height(40.dp)
            .semantics { contentDescription = label }
            .pointerInput(range) { detectTapGestures { at(it.x) } }
            .pointerInput(range) { detectHorizontalDragGestures(onDragStart = { at(it.x) }) { c, _ -> at(c.position.x) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            width = size.width
            val y = size.height / 2
            drawLine(Pal.line, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(accent, Offset(0f, y), Offset(size.width * frac, y), 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(accent, 9.dp.toPx(), Offset(size.width * frac, y))
        }
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Pal.raised).border(1.dp, Pal.line, RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((v, text) in options) {
            val on = v == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) accent else Color.Transparent)
                    .semantics { role = Role.RadioButton }
                    .pointerInput(v) { detectTapGestures { onSelect(v) } },
                contentAlignment = Alignment.Center,
            ) { Tx(text, Type.label.copy(color = if (on) Pal.bg else Pal.text, fontSize = 12.sp)) }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Tx(text.uppercase(), Type.label, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun GapW(w: Dp) = Spacer(Modifier.width(w))
