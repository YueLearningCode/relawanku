package com.example.relawanku.data

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UploadPart(val file: File, val part: MultipartBody.Part) {
    companion object {
        fun fromUri(context: Context, uri: Uri): UploadPart {
            val mime =
                context.contentResolver.getType(uri)
                    ?: throw IllegalArgumentException("Jenis foto belum dikenali.")
            val extension =
                when (mime) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> throw IllegalArgumentException("Gunakan foto JPEG, PNG, atau WebP.")
                }
            val file = File.createTempFile("upload-", ".$extension", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 5L * 1024 * 1024) { "Ukuran foto maksimal 5 MB." }
                            out.write(buffer, 0, count)
                        }
                        require(total > 0) { "Foto kosong atau tidak dapat dibaca." }
                    }
                } ?: throw IllegalArgumentException("Foto tidak dapat dibaca.")
                return UploadPart(
                    file,
                    MultipartBody.Part.createFormData(
                        "image",
                        file.name,
                        file.asRequestBody(mime.toMediaType()),
                    ),
                )
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }
}
