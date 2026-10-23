package xzylib.base.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedbackUtils {
    /**
     * 为轻量触觉反馈提供统一入口，当前用于超级岛关闭指示器等场景。
     *
     * @param context 上下文
     * @param durationMs 振动时长，单位毫秒
     */
    fun performLightHaptic(
        context: Context,
        durationMs: Long = 30L,
    ) {
        try {
            val vibrator = obtainVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect =
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 统一获取 Vibrator，兼容 Android 12+ 的 VibratorManager。
     */
    private fun obtainVibrator(context: Context): Vibrator? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
}
