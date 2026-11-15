package com.yogeshpaliyal.deepr.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.io.OutputStream

object SafPermissionHelper {
    fun hasPersistedWritePermission(
        context: Context,
        uriString: String,
    ): Boolean {
        if (!uriString.startsWith("content://")) return false
        val targetUri = runCatching { uriString.toUri() }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == targetUri && permission.isWritePermission
        }
    }

    fun hasPersistedReadPermission(
        context: Context,
        uriString: String,
    ): Boolean {
        if (!uriString.startsWith("content://")) return false
        val targetUri = runCatching { uriString.toUri() }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == targetUri && (permission.isReadPermission || permission.isWritePermission)
        }
    }

    fun takePersistableWritePermission(
        context: Context,
        uri: Uri,
    ): Boolean {
        val resolver = context.contentResolver
        val rwFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val rwGranted =
            runCatching {
                resolver.takePersistableUriPermission(uri, rwFlags)
                true
            }.getOrDefault(false)

        if (!rwGranted) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        return hasPersistedWritePermission(context, uri.toString())
    }

    fun openWritableSafStream(
        context: Context,
        uri: Uri,
    ): OutputStream? {
        val resolver = context.contentResolver
        val modes = listOf("wt", "rwt", "w")
        var lastModeError: RuntimeException? = null
        for (mode in modes) {
            try {
                return resolver.openOutputStream(uri, mode)
            } catch (e: IllegalArgumentException) {
                lastModeError = e
            } catch (e: UnsupportedOperationException) {
                lastModeError = e
            }
        }
        if (lastModeError != null) {
            throw lastModeError
        }
        return null
    }

    fun formatUriForDisplay(uriString: String): String {
        if (uriString.isBlank()) return ""
        return Uri.decode(uriString.substringAfterLast("/"))
    }
}
