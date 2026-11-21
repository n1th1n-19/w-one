package app.wone.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.wone.data.HapticLevel

/**
 * Touch haptics. "Rich" uses the phone's haptic primitives when it has them, "Light" only the
 * system tick, "Off" nothing. Every call is a no-op when the phone has no vibrator.
 */
class Haptics(context: Context) {

    @Volatile var level: HapticLevel = HapticLevel.Rich

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private val primitives: Boolean = Build.VERSION.SDK_INT >= 31 && vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        VibrationEffect.Composition.PRIMITIVE_THUD,
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
    ) == true

    private fun play(effect: VibrationEffect?) {
        val v = vibrator ?: return
        if (effect == null || level == HapticLevel.Off) return
        if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        else v.vibrate(effect)
    }

    private fun primitive(id: Int, scale: Float): VibrationEffect? =
        if (primitives && Build.VERSION.SDK_INT >= 31) compose(id, scale) else null

    @androidx.annotation.RequiresApi(30)
    private fun compose(id: Int, scale: Float): VibrationEffect =
        VibrationEffect.startComposition().addPrimitive(id, scale).compose()

    private fun predefined(id: Int): VibrationEffect? =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id) else VibrationEffect.createOneShot(8, 120)

    /** A keyboard key going down: short, light. */
    fun key() = play(
        if (level == HapticLevel.Rich) primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f) ?: predefined(VibrationEffect.EFFECT_CLICK)
        else predefined(VibrationEffect.EFFECT_CLICK),
    )

    /** Face buttons, bumpers, mouse buttons: a firm click. */
    fun click() = play(
        if (level == HapticLevel.Rich) primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f) ?: predefined(VibrationEffect.EFFECT_CLICK)
        else predefined(VibrationEffect.EFFECT_TICK),
    )

    /** A lighter touch for taps on a pad surface. */
    fun tap() = play(
        if (level == HapticLevel.Rich) primitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.5f) ?: predefined(VibrationEffect.EFFECT_TICK)
        else null,
    )

    /** D-pad and home: a sharper click. */
    fun sharp() = play(
        if (level == HapticLevel.Rich) primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f) ?: predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else predefined(VibrationEffect.EFFECT_CLICK),
    )

    /** Analog trigger: a rise that grows with how far it is pulled, 0..1. */
    fun trigger(pull: Float) {
        if (level != HapticLevel.Rich || !primitives || Build.VERSION.SDK_INT < 31) { if (pull > 0.95f) click(); return }
        play(rise((0.3f + 0.6f * pull).coerceIn(0f, 1f)))
    }

    @androidx.annotation.RequiresApi(30)
    private fun rise(scale: Float): VibrationEffect =
        VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale).compose()

    /** Stick pushed to its edge. */
    fun edge() = play(
        if (level == HapticLevel.Rich) primitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f) else null,
    )

    /** Stick back at centre. */
    fun settle() = play(
        if (level == HapticLevel.Rich) primitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f) else null,
    )
}
