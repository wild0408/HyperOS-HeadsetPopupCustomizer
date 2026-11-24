package com.wild0408.headsetpopup.config

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Locale

object PopupConfigStorage {
    private const val CONFIG_FILE_NAME = "popup_config.json"
    private const val MEDIA_DIR_NAME = "popup_media"
    private val lock = Any()

    fun configFile(context: Context): File =
        File(context.applicationContext.filesDir, CONFIG_FILE_NAME)

    fun mediaDir(context: Context): File =
        File(context.applicationContext.filesDir, MEDIA_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun mediaFile(context: Context, assetName: String): File? {
        if (assetName.isBlank() || assetName.contains('/') || assetName.contains('\\') || assetName.contains("..")) {
            return null
        }
        return File(mediaDir(context), assetName)
    }

    fun load(context: Context): PopupConfigDocument = synchronized(lock) {
        val file = configFile(context)
        if (!file.exists()) return PopupConfigDocument()
        return runCatching { PopupConfigDocument.fromJson(file.readText(Charsets.UTF_8)) }
            .getOrDefault(PopupConfigDocument())
    }

    fun save(context: Context, document: PopupConfigDocument): PopupConfigDocument = synchronized(lock) {
        val updated = document.copy(updatedAt = System.currentTimeMillis())
        val file = configFile(context)
        val tmp = File(file.parentFile, "$CONFIG_FILE_NAME.tmp")
        tmp.writeText(updated.toJson().toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(updated.toJson().toString(), Charsets.UTF_8)
            tmp.delete()
        }
        pruneUnusedMedia(context, updated.collectAllAssetNames())
        return updated
    }

    fun importMedia(
        context: Context,
        uri: Uri,
        slotPrefix: String,
        defaultScaleMode: PopupScaleMode,
        defaultLoop: Boolean,
        crop: PopupCropRect = PopupCropRect.FULL,
    ): Result<PopupMediaSpec> = runCatching {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri).orEmpty().lowercase(Locale.US)
        val originalName = queryDisplayName(appContext, uri).ifBlank { "media" }
        val ext = detectExtension(originalName, mime)
        val mediaType = when {
            mime.startsWith("video/") || ext in VIDEO_EXTENSIONS -> PopupMediaType.VIDEO
            mime.startsWith("image/") || ext in IMAGE_EXTENSIONS -> PopupMediaType.IMAGE
            else -> error("仅支持图片（PNG/JPG/WebP/GIF）或视频（MP4/WebM）")
        }
        val safePrefix = slotPrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(28)
        val assetName = "${safePrefix}_${System.currentTimeMillis()}.$ext"
        val target = File(mediaDir(appContext), assetName)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取所选媒体文件")
        if (target.length() <= 0L) {
            target.delete()
            error("所选媒体文件为空")
        }
        PopupMediaSpec(
            type = mediaType,
            assetName = assetName,
            displayName = originalName,
            scaleMode = defaultScaleMode,
            loop = defaultLoop,
            crop = crop.normalized(),
        )
    }

    private fun pruneUnusedMedia(context: Context, referencedAssets: Set<String>) {
        val dir = mediaDir(context)
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isFile && file.name !in referencedAssets) {
                runCatching { file.delete() }
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun detectExtension(displayName: String, mime: String): String {
        val nameExt = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        if (nameExt in IMAGE_EXTENSIONS || nameExt in VIDEO_EXTENSIONS) {
            return nameExt
        }
        return when {
            mime.contains("gif") -> "gif"
            mime.contains("webp") -> "webp"
            mime.contains("png") -> "png"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("mp4") -> "mp4"
            mime.contains("webm") -> "webm"
            mime.startsWith("video/") -> "mp4"
            else -> "png"
        }
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "heif", "heic")
    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov")
}

@Stable
class PopupConfigState(private val appContext: Context) {
    var document: PopupConfigDocument by mutableStateOf(PopupConfigStorage.load(appContext))
        private set

    var selectedProfileId: String by mutableStateOf(PopupProfile.GLOBAL_ID)
        private set

    val activeProfile: PopupProfile
        get() = document.findProfile(selectedProfileId)

    fun selectProfile(profileId: String) {
        selectedProfileId = if (profileId == PopupProfile.GLOBAL_ID) {
            PopupProfile.GLOBAL_ID
        } else {
            val normalized = PopupProfile.normalizeDeviceId(profileId)
            if (document.deviceProfiles.any { it.id == normalized }) normalized else PopupProfile.GLOBAL_ID
        }
    }

    fun updateMasterEnabled(enabled: Boolean) {
        commit(document.copy(masterEnabled = enabled))
    }

    fun ensureDeviceProfile(deviceId: String, displayName: String): PopupProfile {
        val normalized = PopupProfile.normalizeDeviceId(deviceId)
        require(normalized.isNotBlank()) { "设备 ID 不能为空" }
        val existing = document.deviceProfiles.firstOrNull { it.id == normalized }
        if (existing != null) {
            selectedProfileId = existing.id
            return existing
        }
        val created = PopupProfile(
            id = normalized,
            name = displayName.ifBlank { normalized },
            enabled = true,
            inheritGlobal = true,
            buttonStyle = PopupButtonStyle.INHERIT,
        )
        commit(document.copy(deviceProfiles = document.deviceProfiles + created))
        selectedProfileId = created.id
        return created
    }

    fun deleteDeviceProfile(deviceId: String) {
        val normalized = PopupProfile.normalizeDeviceId(deviceId)
        val filtered = document.deviceProfiles.filterNot { it.id == normalized }
        commit(document.copy(deviceProfiles = filtered))
        if (selectedProfileId == normalized) {
            selectedProfileId = PopupProfile.GLOBAL_ID
        }
    }

    fun updateProfile(profileId: String, transform: (PopupProfile) -> PopupProfile) {
        if (profileId == PopupProfile.GLOBAL_ID) {
            val updatedGlobal = transform(document.globalProfile)
                .copy(id = PopupProfile.GLOBAL_ID, inheritGlobal = false)
            commit(document.copy(globalProfile = updatedGlobal))
        } else {
            val normalized = PopupProfile.normalizeDeviceId(profileId)
            val updatedList = document.deviceProfiles.map { profile ->
                if (profile.id == normalized) transform(profile).copy(id = normalized) else profile
            }
            commit(document.copy(deviceProfiles = updatedList))
        }
    }

    fun resetProfile(profileId: String) {
        updateProfile(profileId) { current ->
            PopupProfile(
                id = current.id,
                name = current.name,
                enabled = current.enabled,
                inheritGlobal = current.inheritGlobal,
                buttonStyle = if (current.isGlobal) PopupButtonStyle.NATIVE else PopupButtonStyle.INHERIT,
            )
        }
    }

    fun importMedia(
        uri: Uri,
        slotPrefix: String,
        defaultScaleMode: PopupScaleMode,
        defaultLoop: Boolean,
        crop: PopupCropRect = PopupCropRect.FULL,
    ): Result<PopupMediaSpec> =
        PopupConfigStorage.importMedia(appContext, uri, slotPrefix, defaultScaleMode, defaultLoop, crop)

    fun replaceDocument(imported: PopupConfigDocument) {
        commit(imported)
        if (selectedProfileId != PopupProfile.GLOBAL_ID &&
            document.deviceProfiles.none { it.id == selectedProfileId }
        ) {
            selectedProfileId = PopupProfile.GLOBAL_ID
        }
    }

    private fun commit(next: PopupConfigDocument) {
        document = PopupConfigStorage.save(appContext, next)
    }
}

val LocalPopupConfig = compositionLocalOf<PopupConfigState> {
    error("LocalPopupConfig is not provided")
}

@Composable
fun rememberPopupConfigState(): PopupConfigState {
    val context = LocalContext.current.applicationContext
    return remember(context) { PopupConfigState(context) }
}
