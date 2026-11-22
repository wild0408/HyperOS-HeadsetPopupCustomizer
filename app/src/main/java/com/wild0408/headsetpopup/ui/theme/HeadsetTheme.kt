package com.wild0408.headsetpopup.ui.theme

import android.app.Activity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowInsetsControllerCompat
import com.wild0408.headsetpopup.ui.state.AppSettingsState
import com.wild0408.headsetpopup.ui.state.THEME_MODE_DARK
import com.wild0408.headsetpopup.ui.state.THEME_MODE_LIGHT
import com.haooz.nexioui.indication.NexioIndication
import com.haooz.nexioui.material.LocalMaterialNoneEnabled
import com.haooz.nexioui.material.LocalUseFakeProgressiveBlur
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.navigationback.LocalNexioPredictiveBackEnabled
import com.haooz.nexioui.theme.NexioDarkColorScheme
import com.haooz.nexioui.theme.NexioLightColorScheme
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.GlassEnvironment

/** 系统原始深色状态，不受手动覆盖影响。 */
val LocalSystemDarkTheme = staticCompositionLocalOf { false }

/** 设置页图标强调色（与 NexioSchedule 保持一致，不使用 Monet 动态取色）。 */
object SettingsAccents {
    val Blue = Color(0xFF3482FF)
    val Green = Color(0xFF1DCD3A)
    val Purple = Color(0xFF7767F9)
    val Red = Color(0xFFFA382E)
    val Orange = Color(0xFFFF8A00)
    val Gray = Color(0xFF8C9DB0)
}

@Composable
fun HeadsetTheme(
    settings: AppSettingsState,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        THEME_MODE_LIGHT -> false
        THEME_MODE_DARK -> true
        else -> systemDark
    }

    // 统一使用 Miuix / NexioUI 默认浅色与深色语义色板，禁用 MonetSystem
    val nexioColors = if (dark) NexioDarkColorScheme else NexioLightColorScheme
    val materialColors = remember(dark, nexioColors) {
        if (dark) {
            darkColorScheme(
                primary = nexioColors.primary,
                onPrimary = nexioColors.onPrimary,
                primaryContainer = nexioColors.primaryContainer,
                onPrimaryContainer = nexioColors.onPrimaryContainer,
                background = nexioColors.background,
                onBackground = nexioColors.onBackground,
                surface = nexioColors.surface,
                onSurface = nexioColors.onSurface,
                surfaceVariant = nexioColors.surfaceVariant,
                onSurfaceVariant = nexioColors.onSurfaceVariant,
                outline = nexioColors.outline,
                surfaceContainer = nexioColors.surfaceContainer,
                surfaceContainerHigh = nexioColors.surfaceContainerHigh,
                surfaceContainerHighest = nexioColors.surfaceContainerHighest,
                error = nexioColors.error,
            )
        } else {
            lightColorScheme(
                primary = nexioColors.primary,
                onPrimary = nexioColors.onPrimary,
                primaryContainer = nexioColors.primaryContainer,
                onPrimaryContainer = nexioColors.onPrimaryContainer,
                background = nexioColors.background,
                onBackground = nexioColors.onBackground,
                surface = nexioColors.surface,
                onSurface = nexioColors.onSurface,
                surfaceVariant = nexioColors.surfaceVariant,
                onSurfaceVariant = nexioColors.onSurfaceVariant,
                outline = nexioColors.outline,
                surfaceContainer = nexioColors.surfaceContainer,
                surfaceContainerHigh = nexioColors.surfaceContainerHigh,
                surfaceContainerHighest = nexioColors.surfaceContainerHighest,
                error = nexioColors.error,
            )
        }
    }

    // 同步状态栏与导航栏图标深浅色
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    // 同步 SoftGlass 运行时环境与全局材质档位（与 NexioSchedule Theme.kt 保持一致）：
    // density 决定液态折射带 dp->px 换算，isDark 决定按压高光与阴影观感，纯色页无壁纸传 false。
    val density = LocalDensity.current
    val materialLevel = settings.materialLevel
    SideEffect {
        GlassEnvironment.update(
            isDark = dark,
            hasWallpaper = false,
            density = density.density,
        )
        NexioMaterial.level = materialLevel
    }

    val rawHaptic = LocalHapticFeedback.current
    val gatedHaptic = remember(rawHaptic, settings.hapticFeedbackEnabled) {
        if (settings.hapticFeedbackEnabled) {
            rawHaptic
        } else {
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
            }
        }
    }

    val outerTextStyle: TextStyle = LocalTextStyle.current

    CompositionLocalProvider(
        LocalHapticFeedback provides gatedHaptic,
        LocalSystemDarkTheme provides systemDark,
        LocalMaterialNoneEnabled provides NexioMaterial.isNone(),
        LocalUseFakeProgressiveBlur provides !settings.trueProgressiveBlur,
        LocalNexioPredictiveBackEnabled provides settings.predictiveBackEnabled,
    ) {
        NexioTheme(
            isDark = dark,
            colorScheme = nexioColors,
            isDynamicColor = false,
        ) {
            MaterialTheme(colorScheme = materialColors) {
                // 覆盖 Material3 水波纹，统一使用 NexioIndication 整块淡色按压反馈
                val indicationColor = NexioTheme.colorScheme.onBackground
                val indication = remember(indicationColor) { NexioIndication(color = indicationColor) }
                CompositionLocalProvider(
                    LocalIndication provides indication,
                    LocalContentColor provides nexioColors.onBackground,
                    LocalTextStyle provides outerTextStyle,
                    content = content,
                )
            }
        }
    }
}
