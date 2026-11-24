package com.wild0408.headsetpopup.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestTag: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/wild0408/HyperOS-HeadsetPopupCustomizer/releases/latest"
    const val RELEASES_PAGE_URL =
        "https://github.com/wild0408/HyperOS-HeadsetPopupCustomizer/releases"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    suspend fun checkLatestRelease(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "HyperOS-HeadsetPopupCustomizer/$currentVersionName")
            }

            val code = connection.responseCode
            if (code == 404) {
                return@withContext UpdateCheckResult.Error("仓库暂无已发布的 Release")
            }
            if (code == 403) {
                return@withContext UpdateCheckResult.Error("GitHub API 请求频率受限，请稍后重试或前往 Releases 页面查看")
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("检查更新失败（HTTP $code）")
            }

            val bodyText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) {
                return@withContext UpdateCheckResult.Error("无法解析远端版本信息")
            }

            val title = json.optString("name", "").trim().ifBlank { tagName }
            val notes = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", "").trim().ifBlank { RELEASES_PAGE_URL }
            val publishedAt = json.optString("published_at", "").trim()

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                        apkSize = asset.optLong("size", 0L).coerceAtLeast(0L)
                        break
                    }
                }
            }

            if (compareSemVer(tagName, currentVersionName) > 0) {
                UpdateCheckResult.UpdateAvailable(
                    ReleaseInfo(
                        tagName = tagName,
                        title = title,
                        notes = notes,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName,
                        apkSizeBytes = apkSize,
                        publishedAt = publishedAt,
                    ),
                )
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = currentVersionName,
                    latestTag = tagName,
                )
            }
        } catch (error: Throwable) {
            UpdateCheckResult.Error("网络请求失败：${error.message ?: error.javaClass.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Compares two semantic version strings (e.g., "v2.0.2" vs "2.0.0-hook-foundation").
     * Returns > 0 if [remote] is newer than [local].
     */
    internal fun compareSemVer(remote: String, local: String): Int {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        val maxLen = maxOf(r.numbers.size, l.numbers.size, 3)
        for (i in 0 until maxLen) {
            val rv = r.numbers.getOrElse(i) { 0 }
            val lv = l.numbers.getOrElse(i) { 0 }
            if (rv != lv) return rv.compareTo(lv)
        }
        // Stable release (no pre-release suffix) is considered newer than pre-release of same numeric version.
        return when {
            r.preRelease.isEmpty() && l.preRelease.isNotEmpty() -> 1
            r.preRelease.isNotEmpty() && l.preRelease.isEmpty() -> -1
            else -> r.preRelease.compareTo(l.preRelease, ignoreCase = true)
        }
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: String,
    )

    private fun parseVersion(raw: String): ParsedVersion {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        val core = trimmed.substringBefore('-').substringBefore('+')
        val pre = if ('-' in trimmed) trimmed.substringAfter('-').substringBefore('+') else ""
        val nums = core.split('.').mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        return ParsedVersion(nums, pre)
    }
}
