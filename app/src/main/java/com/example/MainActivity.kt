package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.signOut
import com.example.data.ai.ChatDepAiService
import com.example.data.model.AppThemeMode
import com.example.data.repository.ChatRepository
import com.example.firebase.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDepAppScreen
import com.example.ui.theme.ChatDepTheme
import com.example.ui.viewmodel.ChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ChatDepRoot()
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun ChatDepRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var signedOutExplicitly by remember { mutableStateOf(false) }
    val user = currentUser

    if (user == null) {
        ChatDepTheme(darkTheme = true) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AuthScreen(
                    skipAutoSignIn = signedOutExplicitly,
                    onAuthSuccess = { signedOutExplicitly = false }
                )
            }
        }
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedChatDepContent(
                currentUserId = uid,
                firebaseUser = user,
                onSignedOut = { signedOutExplicitly = true }
            )
        }
    }
}

@Composable
private fun AuthenticatedChatDepContent(
    currentUserId: String,
    firebaseUser: FirebaseUser,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ChatViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ChatViewModel(
                    repository = ChatRepository(db),
                    aiService = ChatDepAiService(),
                    currentUserId = currentUserId,
                    firebaseUser = firebaseUser
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val useDarkTheme = when (uiState.themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    ChatDepTheme(darkTheme = useDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatDepAppScreen(
                viewModel = viewModel,
                user = firebaseUser,
                onSignOut = {
                    onSignedOut()
                    viewModel.stopGeneration()
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}
