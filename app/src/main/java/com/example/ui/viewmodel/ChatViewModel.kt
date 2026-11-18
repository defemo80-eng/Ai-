package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ChatDepAiService
import com.example.data.model.AppThemeMode
import com.example.data.model.ChatDepModel
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.PendingAttachment
import com.example.data.model.ResponseStyle
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import com.example.util.loadDocumentAttachment
import com.example.util.loadImageAttachment
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "ChatDepVM"
private const val STOP_TIMEOUT_MILLIS = 5000L
private const val AI_TIMEOUT_MILLIS = 60_000L

sealed interface UiDataState<out T> {
    data object Loading : UiDataState<Nothing>
    data class Success<T>(val data: T) : UiDataState<T>
    data class Error(val message: String) : UiDataState<Nothing>
}

data class ChatUiState(
    val activeConversationId: String? = null,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val pendingAttachment: PendingAttachment? = null,
    val selectedModel: ChatDepModel = ChatDepModel.PRO_FLASH,
    val selectedStyle: ResponseStyle = ResponseStyle.BALANCED,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val searchQuery: String = "",
    val errorMessage: String? = null,
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val aiService: ChatDepAiService,
    private val currentUserId: String,
    private val firebaseUser: FirebaseUser?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    private var generationJob: Job? = null

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to observe user profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val conversationsState: StateFlow<UiDataState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiDataState<List<Conversation>>> { UiDataState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", root)
                emit(UiDataState.Error(root.message ?: "No se pudo cargar el historial."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiDataState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiDataState<List<ChatMessage>>> =
        _activeConversationId
            .flatMapLatest { convId ->
                if (convId.isNullOrBlank()) {
                    flowOf(UiDataState.Success(emptyList()))
                } else {
                    repository.observeMessages(currentUserId, convId)
                        .map<List<ChatMessage>, UiDataState<List<ChatMessage>>> {
                            UiDataState.Success(it)
                        }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val root = firestoreError ?: error
                            Log.w(TAG, "Error observing messages", root)
                            emit(UiDataState.Error(root.message ?: "No se pudieron cargar los mensajes."))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiDataState.Success(emptyList())
            )

    init {
        viewModelScope.launch {
            firebaseUser?.let { repository.ensureUserProfile(it) }
        }
        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null) {
                    _uiState.update { state ->
                        state.copy(
                            selectedModel = ChatDepModel.fromId(profile.preferredModel),
                            selectedStyle = ResponseStyle.fromKey(profile.responseStyle)
                        )
                    }
                }
            }
        }
    }

    fun startNewConversation() {
        stopGeneration()
        _activeConversationId.value = null
        _uiState.update {
            it.copy(
                activeConversationId = null,
                streamingText = "",
                isGenerating = false,
                errorMessage = null
            )
        }
    }

    fun selectConversation(conversationId: String) {
        if (_uiState.value.activeConversationId == conversationId) return
        stopGeneration()
        _activeConversationId.value = conversationId
        _uiState.update {
            it.copy(
                activeConversationId = conversationId,
                streamingText = "",
                isGenerating = false,
                errorMessage = null
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun updatePreferences(model: ChatDepModel, style: ResponseStyle) {
        _uiState.update { it.copy(selectedModel = model, selectedStyle = style) }
        viewModelScope.launch {
            val result = repository.updatePreferences(currentUserId, model, style)
            result.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "No se pudo guardar la preferencia.")
                }
            }
        }
    }

    fun attachImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            loadImageAttachment(context, uri)
                .onSuccess { attachment ->
                    _uiState.update { it.copy(pendingAttachment = attachment, errorMessage = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "No se pudo adjuntar la imagen.")
                    }
                }
        }
    }

    fun attachDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            loadDocumentAttachment(context, uri)
                .onSuccess { attachment ->
                    _uiState.update { it.copy(pendingAttachment = attachment, errorMessage = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "No se pudo leer el documento.")
                    }
                }
        }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, streamingText = "") }
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        val attachment = _uiState.value.pendingAttachment
        if ((prompt.isEmpty() && attachment == null) || _uiState.value.isGenerating) return

        val model = _uiState.value.selectedModel
        val style = _uiState.value.selectedStyle
        val displayContent = if (prompt.isNotEmpty()) {
            prompt
        } else {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> "Analiza esta imagen."
                else -> "Resume y analiza este documento."
            }
        }

        val currentMessages = (messagesState.value as? UiDataState.Success)?.data ?: emptyList()

        generationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    streamingText = "",
                    pendingAttachment = null,
                    errorMessage = null
                )
            }

            try {
                // 1. Ensure a conversation exists in Firestore
                var convId = _activeConversationId.value
                val isNewConversation = convId.isNullOrBlank()
                if (isNewConversation) {
                    val initialTitle = displayContent.replace("\n", " ").take(45).ifBlank { "Nueva conversación" }
                    val createResult = repository.createConversation(
                        userId = currentUserId,
                        title = initialTitle,
                        preview = displayContent,
                        modelName = model.modelId
                    )
                    convId = createResult.getOrElse { err ->
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                errorMessage = err.localizedMessage ?: "No se pudo crear la conversación."
                            )
                        }
                        return@launch
                    }
                    _activeConversationId.value = convId
                    _uiState.update { it.copy(activeConversationId = convId) }
                }

                val activeConvId = convId!!

                // 2. Save user message to Firestore
                repository.addMessage(
                    userId = currentUserId,
                    conversationId = activeConvId,
                    role = "user",
                    content = displayContent,
                    hasAttachment = attachment != null,
                    attachmentLabel = attachment?.label,
                    attachmentType = attachment?.typeKey,
                    modelName = null
                ).getOrElse { err ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = err.localizedMessage ?: "No se pudo guardar tu mensaje."
                        )
                    }
                    return@launch
                }

                // 3. Stream Gemini reply via Firebase AI Logic with realistic 60s timeout
                val accumulated = StringBuilder()
                withTimeout(AI_TIMEOUT_MILLIS) {
                    aiService.streamReply(
                        history = currentMessages,
                        userPrompt = displayContent,
                        attachment = attachment,
                        model = model,
                        style = style
                    ).collect { chunkText ->
                        accumulated.append(chunkText)
                        _uiState.update { it.copy(streamingText = accumulated.toString()) }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingText = "",
                            errorMessage = "El modelo devolvió una respuesta vacía. Intenta reformular tu consulta."
                        )
                    }
                    return@launch
                }

                // 4. Persist AI response in Firestore
                repository.addMessage(
                    userId = currentUserId,
                    conversationId = activeConvId,
                    role = "model",
                    content = finalReply,
                    hasAttachment = false,
                    modelName = model.modelId
                )

                val newCount = (currentMessages.size + 2).toLong()
                val betterTitle = if (isNewConversation) {
                    aiService.suggestTitle(displayContent, attachment?.label)
                } else {
                    null
                }
                repository.updateConversationAfterMessage(
                    userId = currentUserId,
                    conversationId = activeConvId,
                    preview = finalReply.replace("\n", " "),
                    messageCount = newCount,
                    modelName = model.modelId,
                    newTitle = betterTitle
                )

                _uiState.update { it.copy(isGenerating = false, streamingText = "") }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "ChatDep AI stream timed out", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        errorMessage = "La respuesta tardó más de 60 segundos. Intenta nuevamente o usa ChatDep Turbo."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ChatDep AI stream failed", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        errorMessage = e.localizedMessage ?: "Ocurrió un error al consultar ChatDep AI."
                    )
                }
            }
        }
    }

    fun regenerateLastResponse() {
        if (_uiState.value.isGenerating) return
        val convId = _activeConversationId.value ?: return
        val messages = (messagesState.value as? UiDataState.Success)?.data ?: return
        val lastModelMsg = messages.lastOrNull { it.role == "model" } ?: return
        val lastUserIndex = messages.indexOfLast { it.role == "user" }
        if (lastUserIndex < 0) return
        val lastUserMsg = messages[lastUserIndex]
        val historyBeforeLastUser = messages.take(lastUserIndex)

        val model = _uiState.value.selectedModel
        val style = _uiState.value.selectedStyle

        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, streamingText = "", errorMessage = null) }
            try {
                val accumulated = StringBuilder()
                withTimeout(AI_TIMEOUT_MILLIS) {
                    aiService.streamReply(
                        history = historyBeforeLastUser,
                        userPrompt = lastUserMsg.content,
                        attachment = null,
                        model = model,
                        style = style
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _uiState.update { it.copy(streamingText = accumulated.toString()) }
                    }
                }
                val regenerated = accumulated.toString().trim()
                if (regenerated.isNotEmpty()) {
                    repository.updateMessageContent(
                        userId = currentUserId,
                        conversationId = convId,
                        messageId = lastModelMsg.id,
                        content = regenerated,
                        modelName = model.modelId
                    )
                    repository.updateConversationAfterMessage(
                        userId = currentUserId,
                        conversationId = convId,
                        preview = regenerated.replace("\n", " "),
                        messageCount = messages.size.toLong(),
                        modelName = model.modelId
                    )
                }
                _uiState.update { it.copy(isGenerating = false, streamingText = "") }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Regenerate timed out", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        errorMessage = "La regeneración superó los 60 segundos."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Regenerate failed", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        errorMessage = e.localizedMessage ?: "No se pudo regenerar la respuesta."
                    )
                }
            }
        }
    }

    fun togglePin(conversation: Conversation) {
        viewModelScope.launch {
            repository.togglePinConversation(
                userId = currentUserId,
                conversationId = conversation.id,
                pinned = !conversation.pinned
            ).onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(currentUserId, conversationId, newTitle)
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.localizedMessage) }
                }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                startNewConversation()
            }
            repository.deleteConversation(currentUserId, conversationId)
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.localizedMessage) }
                }
        }
    }
}
