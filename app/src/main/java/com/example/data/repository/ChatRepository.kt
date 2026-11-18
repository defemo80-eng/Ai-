package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.ChatDepModel
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ResponseStyle
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para acceder a ChatDep.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "La sesión activa cambió." }
        }
        return uid
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val safeName = (
                user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: "Usuario ChatDep"
                ).take(100)
            val safeEmail = user.email?.takeIf { it.isNotBlank() }?.take(200)
            val safePhoto = user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.take(500)

            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "preferredModel" to ChatDepModel.PRO_FLASH.modelId,
                    "responseStyle" to ResponseStyle.BALANCED.key,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val updateMap = mapOf(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updatePreferences(
        userId: String,
        preferredModel: ChatDepModel,
        responseStyle: ResponseStyle,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "preferredModel" to preferredModel.modelId,
                        "responseStyle" to responseStyle.key,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users").document(uid)
                .collection("conversations")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<Conversation> { it.pinned }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId(userId)
        require(conversationId.isNotBlank()) { "conversationId no puede estar vacío." }
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversation(
        userId: String,
        title: String,
        preview: String,
        modelName: String,
    ): Result<String> {
        val basePath = "users/$userId/conversations"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document()
            val safeTitle = title.trim().ifBlank { "Nueva conversación" }.take(120)
            val safePreview = preview.trim().take(240)
            val safeModel = ChatDepModel.fromId(modelName).modelId
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "lastMessagePreview" to safePreview,
                "messageCount" to 0L,
                "pinned" to false,
                "modelName" to safeModel,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, basePath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        userId: String,
        conversationId: String,
        role: String,
        content: String,
        hasAttachment: Boolean,
        attachmentLabel: String? = null,
        attachmentType: String? = null,
        modelName: String? = null,
    ): Result<String> {
        val basePath = "users/$userId/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank()) { "conversationId requerido." }
            require(role == "user" || role == "model") { "Rol inválido." }
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").document()
            val safeContent = content.take(50_000)
            val payload = mapOf(
                "userId" to uid,
                "conversationId" to conversationId,
                "role" to role,
                "content" to safeContent,
                "hasAttachment" to hasAttachment,
                "attachmentLabel" to attachmentLabel?.takeIf { it.isNotBlank() }?.take(200),
                "attachmentType" to attachmentType?.takeIf { it == "image" || it == "document" },
                "modelName" to modelName?.takeIf { it.isNotBlank() }?.take(60),
                "createdAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, basePath)
            Result.failure(e)
        }
    }

    suspend fun updateConversationAfterMessage(
        userId: String,
        conversationId: String,
        preview: String,
        messageCount: Long,
        modelName: String,
        newTitle: String? = null,
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            val updates = mutableMapOf<String, Any>(
                "lastMessagePreview" to preview.trim().take(240),
                "messageCount" to messageCount.coerceIn(0L, 10_000L),
                "modelName" to ChatDepModel.fromId(modelName).modelId,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (!newTitle.isNullOrBlank()) {
                updates["title"] = newTitle.trim().take(120)
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMessageContent(
        userId: String,
        conversationId: String,
        messageId: String,
        content: String,
        modelName: String,
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages/$messageId"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank() && messageId.isNotBlank())
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "content" to content.take(50_000),
                        "modelName" to modelName.take(60),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePinConversation(
        userId: String,
        conversationId: String,
        pinned: Boolean,
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank())
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "pinned" to pinned,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(
        userId: String,
        conversationId: String,
        newTitle: String,
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank())
            val safeTitle = newTitle.trim().take(120)
            require(safeTitle.isNotEmpty()) { "El título no puede estar vacío." }
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "title" to safeTitle,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(
        userId: String,
        conversationId: String,
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            val uid = requireUserId(userId)
            require(conversationId.isNotBlank())
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)

            // Delete child messages in chunks of up to 400
            val messagesSnap = convRef.collection("messages").get().await()
            messagesSnap.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
