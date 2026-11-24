package com.wild0408.headsetpopup.config

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupExportSummary(
    val profileCount: Int,
    val mediaCount: Int,
    val totalBytes: Long,
)

data class BackupImportSummary(
    val document: PopupConfigDocument,
    val profileCount: Int,
    val restoredMediaCount: Int,
    val missingMediaCount: Int,
)

object PopupConfigBackupManager {
    private const val BACKUP_FORMAT_VERSION = 1
    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_CONFIG = "popup_config.json"
    private const val MEDIA_PREFIX = "media/"
    private const val MAX_CONFIG_BYTES = 4 * 1024 * 1024 // 4 MB safety guard

    fun defaultBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "headsetpopup-backup-$stamp.zip"
    }

    suspend fun exportBackup(
        context: Context,
        targetUri: Uri,
        document: PopupConfigDocument,
    ): Result<BackupExportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val referencedAssets = document.collectAllAssetNames()
            val mediaFiles = referencedAssets.mapNotNull { name ->
                PopupConfigStorage.mediaFile(appContext, name)?.takeIf { it.exists() && it.isFile }?.let { name to it }
            }

            val configBytes = document.toJson().toString(2).toByteArray(Charsets.UTF_8)
            val appVersion = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
            }.getOrDefault("")

            val manifestBytes = JSONObject().apply {
                put("backupFormatVersion", BACKUP_FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("appVersion", appVersion)
                put("deviceProfileCount", document.deviceProfiles.size)
                put("mediaCount", mediaFiles.size)
            }.toString(2).toByteArray(Charsets.UTF_8)

            var totalBytes = configBytes.size.toLong() + manifestBytes.size.toLong()
            val outputStream = appContext.contentResolver.openOutputStream(targetUri)
                ?: error("无法打开导出目标文件")

            outputStream.use { rawOut ->
                ZipOutputStream(BufferedOutputStream(rawOut)).use { zip ->
                    zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                    zip.write(manifestBytes)
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(ENTRY_CONFIG))
                    zip.write(configBytes)
                    zip.closeEntry()

                    for ((assetName, file) in mediaFiles) {
                        zip.putNextEntry(ZipEntry("$MEDIA_PREFIX$assetName"))
                        file.inputStream().use { input ->
                            totalBytes += input.copyTo(zip)
                        }
                        zip.closeEntry()
                    }
                }
            }

            BackupExportSummary(
                profileCount = document.deviceProfiles.size,
                mediaCount = mediaFiles.size,
                totalBytes = totalBytes,
            )
        }
    }

    suspend fun importBackup(
        context: Context,
        sourceUri: Uri,
    ): Result<BackupImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val stagingDir = File(appContext.cacheDir, "backup_import_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            try {
                var configJsonText: String? = null
                val extractedMedia = mutableMapOf<String, File>()

                val inputStream = appContext.contentResolver.openInputStream(sourceUri)
                    ?: error("无法读取所选备份文件")

                inputStream.use { rawIn ->
                    ZipInputStream(BufferedInputStream(rawIn)).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val entryName = entry.name.orEmpty().replace('\\', '/')
                            if (entry.isDirectory || entryName.startsWith("/") || entryName.contains("..")) {
                                zip.closeEntry()
                                continue
                            }
                            when {
                                entryName == ENTRY_CONFIG -> {
                                    val bytes = readLimitedBytes(zip, MAX_CONFIG_BYTES)
                                    configJsonText = bytes.toString(Charsets.UTF_8)
                                }
                                entryName.startsWith(MEDIA_PREFIX) -> {
                                    val assetName = entryName.removePrefix(MEDIA_PREFIX)
                                    if (isSafeSingleSegmentName(assetName)) {
                                        val stagedFile = File(stagingDir, assetName)
                                        stagedFile.outputStream().use { out ->
                                            zip.copyTo(out)
                                        }
                                        if (stagedFile.length() > 0L) {
                                            extractedMedia[assetName] = stagedFile
                                        } else {
                                            stagedFile.delete()
                                        }
                                    }
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }

                val rawConfig = configJsonText?.takeIf { it.isNotBlank() }
                    ?: error("备份包中缺少 $ENTRY_CONFIG，请确认是否为有效的配置备份文件")

                val importedDoc = runCatching { PopupConfigDocument.fromJson(rawConfig) }
                    .getOrElse { err -> error("备份配置解析失败：${err.message ?: "JSON 格式无效"}") }

                val referencedAssets = importedDoc.collectAllAssetNames()
                val targetMediaDir = PopupConfigStorage.mediaDir(appContext)
                var restoredCount = 0
                var missingCount = 0

                for (assetName in referencedAssets) {
                    val staged = extractedMedia[assetName]
                    val target = PopupConfigStorage.mediaFile(appContext, assetName)
                    if (staged != null && staged.isFile && target != null) {
                        val tmp = File(targetMediaDir, "$assetName.tmp")
                        staged.inputStream().use { input ->
                            tmp.outputStream().use { out ->
                                input.copyTo(out)
                            }
                        }
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        restoredCount++
                    } else if (target == null || !target.isFile) {
                        missingCount++
                    }
                }

                BackupImportSummary(
                    document = importedDoc,
                    profileCount = importedDoc.deviceProfiles.size,
                    restoredMediaCount = restoredCount,
                    missingMediaCount = missingCount,
                )
            } finally {
                runCatching { stagingDir.deleteRecursively() }
            }
        }
    }

    private fun isSafeSingleSegmentName(name: String): Boolean {
        return name.isNotBlank() &&
            !name.contains('/') &&
            !name.contains('\\') &&
            !name.contains("..")
    }

    private fun readLimitedBytes(zip: ZipInputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = zip.read(buf)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                error("配置文件体积超出限制")
            }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }
}
