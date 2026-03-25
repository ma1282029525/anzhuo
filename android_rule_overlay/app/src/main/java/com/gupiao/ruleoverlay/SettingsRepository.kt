package com.gupiao.ruleoverlay

import android.content.Context
import androidx.core.content.edit
import kotlin.math.roundToInt

data class OverlayConfig(
    val content: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val collapsed: Boolean,
    val textSizeSp: Float
)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): OverlayConfig {
        return OverlayConfig(
            content = prefs.getString(KEY_CONTENT, appContext.getString(R.string.default_rules)).orEmpty(),
            x = prefs.getInt(KEY_X, dpToPx(DEFAULT_X_DP)),
            y = prefs.getInt(KEY_Y, dpToPx(DEFAULT_Y_DP)),
            width = prefs.getInt(KEY_WIDTH, dpToPx(DEFAULT_WIDTH_DP)),
            height = prefs.getInt(KEY_HEIGHT, dpToPx(DEFAULT_HEIGHT_DP)),
            collapsed = prefs.getBoolean(KEY_COLLAPSED, false),
            textSizeSp = prefs.getFloat(KEY_TEXT_SIZE_SP, DEFAULT_TEXT_SIZE_SP)
        )
    }

    fun saveContent(content: String) {
        prefs.edit { putString(KEY_CONTENT, content) }
    }

    fun saveTextSize(textSizeSp: Float) {
        prefs.edit { putFloat(KEY_TEXT_SIZE_SP, textSizeSp) }
    }

    fun saveBounds(x: Int, y: Int, width: Int, height: Int) {
        prefs.edit {
            putInt(KEY_X, x)
            putInt(KEY_Y, y)
            putInt(KEY_WIDTH, width)
            putInt(KEY_HEIGHT, height)
        }
    }

    fun saveCollapsed(collapsed: Boolean) {
        prefs.edit { putBoolean(KEY_COLLAPSED, collapsed) }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * appContext.resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private const val PREFS_NAME = "rule_overlay_prefs"
        private const val KEY_CONTENT = "content"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_COLLAPSED = "collapsed"
        private const val KEY_TEXT_SIZE_SP = "text_size_sp"

        private const val DEFAULT_X_DP = 16
        private const val DEFAULT_Y_DP = 120
        private const val DEFAULT_WIDTH_DP = 300
        private const val DEFAULT_HEIGHT_DP = 220
        private const val DEFAULT_TEXT_SIZE_SP = 16f
    }
}
