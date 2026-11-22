package com.wild0408.headsetpopup.config

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

enum class PopupMediaType(val wire: String, val label: String) {
    NONE("none", "未设置"),
    IMAGE("image", "图片 / 动图"),
    VIDEO("video", "视频");

    companion object {
        fun fromWire(value: String?): PopupMediaType =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: NONE
    }
}

enum class PopupScaleMode(val wire: String, val label: String) {
    CENTER_CROP("center_crop", "铺满裁切"),
    FIT_CENTER("fit_center", "完整适应");

    companion object {
        fun fromWire(value: String?): PopupScaleMode =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: FIT_CENTER
    }
}

enum class PopupForegroundTone(val wire: String, val label: String) {
    AUTO("auto", "跟随系统"),
    LIGHT("light", "浅色文字"),
    DARK("dark", "深色文字");

    companion object {
        fun fromWire(value: String?): PopupForegroundTone =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: AUTO
    }
}

enum class PopupButtonStyle(val wire: String, val label: String, val summary: String) {
    INHERIT("inherit", "继承全局", "跟随全局默认方案的按钮与关闭键材质"),
    NATIVE("native", "保留原版", "保持系统默认底部按钮与右上角关闭键样式"),
    LIQUID_AUTO("liquid_auto", "液态玻璃 · 自动", "SoftGlass 液态透镜，高通透微模糊与边缘色散折射高光"),
    SOFT_AUTO("soft_auto", "柔光玻璃 · 自动", "SoftGlass 柔光玻璃，柔和模糊与菲涅尔顺滑高光边缘"),
    FROSTED_AUTO("frosted_auto", "磨砂玻璃 · 自动", "SoftGlass 强磨砂雾面，高模糊遮蔽力与细腻磨砂质感"),
    LIQUID_LIGHT("liquid_light", "液态玻璃 · 浅色", "晶透浅色液态玻璃与色散光边，配深色文字和图标"),
    LIQUID_DARK("liquid_dark", "液态玻璃 · 深色", "晶透深色液态玻璃与色散光边，配浅色文字和图标"),
    SOFT_LIGHT("soft_light", "柔光玻璃 · 浅色", "亮透柔光玻璃，配深色文字和图标"),
    SOFT_DARK("soft_dark", "柔光玻璃 · 深色", "暗透柔光玻璃，配浅色文字和图标"),
    TINT_DARK("tint_dark", "墨色玻璃", "烟熏暗透染色玻璃"),
    TINT_PURPLE("tint_purple", "幻紫玻璃", "淡紫冷调染色玻璃");

    companion object {
        fun fromWire(value: String?, defaultStyle: PopupButtonStyle = NATIVE): PopupButtonStyle {
            if (value == null) return defaultStyle
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }?.let { return it }
            return when (value.lowercase()) {
                "glass_auto" -> LIQUID_AUTO
                "glass_light" -> LIQUID_LIGHT
                "glass_dark" -> LIQUID_DARK
                else -> defaultStyle
            }
        }
    }
}

data class PopupCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    fun normalized(): PopupCropRect {
        val minSpan = 0.05f
        var l = left.coerceIn(0f, 1f - minSpan)
        var t = top.coerceIn(0f, 1f - minSpan)
        var r = right.coerceIn(l + minSpan, 1f)
        var b = bottom.coerceIn(t + minSpan, 1f)
        if (r - l < minSpan) {
            l = 0f
            r = 1f
        }
        if (b - t < minSpan) {
            t = 0f
            b = 1f
        }
        return PopupCropRect(l, t, r, b)
    }

    val isFullFrame: Boolean
        get() = left <= 0.002f && top <= 0.002f && right >= 0.998f && bottom >= 0.998f

    fun summaryLabel(): String {
        val n = normalized()
        if (n.isFullFrame) return "完整画面"
        val wPct = ((n.right - n.left) * 100f).roundToInt().coerceIn(5, 100)
        val hPct = ((n.bottom - n.top) * 100f).roundToInt().coerceIn(5, 100)
        return "自定义裁剪 ${wPct}%×${hPct}%"
    }

    fun toJson(): JSONObject {
        val n = normalized()
        return JSONObject().apply {
            put("left", n.left.toDouble())
            put("top", n.top.toDouble())
            put("right", n.right.toDouble())
            put("bottom", n.bottom.toDouble())
        }
    }

    companion object {
        val FULL = PopupCropRect(0f, 0f, 1f, 1f)

        fun fromJson(obj: JSONObject?): PopupCropRect {
            if (obj == null) return FULL
            return PopupCropRect(
                left = obj.optDouble("left", 0.0).toFloat(),
                top = obj.optDouble("top", 0.0).toFloat(),
                right = obj.optDouble("right", 1.0).toFloat(),
                bottom = obj.optDouble("bottom", 1.0).toFloat(),
            ).normalized()
        }
    }
}

enum class ConnectedTransitionMode(val wire: String, val label: String, val summary: String) {
    NATIVE("native", "原版过场", "保持系统连接过场"),
    CUSTOM("custom", "自定义过场", "播完自定义素材后进入电量页"),
    SKIP("skip", "跳过过场", "直接显示电量页");

    companion object {
        fun fromWire(value: String?): ConnectedTransitionMode =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: NATIVE
    }
}

enum class ConnectedVisualMode(val wire: String, val label: String, val summary: String) {
    SEPARATE("separate", "分别替换", "耳机图与充电盒图独立设置"),
    UNIFIED("unified", "合并整块", "整块展示自定义图片或视频");

    companion object {
        fun fromWire(value: String?): ConnectedVisualMode =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: SEPARATE
    }
}

enum class PopupStageKey(
    val key: String,
    val title: String,
    val shortTitle: String,
    val description: String,
    val connectState: Int,
) {
    FIND(
        key = "find",
        title = "发现设备",
        shortTitle = "发现",
        description = "开盖发现未连接设备时的弹窗标题、文案、主按钮与展示动图",
        connectState = 1,
    ),
    CONNECTING(
        key = "connecting",
        title = "正在连接",
        shortTitle = "连接中",
        description = "点击连接或同账号自动连接时的标题、底部提示与循环动图",
        connectState = 2,
    ),
    CONNECTED(
        key = "connected",
        title = "连接成功与电量",
        shortTitle = "电量页",
        description = "过场动图，以及电量页分别替换耳机/盒图或合并整块自定义动图",
        connectState = 3,
    ),
    FAIL(
        key = "fail",
        title = "连接失败",
        shortTitle = "失败",
        description = "配对失败或超时时的标题、提示文案、重试按钮与失败动图",
        connectState = 4,
    ),
    RESET(
        key = "reset",
        title = "重置 / 长按配对",
        shortTitle = "重置",
        description = "检测到他人设备或需要长按配对键时的标题、引导文案与动图",
        connectState = 5,
    );

    companion object {
        fun fromKey(key: String?): PopupStageKey =
            entries.firstOrNull { it.key == key } ?: FIND
    }
}

data class PopupMediaSpec(
    val type: PopupMediaType = PopupMediaType.NONE,
    val assetName: String = "",
    val displayName: String = "",
    val scaleMode: PopupScaleMode = PopupScaleMode.FIT_CENTER,
    val loop: Boolean = true,
    val crop: PopupCropRect = PopupCropRect.FULL,
) {
    val isConfigured: Boolean
        get() = type != PopupMediaType.NONE && assetName.isNotBlank()

    fun summaryLabel(): String = if (!isConfigured) {
        "未替换 · 保留原版"
    } else {
        val name = displayName.ifBlank { assetName }
        val cropSuffix = if (crop.isFullFrame) "" else " · ${crop.summaryLabel()}"
        "${type.label} · $name$cropSuffix"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.wire)
        put("assetName", assetName)
        put("displayName", displayName)
        put("scaleMode", scaleMode.wire)
        put("loop", loop)
        put("crop", crop.toJson())
    }

    companion object {
        val NONE = PopupMediaSpec()

        fun fromJson(obj: JSONObject?, defaultScale: PopupScaleMode = PopupScaleMode.FIT_CENTER): PopupMediaSpec {
            if (obj == null) return PopupMediaSpec(scaleMode = defaultScale)
            return PopupMediaSpec(
                type = PopupMediaType.fromWire(obj.optString("type", "none")),
                assetName = obj.optString("assetName", ""),
                displayName = obj.optString("displayName", ""),
                scaleMode = PopupScaleMode.fromWire(obj.optString("scaleMode", defaultScale.wire)),
                loop = obj.optBoolean("loop", true),
                crop = PopupCropRect.fromJson(obj.optJSONObject("crop")),
            )
        }
    }
}

data class PopupBackgroundConfig(
    val enabled: Boolean = false,
    val media: PopupMediaSpec = PopupMediaSpec(scaleMode = PopupScaleMode.CENTER_CROP),
    val alpha: Float = 1.0f,
    val scrimAlpha: Float = 0.0f,
    val foregroundTone: PopupForegroundTone = PopupForegroundTone.AUTO,
) {
    val isActive: Boolean
        get() = enabled && media.isConfigured

    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("media", media.toJson())
        put("alpha", alpha.toDouble())
        put("scrimAlpha", scrimAlpha.toDouble())
        put("foregroundTone", foregroundTone.wire)
    }

    companion object {
        fun fromJson(obj: JSONObject?): PopupBackgroundConfig {
            if (obj == null) return PopupBackgroundConfig()
            return PopupBackgroundConfig(
                enabled = obj.optBoolean("enabled", false),
                media = PopupMediaSpec.fromJson(obj.optJSONObject("media"), PopupScaleMode.CENTER_CROP),
                alpha = obj.optDouble("alpha", 1.0).toFloat().coerceIn(0.15f, 1.0f),
                scrimAlpha = obj.optDouble("scrimAlpha", 0.0).toFloat().coerceIn(0.0f, 0.80f),
                foregroundTone = PopupForegroundTone.fromWire(obj.optString("foregroundTone", "auto")),
            )
        }
    }
}

data class StageOverrideConfig(
    val enabled: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val buttonText: String = "",
    val media: PopupMediaSpec = PopupMediaSpec.NONE,
) {
    val hasAnyOverride: Boolean
        get() = enabled && (title.isNotBlank() || subtitle.isNotBlank() || buttonText.isNotBlank() || media.isConfigured)

    fun summaryLabel(): String {
        if (!enabled) return "未开启 · 保留原版"
        val parts = buildList {
            if (media.isConfigured) add(if (media.type == PopupMediaType.VIDEO) "自定义视频" else "自定义图片/动图")
            if (title.isNotBlank()) add("标题")
            if (subtitle.isNotBlank()) add("文案")
            if (buttonText.isNotBlank()) add("按钮")
        }
        return if (parts.isEmpty()) "已开启但未填项 · 保留原版" else "已替换：${parts.joinToString(" · ")}"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("title", title)
        put("subtitle", subtitle)
        put("buttonText", buttonText)
        put("media", media.toJson())
    }

    companion object {
        fun fromJson(obj: JSONObject?): StageOverrideConfig {
            if (obj == null) return StageOverrideConfig()
            return StageOverrideConfig(
                enabled = obj.optBoolean("enabled", false),
                title = obj.optString("title", ""),
                subtitle = obj.optString("subtitle", ""),
                buttonText = obj.optString("buttonText", ""),
                media = PopupMediaSpec.fromJson(obj.optJSONObject("media"), PopupScaleMode.FIT_CENTER),
            )
        }
    }
}

data class ConnectedStageConfig(
    val enabled: Boolean = false,
    val title: String = "",
    val buttonText: String = "",
    val transitionMode: ConnectedTransitionMode = ConnectedTransitionMode.NATIVE,
    val transitionMedia: PopupMediaSpec = PopupMediaSpec(loop = false),
    val visualMode: ConnectedVisualMode = ConnectedVisualMode.SEPARATE,
    val headsetMedia: PopupMediaSpec = PopupMediaSpec.NONE,
    val caseMedia: PopupMediaSpec = PopupMediaSpec.NONE,
    val unifiedMedia: PopupMediaSpec = PopupMediaSpec.NONE,
) {
    val hasAnyOverride: Boolean
        get() = enabled && (
            title.isNotBlank() ||
                buttonText.isNotBlank() ||
                transitionMode == ConnectedTransitionMode.SKIP ||
                (transitionMode == ConnectedTransitionMode.CUSTOM && transitionMedia.isConfigured) ||
                (visualMode == ConnectedVisualMode.SEPARATE && (headsetMedia.isConfigured || caseMedia.isConfigured)) ||
                (visualMode == ConnectedVisualMode.UNIFIED && unifiedMedia.isConfigured)
            )

    fun summaryLabel(): String {
        if (!enabled) return "未开启 · 保留原版"
        val parts = buildList {
            when (visualMode) {
                ConnectedVisualMode.SEPARATE -> {
                    if (headsetMedia.isConfigured) add("耳机图")
                    if (caseMedia.isConfigured) add("充电盒图")
                }
                ConnectedVisualMode.UNIFIED -> {
                    if (unifiedMedia.isConfigured) add("合并整块动图")
                }
            }
            when (transitionMode) {
                ConnectedTransitionMode.CUSTOM -> if (transitionMedia.isConfigured) add("过场动图")
                ConnectedTransitionMode.SKIP -> add("跳过过场")
                ConnectedTransitionMode.NATIVE -> Unit
            }
            if (title.isNotBlank()) add("标题")
            if (buttonText.isNotBlank()) add("按钮")
        }
        return if (parts.isEmpty()) "已开启但未填项 · 保留原版" else "已替换：${parts.joinToString(" · ")}"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("title", title)
        put("buttonText", buttonText)
        put("transitionMode", transitionMode.wire)
        put("transitionMedia", transitionMedia.toJson())
        put("visualMode", visualMode.wire)
        put("headsetMedia", headsetMedia.toJson())
        put("caseMedia", caseMedia.toJson())
        put("unifiedMedia", unifiedMedia.toJson())
    }

    companion object {
        fun fromJson(obj: JSONObject?): ConnectedStageConfig {
            if (obj == null) return ConnectedStageConfig()
            return ConnectedStageConfig(
                enabled = obj.optBoolean("enabled", false),
                title = obj.optString("title", ""),
                buttonText = obj.optString("buttonText", ""),
                transitionMode = ConnectedTransitionMode.fromWire(obj.optString("transitionMode", "native")),
                transitionMedia = PopupMediaSpec.fromJson(obj.optJSONObject("transitionMedia"), PopupScaleMode.FIT_CENTER),
                visualMode = ConnectedVisualMode.fromWire(obj.optString("visualMode", "separate")),
                headsetMedia = PopupMediaSpec.fromJson(obj.optJSONObject("headsetMedia"), PopupScaleMode.FIT_CENTER),
                caseMedia = PopupMediaSpec.fromJson(obj.optJSONObject("caseMedia"), PopupScaleMode.FIT_CENTER),
                unifiedMedia = PopupMediaSpec.fromJson(obj.optJSONObject("unifiedMedia"), PopupScaleMode.FIT_CENTER),
            )
        }
    }
}

data class PopupProfile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val inheritGlobal: Boolean = true,
    val background: PopupBackgroundConfig = PopupBackgroundConfig(),
    val buttonStyle: PopupButtonStyle = PopupButtonStyle.NATIVE,
    val findStage: StageOverrideConfig = StageOverrideConfig(),
    val connectingStage: StageOverrideConfig = StageOverrideConfig(),
    val connectedStage: ConnectedStageConfig = ConnectedStageConfig(),
    val failStage: StageOverrideConfig = StageOverrideConfig(),
    val resetStage: StageOverrideConfig = StageOverrideConfig(),
) {
    val isGlobal: Boolean
        get() = id == GLOBAL_ID

    fun stageConfig(stage: PopupStageKey): StageOverrideConfig = when (stage) {
        PopupStageKey.FIND -> findStage
        PopupStageKey.CONNECTING -> connectingStage
        PopupStageKey.FAIL -> failStage
        PopupStageKey.RESET -> resetStage
        PopupStageKey.CONNECTED -> StageOverrideConfig()
    }

    fun withStageConfig(stage: PopupStageKey, config: StageOverrideConfig): PopupProfile = when (stage) {
        PopupStageKey.FIND -> copy(findStage = config)
        PopupStageKey.CONNECTING -> copy(connectingStage = config)
        PopupStageKey.FAIL -> copy(failStage = config)
        PopupStageKey.RESET -> copy(resetStage = config)
        PopupStageKey.CONNECTED -> this
    }

    fun activeCustomizationCount(): Int {
        var count = 0
        if (background.isActive) count++
        if (buttonStyle != PopupButtonStyle.NATIVE && buttonStyle != PopupButtonStyle.INHERIT) count++
        if (findStage.hasAnyOverride) count++
        if (connectingStage.hasAnyOverride) count++
        if (connectedStage.hasAnyOverride) count++
        if (failStage.hasAnyOverride) count++
        if (resetStage.hasAnyOverride) count++
        return count
    }

    fun collectAssetNames(): Set<String> = buildSet {
        listOf(
            background.media,
            findStage.media,
            connectingStage.media,
            connectedStage.transitionMedia,
            connectedStage.headsetMedia,
            connectedStage.caseMedia,
            connectedStage.unifiedMedia,
            failStage.media,
            resetStage.media,
        ).forEach { media ->
            if (media.assetName.isNotBlank()) add(media.assetName)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("inheritGlobal", inheritGlobal)
        put("background", background.toJson())
        put("buttonStyle", buttonStyle.wire)
        put("findStage", findStage.toJson())
        put("connectingStage", connectingStage.toJson())
        put("connectedStage", connectedStage.toJson())
        put("failStage", failStage.toJson())
        put("resetStage", resetStage.toJson())
    }

    companion object {
        const val GLOBAL_ID = "global"

        fun defaultGlobal(): PopupProfile = PopupProfile(
            id = GLOBAL_ID,
            name = "全局默认配置",
            enabled = true,
            inheritGlobal = false,
        )

        fun normalizeDeviceId(raw: String?): String =
            raw.orEmpty().trim().uppercase()

        fun fromJson(obj: JSONObject?): PopupProfile {
            if (obj == null) return defaultGlobal()
            val rawId = obj.optString("id", GLOBAL_ID).ifBlank { GLOBAL_ID }
            val id = if (rawId.equals(GLOBAL_ID, ignoreCase = true)) GLOBAL_ID else normalizeDeviceId(rawId)
            val defaultBtn = if (id == GLOBAL_ID) PopupButtonStyle.NATIVE else PopupButtonStyle.INHERIT
            val parsedBtn = PopupButtonStyle.fromWire(
                if (obj.has("buttonStyle")) obj.optString("buttonStyle") else null,
                defaultStyle = defaultBtn,
            )
            return PopupProfile(
                id = id,
                name = obj.optString("name", if (id == GLOBAL_ID) "全局默认配置" else id),
                enabled = obj.optBoolean("enabled", true),
                inheritGlobal = obj.optBoolean("inheritGlobal", id != GLOBAL_ID),
                background = PopupBackgroundConfig.fromJson(obj.optJSONObject("background")),
                buttonStyle = if (id == GLOBAL_ID && parsedBtn == PopupButtonStyle.INHERIT) {
                    PopupButtonStyle.NATIVE
                } else {
                    parsedBtn
                },
                findStage = StageOverrideConfig.fromJson(obj.optJSONObject("findStage")),
                connectingStage = StageOverrideConfig.fromJson(obj.optJSONObject("connectingStage")),
                connectedStage = ConnectedStageConfig.fromJson(obj.optJSONObject("connectedStage")),
                failStage = StageOverrideConfig.fromJson(obj.optJSONObject("failStage")),
                resetStage = StageOverrideConfig.fromJson(obj.optJSONObject("resetStage")),
            )
        }
    }
}

data class ResolvedPopupConfig(
    val active: Boolean,
    val matchedProfileId: String,
    val matchedProfileName: String,
    val background: PopupBackgroundConfig,
    val buttonStyle: PopupButtonStyle,
    val findStage: StageOverrideConfig,
    val connectingStage: StageOverrideConfig,
    val connectedStage: ConnectedStageConfig,
    val failStage: StageOverrideConfig,
    val resetStage: StageOverrideConfig,
) {
    val hasAnyEffect: Boolean
        get() = active && (
            background.isActive ||
                buttonStyle != PopupButtonStyle.NATIVE ||
                findStage.hasAnyOverride ||
                connectingStage.hasAnyOverride ||
                connectedStage.hasAnyOverride ||
                failStage.hasAnyOverride ||
                resetStage.hasAnyOverride
            )

    fun toJson(): JSONObject = JSONObject().apply {
        put("active", active)
        put("matchedProfileId", matchedProfileId)
        put("matchedProfileName", matchedProfileName)
        put("background", background.toJson())
        put("buttonStyle", buttonStyle.wire)
        put("findStage", findStage.toJson())
        put("connectingStage", connectingStage.toJson())
        put("connectedStage", connectedStage.toJson())
        put("failStage", failStage.toJson())
        put("resetStage", resetStage.toJson())
    }

    companion object {
        val EMPTY = ResolvedPopupConfig(
            active = false,
            matchedProfileId = "",
            matchedProfileName = "",
            background = PopupBackgroundConfig(),
            buttonStyle = PopupButtonStyle.NATIVE,
            findStage = StageOverrideConfig(),
            connectingStage = StageOverrideConfig(),
            connectedStage = ConnectedStageConfig(),
            failStage = StageOverrideConfig(),
            resetStage = StageOverrideConfig(),
        )
    }
}

data class PopupConfigDocument(
    val masterEnabled: Boolean = true,
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val globalProfile: PopupProfile = PopupProfile.defaultGlobal(),
    val deviceProfiles: List<PopupProfile> = emptyList(),
) {
    fun findProfile(profileId: String): PopupProfile {
        if (profileId == PopupProfile.GLOBAL_ID) return globalProfile
        val normalized = PopupProfile.normalizeDeviceId(profileId)
        return deviceProfiles.firstOrNull { it.id == normalized } ?: globalProfile
    }

    fun collectAllAssetNames(): Set<String> = buildSet {
        addAll(globalProfile.collectAssetNames())
        deviceProfiles.forEach { addAll(it.collectAssetNames()) }
    }

    fun resolveForProfile(profile: PopupProfile): ResolvedPopupConfig =
        if (profile.isGlobal) resolveForDevice(null) else resolveForDevice(profile.id)

    fun resolveForDevice(deviceId: String?): ResolvedPopupConfig {
        if (!masterEnabled) return ResolvedPopupConfig.EMPTY
        val normalizedId = PopupProfile.normalizeDeviceId(deviceId)
        val deviceProfile = if (normalizedId.isNotEmpty()) {
            deviceProfiles.firstOrNull { it.id.equals(normalizedId, ignoreCase = true) && it.enabled }
        } else {
            null
        }
        val baseGlobal = if (globalProfile.enabled) globalProfile else null

        if (deviceProfile == null) {
            val g = baseGlobal ?: return ResolvedPopupConfig.EMPTY
            return ResolvedPopupConfig(
                active = true,
                matchedProfileId = g.id,
                matchedProfileName = g.name,
                background = if (g.background.isActive) g.background else PopupBackgroundConfig(),
                buttonStyle = if (g.buttonStyle == PopupButtonStyle.INHERIT) PopupButtonStyle.NATIVE else g.buttonStyle,
                findStage = activeStageOrEmpty(g.findStage),
                connectingStage = activeStageOrEmpty(g.connectingStage),
                connectedStage = activeConnectedOrEmpty(g.connectedStage),
                failStage = activeStageOrEmpty(g.failStage),
                resetStage = activeStageOrEmpty(g.resetStage),
            )
        }

        val fallback = if (deviceProfile.inheritGlobal) baseGlobal else null
        return ResolvedPopupConfig(
            active = true,
            matchedProfileId = deviceProfile.id,
            matchedProfileName = deviceProfile.name,
            background = mergeBackground(deviceProfile.background, fallback?.background),
            buttonStyle = mergeButtonStyle(deviceProfile.buttonStyle, fallback?.buttonStyle),
            findStage = mergeStage(deviceProfile.findStage, fallback?.findStage),
            connectingStage = mergeStage(deviceProfile.connectingStage, fallback?.connectingStage),
            connectedStage = mergeConnectedStage(deviceProfile.connectedStage, fallback?.connectedStage),
            failStage = mergeStage(deviceProfile.failStage, fallback?.failStage),
            resetStage = mergeStage(deviceProfile.resetStage, fallback?.resetStage),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("masterEnabled", masterEnabled)
        put("version", version)
        put("updatedAt", updatedAt)
        put("globalProfile", globalProfile.toJson())
        put(
            "deviceProfiles",
            JSONArray().apply {
                deviceProfiles.forEach { put(it.toJson()) }
            },
        )
    }

    companion object {
        fun fromJson(raw: String?): PopupConfigDocument {
            if (raw.isNullOrBlank()) return PopupConfigDocument()
            return runCatching {
                val obj = JSONObject(raw)
                val global = PopupProfile.fromJson(obj.optJSONObject("globalProfile"))
                    .copy(id = PopupProfile.GLOBAL_ID, inheritGlobal = false)
                val list = buildList {
                    val arr = obj.optJSONArray("deviceProfiles")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val profile = PopupProfile.fromJson(item)
                            if (!profile.isGlobal && profile.id.isNotBlank()) {
                                add(profile)
                            }
                        }
                    }
                }
                PopupConfigDocument(
                    masterEnabled = obj.optBoolean("masterEnabled", true),
                    version = obj.optInt("version", 1),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    globalProfile = global,
                    deviceProfiles = list,
                )
            }.getOrDefault(PopupConfigDocument())
        }

        private fun activeStageOrEmpty(stage: StageOverrideConfig): StageOverrideConfig =
            if (stage.enabled) stage else StageOverrideConfig()

        private fun activeConnectedOrEmpty(stage: ConnectedStageConfig): ConnectedStageConfig =
            if (stage.enabled) stage else ConnectedStageConfig()

        private fun mergeBackground(
            primary: PopupBackgroundConfig,
            fallback: PopupBackgroundConfig?,
        ): PopupBackgroundConfig {
            // 设备方案明确开启「自定义卡片背景」时，不再回退全局背景：
            // 有素材则用本设备素材，留空则恢复系统原版背景。
            if (primary.enabled) {
                return if (primary.media.isConfigured) primary else PopupBackgroundConfig()
            }
            if (fallback != null && fallback.isActive) return fallback
            return PopupBackgroundConfig()
        }

        private fun mergeButtonStyle(
            primary: PopupButtonStyle,
            fallback: PopupButtonStyle?,
        ): PopupButtonStyle {
            if (primary == PopupButtonStyle.INHERIT) {
                return fallback?.takeUnless { it == PopupButtonStyle.INHERIT } ?: PopupButtonStyle.NATIVE
            }
            return primary
        }

        private fun mergeStage(
            primary: StageOverrideConfig,
            fallback: StageOverrideConfig?,
        ): StageOverrideConfig {
            val p = activeStageOrEmpty(primary)
            val f = fallback?.let(::activeStageOrEmpty) ?: StageOverrideConfig()
            val enabled = p.enabled || f.enabled
            if (!enabled) return StageOverrideConfig()
            return StageOverrideConfig(
                enabled = true,
                title = p.title.ifBlank { f.title },
                subtitle = p.subtitle.ifBlank { f.subtitle },
                buttonText = p.buttonText.ifBlank { f.buttonText },
                media = if (p.media.isConfigured) p.media else f.media,
            )
        }

        private fun mergeConnectedStage(
            primary: ConnectedStageConfig,
            fallback: ConnectedStageConfig?,
        ): ConnectedStageConfig {
            val p = activeConnectedOrEmpty(primary)
            val f = fallback?.let(::activeConnectedOrEmpty) ?: ConnectedStageConfig()
            val enabled = p.enabled || f.enabled
            if (!enabled) return ConnectedStageConfig()

            val transitionMode = when {
                p.enabled && (p.transitionMode == ConnectedTransitionMode.SKIP ||
                    (p.transitionMode == ConnectedTransitionMode.CUSTOM && p.transitionMedia.isConfigured)) -> p.transitionMode
                f.enabled && (f.transitionMode == ConnectedTransitionMode.SKIP ||
                    (f.transitionMode == ConnectedTransitionMode.CUSTOM && f.transitionMedia.isConfigured)) -> f.transitionMode
                else -> ConnectedTransitionMode.NATIVE
            }
            val transitionMedia = when (transitionMode) {
                ConnectedTransitionMode.CUSTOM -> if (p.enabled && p.transitionMedia.isConfigured) p.transitionMedia else f.transitionMedia
                else -> PopupMediaSpec.NONE
            }

            val primaryHasVisual = p.enabled && (
                (p.visualMode == ConnectedVisualMode.UNIFIED && p.unifiedMedia.isConfigured) ||
                    (p.visualMode == ConnectedVisualMode.SEPARATE && (p.headsetMedia.isConfigured || p.caseMedia.isConfigured))
                )
            val useVisualMode = when {
                primaryHasVisual -> p.visualMode
                f.enabled -> f.visualMode
                else -> p.visualMode
            }
            val headsetMedia = if (useVisualMode == ConnectedVisualMode.SEPARATE) {
                when {
                    p.enabled && p.headsetMedia.isConfigured -> p.headsetMedia
                    f.enabled && f.visualMode == ConnectedVisualMode.SEPARATE && f.headsetMedia.isConfigured -> f.headsetMedia
                    else -> PopupMediaSpec.NONE
                }
            } else {
                PopupMediaSpec.NONE
            }
            val caseMedia = if (useVisualMode == ConnectedVisualMode.SEPARATE) {
                when {
                    p.enabled && p.caseMedia.isConfigured -> p.caseMedia
                    f.enabled && f.visualMode == ConnectedVisualMode.SEPARATE && f.caseMedia.isConfigured -> f.caseMedia
                    else -> PopupMediaSpec.NONE
                }
            } else {
                PopupMediaSpec.NONE
            }
            val unifiedMedia = if (useVisualMode == ConnectedVisualMode.UNIFIED) {
                when {
                    p.enabled && p.unifiedMedia.isConfigured -> p.unifiedMedia
                    f.enabled && f.visualMode == ConnectedVisualMode.UNIFIED && f.unifiedMedia.isConfigured -> f.unifiedMedia
                    else -> PopupMediaSpec.NONE
                }
            } else {
                PopupMediaSpec.NONE
            }

            return ConnectedStageConfig(
                enabled = true,
                title = p.title.ifBlank { f.title },
                buttonText = p.buttonText.ifBlank { f.buttonText },
                transitionMode = transitionMode,
                transitionMedia = transitionMedia,
                visualMode = useVisualMode,
                headsetMedia = headsetMedia,
                caseMedia = caseMedia,
                unifiedMedia = unifiedMedia,
            )
        }
    }
}
