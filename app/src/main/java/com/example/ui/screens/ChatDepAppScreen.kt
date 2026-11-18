package com.example.ui.screens

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatDepModel
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.PendingAttachment
import com.example.ui.components.MarkdownMessageContent
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DeepElectricBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.UiDataState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ChatDepAppScreen(
    viewModel: ChatViewModel,
    user: FirebaseUser?,
    onSignOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showSettingsSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImage(context, uri)
        }
    }

    // Storage Access Framework Document Picker (PDF, text, code, markdown, CSV)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachDocument(context, uri)
        }
    }

    // Back handler when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 840.dp

        val drawerContent: @Composable () -> Unit = {
            ChatDepDrawerContent(
                user = user,
                conversationsState = conversationsState,
                activeConversationId = uiState.activeConversationId,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onNewConversation = {
                    viewModel.startNewConversation()
                    if (!isExpandedScreen) scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    if (!isExpandedScreen) scope.launch { drawerState.close() }
                },
                onTogglePin = viewModel::togglePin,
                onRequestRename = { conv -> renameTarget = conv },
                onRequestDelete = { conv -> deleteTarget = conv },
                onOpenSettings = {
                    if (!isExpandedScreen) scope.launch { drawerState.close() }
                    showSettingsSheet = true
                },
                onSignOut = onSignOut
            )
        }

        if (isExpandedScreen) {
            PermanentNavigationDrawer(
                drawerContent = drawerContent
            ) {
                ChatWorkspace(
                    uiState = uiState,
                    messagesState = messagesState,
                    user = user,
                    isExpandedScreen = true,
                    onOpenDrawer = {},
                    onNewChat = viewModel::startNewConversation,
                    onOpenSettings = { showSettingsSheet = true },
                    onSendMessage = viewModel::sendMessage,
                    onStopGeneration = viewModel::stopGeneration,
                    onRegenerate = viewModel::regenerateLastResponse,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickDocument = {
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/*",
                                "application/json",
                                "application/xml"
                            )
                        )
                    },
                    onClearAttachment = viewModel::clearAttachment,
                    onDismissError = viewModel::dismissError,
                    onQuickModelSwitch = { nextModel ->
                        viewModel.updatePreferences(nextModel, uiState.selectedStyle)
                    }
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = drawerContent
            ) {
                ChatWorkspace(
                    uiState = uiState,
                    messagesState = messagesState,
                    user = user,
                    isExpandedScreen = false,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = viewModel::startNewConversation,
                    onOpenSettings = { showSettingsSheet = true },
                    onSendMessage = viewModel::sendMessage,
                    onStopGeneration = viewModel::stopGeneration,
                    onRegenerate = viewModel::regenerateLastResponse,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickDocument = {
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/*",
                                "application/json",
                                "application/xml"
                            )
                        )
                    },
                    onClearAttachment = viewModel::clearAttachment,
                    onDismissError = viewModel::dismissError,
                    onQuickModelSwitch = { nextModel ->
                        viewModel.updatePreferences(nextModel, uiState.selectedStyle)
                    }
                )
            }
        }
    }

    if (showSettingsSheet) {
        ChatDepSettingsSheet(
            selectedModel = uiState.selectedModel,
            selectedStyle = uiState.selectedStyle,
            themeMode = uiState.themeMode,
            onSelectModel = { model -> viewModel.updatePreferences(model, uiState.selectedStyle) },
            onSelectStyle = { style -> viewModel.updatePreferences(uiState.selectedModel, style) },
            onSelectTheme = viewModel::setThemeMode,
            onDismiss = { showSettingsSheet = false }
        )
    }

    renameTarget?.let { target ->
        RenameConversationDialog(
            currentTitle = target.title,
            onConfirm = { newTitle ->
                viewModel.renameConversation(target.id, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar conversación") },
            text = {
                Text("¿Seguro que deseas eliminar \"${target.title}\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(target.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatWorkspace(
    uiState: ChatUiState,
    messagesState: UiDataState<List<ChatMessage>>,
    user: FirebaseUser?,
    isExpandedScreen: Boolean,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerate: () -> Unit,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onClearAttachment: () -> Unit,
    onDismissError: () -> Unit,
    onQuickModelSwitch: (ChatDepModel) -> Unit,
) {
    val context = LocalContext.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val esLocale = Locale("es", "ES")
                val res = instance?.setLanguage(esLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    instance?.setLanguage(Locale.getDefault())
                }
                ttsReady = true
            }
        }
        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                speakingMessageId = null
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speakingMessageId = null
            }
        })
        instance
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiDataState.Success)?.data ?: emptyList()

    LaunchedEffect(messages.size, uiState.streamingText.length, uiState.isGenerating) {
        val totalItems = messages.size + if (uiState.isGenerating) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    if (!isExpandedScreen) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(id = R.string.cd_open_drawer)
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ChatDep",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    val next = if (uiState.selectedModel == ChatDepModel.PRO_FLASH) {
                                        ChatDepModel.TURBO_LITE
                                    } else {
                                        ChatDepModel.PRO_FLASH
                                    }
                                    onQuickModelSwitch(next)
                                }
                                .testTag("model_pill_button")
                        ) {
                            Text(
                                text = uiState.selectedModel.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddComment,
                            contentDescription = stringResource(id = R.string.cd_new_chat)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(id = R.string.cd_settings)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.pendingAttachment?.let { attachment ->
                        PendingAttachmentChip(
                            attachment = attachment,
                            onRemove = onClearAttachment
                        )
                    }

                    MessageComposerBar(
                        text = inputText,
                        onTextChange = { inputText = it },
                        isGenerating = uiState.isGenerating,
                        hasAttachment = uiState.pendingAttachment != null,
                        onSend = {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend)
                        },
                        onStop = onStopGeneration,
                        onPickImage = onPickImage,
                        onPickDocument = onPickDocument
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cerrar error",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (messagesState) {
                    is UiDataState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is UiDataState.Error -> {
                        Text(
                            text = messagesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    is UiDataState.Success -> {
                        if (messages.isEmpty() && !uiState.isGenerating) {
                            ChatDepWelcomeContent(
                                userName = user?.displayName,
                                isWideScreen = isExpandedScreen,
                                onSendPrompt = { prompt -> onSendMessage(prompt) },
                                onPickImage = onPickImage,
                                onPickDocument = onPickDocument
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 14.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxSize()
                            ) {
                                val lastModelIdx = messages.indexOfLast { it.role == "model" }
                                itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                                    if (msg.role == "user") {
                                        UserMessageBubble(message = msg)
                                    } else {
                                        val isSpeaking = speakingMessageId == msg.id
                                        AssistantMessageBubble(
                                            content = msg.content,
                                            modelTag = msg.modelName,
                                            isLatest = index == lastModelIdx && !uiState.isGenerating,
                                            isSpeaking = isSpeaking,
                                            canSpeak = ttsReady,
                                            onToggleSpeak = {
                                                if (isSpeaking) {
                                                    tts.stop()
                                                    speakingMessageId = null
                                                } else if (ttsReady) {
                                                    speakingMessageId = msg.id
                                                    val cleanForSpeech = msg.content
                                                        .replace("```", " ")
                                                        .replace("**", "")
                                                        .take(3500)
                                                    tts.speak(
                                                        cleanForSpeech,
                                                        TextToSpeech.QUEUE_FLUSH,
                                                        null,
                                                        msg.id
                                                    )
                                                }
                                            },
                                            onRegenerate = onRegenerate
                                        )
                                    }
                                }

                                if (uiState.isGenerating) {
                                    item(key = "streaming_bubble") {
                                        StreamingAssistantBubble(streamingText = uiState.streamingText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 6.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            color = Color.Transparent,
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(ElectricBlueBright, DeepElectricBlue))
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.hasAttachment && !message.attachmentLabel.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CrispWhite.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (message.attachmentType == "image") {
                                        Icons.Rounded.Image
                                    } else {
                                        Icons.Rounded.Description
                                    },
                                    contentDescription = null,
                                    tint = CrispWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message.attachmentLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CrispWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CrispWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    content: String,
    modelTag: String?,
    isLatest: Boolean,
    isSpeaking: Boolean,
    canSpeak: Boolean,
    onToggleSpeak: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.chatdep_logo),
            contentDescription = stringResource(id = R.string.cd_chatdep_logo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ChatDep",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!modelTag.isNullOrBlank()) {
                        Text(
                            text = ChatDepModel.fromId(modelTag).displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                MarkdownMessageContent(
                    markdown = content,
                    textColor = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(content))
                            copied = true
                        },
                        modifier = Modifier.testTag("copy_message_button")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(id = R.string.cd_copy_message),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (copied) "Copiado" else "Copiar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    if (canSpeak) {
                        TextButton(onClick = onToggleSpeak) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp,
                                contentDescription = stringResource(id = R.string.cd_speak_message),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSpeaking) "Detener voz" else "Escuchar",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (isLatest) {
                        TextButton(
                            onClick = onRegenerate,
                            modifier = Modifier.testTag("regenerate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(id = R.string.cd_regenerate),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(streamingText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "streamingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.chatdep_logo),
            contentDescription = stringResource(id = R.string.cd_chatdep_logo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(CyanGlow)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (streamingText.isBlank()) "ChatDep está pensando..." else "ChatDep está respondiendo...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (streamingText.isNotBlank()) {
                    MarkdownMessageContent(
                        markdown = streamingText,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentChip(
    attachment: PendingAttachment,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> {
                    Image(
                        bitmap = attachment.bitmap.asImageBitmap(),
                        contentDescription = attachment.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (attachment is PendingAttachment.ImageAttachment) {
                        "Imagen lista para analizar"
                    } else {
                        "Documento listo para resumir o consultar"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(id = R.string.cd_remove_attachment)
                )
            }
        }
    }
}

@Composable
private fun MessageComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    hasAttachment: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
) {
    var attachMenuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box {
                IconButton(
                    onClick = { attachMenuOpen = true },
                    enabled = !isGenerating,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(id = R.string.cd_attach),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = attachMenuOpen,
                    onDismissRequest = { attachMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.attach_image)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Image, contentDescription = null)
                        },
                        onClick = {
                            attachMenuOpen = false
                            onPickImage()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.attach_document)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.AttachFile, contentDescription = null)
                        },
                        onClick = {
                            attachMenuOpen = false
                            onPickDocument()
                        }
                    )
                }
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.message_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_input")
            )

            val canSend = (text.isNotBlank() || hasAttachment) && !isGenerating
            IconButton(
                onClick = {
                    if (isGenerating) onStop() else if (canSend) onSend()
                },
                enabled = isGenerating || canSend,
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isGenerating -> MaterialTheme.colorScheme.error
                            canSend -> ElectricBlue
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                    contentDescription = if (isGenerating) {
                        stringResource(id = R.string.stop_generation)
                    } else {
                        stringResource(id = R.string.cd_send)
                    },
                    tint = if (isGenerating || canSend) {
                        CrispWhite
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RenameConversationDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar conversación") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 120) title = it },
                singleLine = true,
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title) },
                enabled = title.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
