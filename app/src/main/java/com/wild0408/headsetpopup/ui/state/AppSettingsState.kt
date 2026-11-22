package com.wild0408.headsetpopup.ui.state

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.wild0408.headsetpopup.trigger.TriggerDevicePreset
import com.wild0408.headsetpopup.trigger.TriggerScenario
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.material.NexioMaterialLevel
import com.haooz.softglass.platform.isRenderEffectSupported

const val THEME_MODE_SYSTEM = "system"
const val THEME_MODE_LIGHT = "light"
const val THEME_MODE_DARK = "dark"

@Stable
class AppSettingsState(private val prefs: SharedPreferences) {

    var themeMode: String by mutableStateOf(prefs.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM) ?: THEME_MODE_SYSTEM)
        private set

    var materialLevel: NexioMaterialLevel by mutableStateOf(loadMaterialLevel())
        private set

    var trueProgressiveBlur: Boolean by mutableStateOf(prefs.getBoolean(KEY_TRUE_PROGRESSIVE_BLUR, true))
        private set

    var hapticFeedbackEnabled: Boolean by mutableStateOf(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
        private set

    var predictiveBackEnabled: Boolean by mutableStateOf(prefs.getBoolean(KEY_PREDICTIVE_BACK, true))
        private set

    var triggerDeviceId: String by mutableStateOf(
        prefs.getString(KEY_TRIGGER_DEVICE_ID, TriggerDevicePreset.REDMI_BUDS_8_PRO.deviceId)
            ?: TriggerDevicePreset.REDMI_BUDS_8_PRO.deviceId,
    )
        private set

    var triggerScenarioKey: String by mutableStateOf(
        prefs.getString(KEY_TRIGGER_SCENARIO, TriggerScenario.DISCOVERY.key)
            ?: TriggerScenario.DISCOVERY.key,
    )
        private set

    val materialLocked: Boolean = !isRenderEffectSupported()

    init {
        NexioMaterial.level = materialLevel
    }

    private fun loadMaterialLevel(): NexioMaterialLevel {
        if (!isRenderEffectSupported()) return NexioMaterialLevel.None
        return when (prefs.getString(KEY_MATERIAL_LEVEL, MATERIAL_LIQUID)) {
            MATERIAL_SOFT -> NexioMaterialLevel.Soft
            MATERIAL_LIQUID -> NexioMaterialLevel.Liquid
            MATERIAL_FROSTED -> NexioMaterialLevel.Frosted
            MATERIAL_NONE -> NexioMaterialLevel.None
            else -> NexioMaterialLevel.Liquid
        }
    }

    fun updateThemeMode(mode: String) {
        val resolved = when (mode) {
            THEME_MODE_LIGHT, THEME_MODE_DARK, THEME_MODE_SYSTEM -> mode
            else -> THEME_MODE_SYSTEM
        }
        themeMode = resolved
        prefs.edit().putString(KEY_THEME_MODE, resolved).apply()
    }

    fun updateMaterialLevel(level: NexioMaterialLevel) {
        val resolved = if (materialLocked) NexioMaterialLevel.None else level
        materialLevel = resolved
        NexioMaterial.level = resolved
        val key = when (resolved) {
            NexioMaterialLevel.Soft -> MATERIAL_SOFT
            NexioMaterialLevel.Liquid -> MATERIAL_LIQUID
            NexioMaterialLevel.Frosted -> MATERIAL_FROSTED
            NexioMaterialLevel.None -> MATERIAL_NONE
        }
        prefs.edit().putString(KEY_MATERIAL_LEVEL, key).apply()
    }

    fun updateTrueProgressiveBlur(enabled: Boolean) {
        trueProgressiveBlur = enabled
        prefs.edit().putBoolean(KEY_TRUE_PROGRESSIVE_BLUR, enabled).apply()
    }

    fun updateHapticFeedback(enabled: Boolean) {
        hapticFeedbackEnabled = enabled
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun updatePredictiveBack(enabled: Boolean) {
        predictiveBackEnabled = enabled
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK, enabled).apply()
    }

    fun updateTriggerDeviceId(deviceId: String) {
        val preset = TriggerDevicePreset.fromDeviceId(deviceId)
        triggerDeviceId = preset.deviceId
        prefs.edit().putString(KEY_TRIGGER_DEVICE_ID, preset.deviceId).apply()
    }

    fun updateTriggerScenario(scenario: TriggerScenario) {
        triggerScenarioKey = scenario.key
        prefs.edit().putString(KEY_TRIGGER_SCENARIO, scenario.key).apply()
    }

    fun materialLabel(level: NexioMaterialLevel = materialLevel): String = when (level) {
        NexioMaterialLevel.Liquid -> "液态玻璃"
        NexioMaterialLevel.Soft -> "柔光玻璃"
        NexioMaterialLevel.Frosted -> "清透磨砂"
        NexioMaterialLevel.None -> "无材质"
    }

    companion object {
        private const val PREFS_NAME = "headset_popup_ui_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MATERIAL_LEVEL = "material_level"
        private const val KEY_TRUE_PROGRESSIVE_BLUR = "true_progressive_blur"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_PREDICTIVE_BACK = "predictive_back"
        private const val KEY_TRIGGER_DEVICE_ID = "trigger_device_id"
        private const val KEY_TRIGGER_SCENARIO = "trigger_scenario"

        private const val MATERIAL_SOFT = "soft"
        private const val MATERIAL_LIQUID = "liquid"
        private const val MATERIAL_FROSTED = "frosted"
        private const val MATERIAL_NONE = "none"

        fun create(context: Context): AppSettingsState {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppSettingsState(prefs)
        }
    }
}

val LocalAppSettings = staticCompositionLocalOf<AppSettingsState> {
    error("LocalAppSettings not provided")
}

@Composable
fun rememberAppSettingsState(): AppSettingsState {
    val context = LocalContext.current
    return remember(context) { AppSettingsState.create(context) }
}
