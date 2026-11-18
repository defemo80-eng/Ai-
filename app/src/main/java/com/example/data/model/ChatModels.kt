package com.example.data.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ChatDepModel(
    val modelId: String,
    val displayName: String,
    val subtitle: String,
) {
    PRO_FLASH(
        modelId = "gemini-3.8-flash",
        displayName = "ChatDep Pro",
        subtitle = "Razonamiento avanzado, programación y visión multimodal"
    ),
    TURBO_LITE(
        modelId = "gemini-3.5-flash-lite",
        displayName = "ChatDep Turbo",
        subtitle = "Respuestas ultrarrápidas para consultas ágiles"
    );

    companion object {
        fun fromId(id: String?): ChatDepModel =
            entries.firstOrNull { it.modelId == id } ?: PRO_FLASH
    }
}

enum class ResponseStyle(
    val key: String,
    val displayName: String,
    val description: String,
) {
    BALANCED(
        key = "balanced",
        displayName = "Equilibrado",
        description = "Natural, claro y preciso para cualquier tema"
    ),
    CODING(
        key = "coding",
        displayName = "Código y Proyectos",
        description = "Arquitectura limpia, código completo y depuración"
    ),
    TUTOR(
        key = "tutor",
        displayName = "Estudio y Tutor",
        description = "Explicaciones paso a paso, tareas y matemáticas"
    ),
    CONCISE(
        key = "concise",
        displayName = "Directo",
        description = "Respuestas breves, estructuradas y al grano"
    );

    companion object {
        fun fromKey(key: String?): ResponseStyle =
            entries.firstOrNull { it.key == key } ?: BALANCED
    }
}

enum class AppThemeMode(val label: String) {
    DARK("Azul Oscuro"),
    LIGHT("Blanco Claro"),
    SYSTEM("Sistema")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredModel: String = ChatDepModel.PRO_FLASH.modelId,
    val responseStyle: String = ResponseStyle.BALANCED.key,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Nueva conversación",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val pinned: Boolean = false,
    val modelName: String = ChatDepModel.PRO_FLASH.modelId,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val content: String = "",
    val hasAttachment: Boolean = false,
    val attachmentLabel: String? = null,
    val attachmentType: String? = null,
    val modelName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

sealed interface PendingAttachment {
    val label: String
    val typeKey: String

    data class ImageAttachment(
        val bitmap: Bitmap,
        val previewUri: Uri?,
        override val label: String = "Imagen adjunta",
    ) : PendingAttachment {
        override val typeKey: String = "image"
    }

    data class TextDocumentAttachment(
        override val label: String,
        val extractedText: String,
    ) : PendingAttachment {
        override val typeKey: String = "document"
    }

    data class BinaryDocumentAttachment(
        override val label: String,
        val bytes: ByteArray,
        val mimeType: String,
    ) : PendingAttachment {
        override val typeKey: String = "document"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BinaryDocumentAttachment) return false
            return label == other.label && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = label.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }
    }
}
