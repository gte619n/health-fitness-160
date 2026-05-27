package com.gte619n.healthfitness.network.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

/**
 * Bridges an Android [Uri] (typically returned by
 * `ActivityResultContracts.GetContent`) into the platform-agnostic
 * [PendingUpload] shape that [MultipartUploadClient] consumes.
 *
 * Kept in `core-network` (next to the upload client) so feature modules
 * don't have to invent their own per-screen "Uri -> PendingUpload"
 * wrappers. The [ContentResolver] dependency stays here so the upload
 * client itself remains JVM-testable with `MockWebServer`.
 */
object UriUploads {

    /**
     * Resolves [uri] into a [PendingUpload] using the supplied
     * [ContentResolver]. Falls back to a generic image MIME type and a
     * sensible default filename when the provider's metadata is missing
     * — `ActivityResultContracts.GetContent("image-slash-star")`
     * always yields an image, so the fallback is "good enough" for the
     * gym cover-photo path.
     */
    fun from(
        contentResolver: ContentResolver,
        uri: Uri,
        defaultFilename: String = "upload.jpg",
        defaultMimeType: String = "image/jpeg",
    ): PendingUpload {
        val mimeType = contentResolver.getType(uri) ?: defaultMimeType
        val filename = resolveDisplayName(contentResolver, uri) ?: defaultFilename
        val size = resolveSize(contentResolver, uri)
        return PendingUpload(
            filename = filename,
            mimeType = mimeType,
            source = {
                contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not open input stream for $uri")
            },
            sizeBytes = size,
        )
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val cursor = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) return null
            return it.getString(index)
        }
    }

    private fun resolveSize(resolver: ContentResolver, uri: Uri): Long? {
        val cursor = resolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(OpenableColumns.SIZE)
            if (index < 0 || it.isNull(index)) return null
            return it.getLong(index)
        }
    }
}
