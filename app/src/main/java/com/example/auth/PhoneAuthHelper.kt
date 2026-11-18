package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

private const val TAG = "ChatDepPhoneAuth"

fun startPhoneNumberVerification(
    context: Context,
    phoneNumberE164: String,
    resendToken: PhoneAuthProvider.ForceResendingToken?,
    scope: CoroutineScope,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
    onAutoVerifiedSuccess: () -> Unit,
    onVerificationError: (String) -> Unit,
) {
    val activity = context as? Activity
    if (activity == null) {
        onVerificationError("No se pudo iniciar la verificación desde esta pantalla.")
        return
    }

    val cleaned = phoneNumberE164.trim().replace(" ", "").replace("-", "")
    if (!cleaned.startsWith("+") || cleaned.length < 8 || !cleaned.drop(1).all { it.isDigit() }) {
        onVerificationError("Ingresa un número válido con código de país (ej. +525512345678).")
        return
    }

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            scope.launch {
                try {
                    Firebase.auth.signInWithCredential(credential).await()
                    onAutoVerifiedSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Auto phone sign-in failed", e)
                    onVerificationError(mapPhoneAuthError(e))
                }
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e(TAG, "Phone verification failed", e)
            onVerificationError(mapPhoneAuthError(e))
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken,
        ) {
            Log.d(TAG, "SMS code sent successfully")
            onCodeSent(verificationId, token)
        }
    }

    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(cleaned)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)

    if (resendToken != null) {
        builder.setForceResendingToken(resendToken)
    }

    PhoneAuthProvider.verifyPhoneNumber(builder.build())
}

fun verifyPhoneSmsCode(
    verificationId: String,
    smsCode: String,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val cleanCode = smsCode.trim()
    if (cleanCode.length < 6) {
        onError("Ingresa el código de 6 dígitos enviado por SMS.")
        return
    }

    scope.launch {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, cleanCode)
            Firebase.auth.signInWithCredential(credential).await()
            onSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SMS code verification failed", e)
            onError(mapPhoneAuthError(e))
        }
    }
}

private fun mapPhoneAuthError(e: Exception): String {
    return when {
        e is FirebaseAuthInvalidCredentialsException ->
            "El número o el código SMS es incorrecto. Verifica e intenta nuevamente."
        e is FirebaseTooManyRequestsException ->
            "Demasiados intentos en poco tiempo. Espera unos minutos antes de reintentar."
        e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "El acceso por teléfono no está habilitado aún en Firebase Console (Authentication > Sign-in method > Teléfono)."
        e.message?.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) == true ->
            "Debes activar el proveedor 'Teléfono' en Firebase Console (Authentication > Sign-in method) para enviar SMS."
        else ->
            e.localizedMessage ?: "No se pudo verificar el número de teléfono."
    }
}
