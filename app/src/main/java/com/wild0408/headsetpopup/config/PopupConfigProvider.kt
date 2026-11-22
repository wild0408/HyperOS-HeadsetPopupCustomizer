package com.wild0408.headsetpopup.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.FileNotFoundException

/**
 * Read-only cross-process provider allowing com.xiaomi.bluetooth:ui to fetch
 * live popup customization JSON and open private image/video assets safely.
 */
class PopupConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        enforceAllowedCaller()
        val ctx = context ?: return Bundle()
        return when (method) {
            METHOD_GET_RESOLVED_CONFIG -> {
                val doc = PopupConfigStorage.load(ctx)
                val resolved = doc.resolveForDevice(arg)
                Bundle().apply {
                    putString(KEY_CONFIG_JSON, resolved.toJson().toString())
                    putLong(KEY_UPDATED_AT, doc.updatedAt)
                    putString(KEY_AUTHORITY, AUTHORITY)
                }
            }
            METHOD_GET_RAW_CONFIG -> {
                val doc = PopupConfigStorage.load(ctx)
                Bundle().apply {
                    putString(KEY_CONFIG_JSON, doc.toJson().toString())
                    putLong(KEY_UPDATED_AT, doc.updatedAt)
                    putString(KEY_AUTHORITY, AUTHORITY)
                }
            }
            else -> Bundle()
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        enforceAllowedCaller()
        if (mode != "r") {
            throw SecurityException("PopupConfigProvider is read-only")
        }
        val ctx = context ?: throw FileNotFoundException("Context unavailable")
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != PATH_MEDIA) {
            throw FileNotFoundException("Unsupported media uri: $uri")
        }
        val file = PopupConfigStorage.mediaFile(ctx, segments[1])
            ?: throw FileNotFoundException("Invalid media asset name")
        if (!file.exists() || !file.isFile) {
            throw FileNotFoundException("Media asset not found: ${segments[1]}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun enforceAllowedCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid == Process.BLUETOOTH_UID || uid == Process.ROOT_UID || uid == Process.SHELL_UID) {
            return
        }
        val pkg = callingPackage
        if (pkg == "com.xiaomi.bluetooth" || pkg == context?.packageName) {
            return
        }
        throw SecurityException("Caller not permitted: pkg=$pkg uid=$uid")
    }

    companion object {
        const val AUTHORITY = "com.wild0408.headsetpopup.popupconfig"
        const val METHOD_GET_RESOLVED_CONFIG = "getResolvedConfig"
        const val METHOD_GET_RAW_CONFIG = "getRawConfig"
        const val KEY_CONFIG_JSON = "config_json"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_AUTHORITY = "authority"
        const val PATH_MEDIA = "media"

        fun mediaUri(assetName: String): Uri =
            Uri.parse("content://$AUTHORITY/$PATH_MEDIA/$assetName")
    }
}
