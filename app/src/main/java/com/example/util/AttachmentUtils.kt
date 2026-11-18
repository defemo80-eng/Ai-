package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import com.example.data.model.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_IMAGE_DIMENSION = 1600
private const val MAX_TEXT_DOC_CHARS = 24_000
private const val MAX_BINARY_DOC_BYTES = 12 * 1024 * 1024 // 12 MB

suspend fun loadImageAttachment(context: Context, uri: Uri): Result<PendingAttachment.ImageAttachment> =
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            if (rawW <= 0 || rawH <= 0) {
                return@withContext Result.failure(IllegalArgumentException("No se pudo leer la imagen seleccionada."))
            }

            var sampleSize = 1
            while (max(rawW / sampleSize, rawH / sampleSize) > MAX_IMAGE_DIMENSION * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalArgumentException("No se pudo decodificar la imagen."))

            val scaledBitmap = if (max(decoded.width, decoded.height) > MAX_IMAGE_DIMENSION) {
                val ratio = MAX_IMAGE_DIMENSION.toFloat() / max(decoded.width, decoded.height).toFloat()
                val targetW = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
                decoded.scale(targetW, targetH, filter = true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            // Verify JPEG-compressed payload fits comfortably under Gemini's 20MB limit
            val checkStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 88, checkStream)
            if (checkStream.size() > 15 * 1024 * 1024) {
                val smaller = scaledBitmap.scale(
                    (scaledBitmap.width * 0.7f).roundToInt().coerceAtLeast(1),
                    (scaledBitmap.height * 0.7f).roundToInt().coerceAtLeast(1),
                    filter = true
                )
                if (smaller != scaledBitmap) scaledBitmap.recycle()
                val label = resolveDisplayName(context, uri) ?: "Imagen adjunta"
                Result.success(PendingAttachment.ImageAttachment(smaller, uri, label))
            } else {
                val label = resolveDisplayName(context, uri) ?: "Imagen adjunta"
                Result.success(PendingAttachment.ImageAttachment(scaledBitmap, uri, label))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

suspend fun loadDocumentAttachment(context: Context, uri: Uri): Result<PendingAttachment> =
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "text/plain"
            val name = resolveDisplayName(context, uri) ?: "Documento"

            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(data)
                    if (read == -1) break
                    total += read
                    if (total > MAX_BINARY_DOC_BYTES) {
                        return@withContext Result.failure(
                            IllegalArgumentException("El archivo supera el límite de 12 MB.")
                        )
                    }
                    buffer.write(data, 0, read)
                }
                buffer.toByteArray()
            } ?: return@withContext Result.failure(IllegalArgumentException("No se pudo abrir el documento."))

            val lowerName = name.lowercase()
            val isPdf = mimeType.contains("pdf", ignoreCase = true) || lowerName.endsWith(".pdf")
            if (isPdf) {
                return@withContext Result.success(
                    PendingAttachment.BinaryDocumentAttachment(
                        label = name,
                        bytes = bytes,
                        mimeType = "application/pdf"
                    )
                )
            }

            val isTextLike = mimeType.startsWith("text/") ||
                mimeType.contains("json") ||
                mimeType.contains("xml") ||
                mimeType.contains("javascript") ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".md") ||
                lowerName.endsWith(".csv") ||
                lowerName.endsWith(".json") ||
                lowerName.endsWith(".kt") ||
                lowerName.endsWith(".java") ||
                lowerName.endsWith(".py") ||
                lowerName.endsWith(".js") ||
                lowerName.endsWith(".ts") ||
                lowerName.endsWith(".html") ||
                lowerName.endsWith(".css") ||
                lowerName.endsWith(".xml") ||
                lowerName.endsWith(".sql")

            if (isTextLike) {
                val text = bytes.toString(Charsets.UTF_8)
                val truncated = if (text.length > MAX_TEXT_DOC_CHARS) {
                    text.take(MAX_TEXT_DOC_CHARS) + "\n\n[... contenido recortado por extensión ...]"
                } else {
                    text
                }
                Result.success(PendingAttachment.TextDocumentAttachment(label = name, extractedText = truncated))
            } else {
                Result.failure(
                    IllegalArgumentException("Formato no compatible. Selecciona PDF, TXT, Markdown, CSV o archivos de código.")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    } catch (_: Exception) {
        null
    }
}
