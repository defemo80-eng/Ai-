package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "ChatDepAuth"

private fun resolveWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return try {
        context.getString(resId).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (message: String, needsGoogleAccount: Boolean) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
) {
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("No se pudo obtener la actividad actual para iniciar sesión.", false)
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onAuthError("Falta la configuración de Google Sign-In (google-services.json).", false)
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                onAuthSuccess()
            } else {
                onAuthError("Tipo de credencial inesperado.", false)
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credentials found on device: ${e.message}", e)
            onAuthError(
                "No se encontraron correos de Google en este dispositivo. Agrega tu cuenta Google para continuar.",
                true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "No se pudo iniciar sesión con Google.", false)
        }
    }
}

fun onChooseGoogleEmailClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (message: String, needsGoogleAccount: Boolean) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
) {
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("No se pudo obtener la actividad actual para elegir correo.", false)
        return
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onAuthError("Falta la configuración de Google Sign-In (google-services.json).", false)
        return
    }

    scope.launch {
        try {
            // Clear cached credential selection so all available Google email options appear
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "clearCredentialState skipped: ${e.message}")
            }

            // Single-option request showing all Google email accounts without auto-select
            val allAccountsOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId(clientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(allAccountsOption)
                .build()

            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                onAuthSuccess()
            } else {
                onAuthError("Tipo de credencial inesperado.", false)
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google email picker cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google email options available via GetGoogleIdOption, falling back to SignInWithGoogle", e)
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onAuthError = onAuthError,
                scope = scope,
                onAuthCancelled = onAuthCancelled
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google email selection failed", e)
            onAuthError(e.localizedMessage ?: "No se pudieron mostrar las opciones de correo.", false)
        }
    }
}

fun openAddGoogleAccountSettings(context: Context) {
    val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
