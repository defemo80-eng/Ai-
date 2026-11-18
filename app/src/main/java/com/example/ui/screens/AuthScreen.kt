package com.example.ui.screens

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onChooseGoogleEmailClicked
import com.example.auth.onGoogleSignInClicked
import com.example.auth.openAddGoogleAccountSettings
import com.example.auth.startPhoneNumberVerification
import com.example.auth.verifyPhoneSmsCode
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedBlueGray
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.google.firebase.auth.PhoneAuthProvider

private enum class AuthMethod {
    GOOGLE,
    PHONE
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    skipAutoSignIn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var selectedMethod by rememberSaveable { mutableStateOf(AuthMethod.GOOGLE) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingSession by remember { mutableStateOf(!skipAutoSignIn) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var needsGoogleAccount by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Phone auth states
    var countryCode by rememberSaveable { mutableStateOf("+52") }
    var phoneDigits by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    LaunchedEffect(skipAutoSignIn) {
        if (skipAutoSignIn) {
            isCheckingSession = false
            return@LaunchedEffect
        }
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSession = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSession = false
            },
            scope = scope
        )
    }

    val pulseTransition = rememberInfiniteTransition(label = "logoGlow")
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MidnightNavy, DeepNavy, Color(0xFF061534))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CyanGlow.copy(alpha = glowAlpha),
                                    ElectricBlue.copy(alpha = glowAlpha * 0.45f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension * 0.62f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(2.dp, CyanGlow.copy(alpha = 0.65f)),
                    color = MidnightNavy,
                    shadowElevation = 18.dp,
                    modifier = Modifier.size(152.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.chatdep_logo),
                        contentDescription = stringResource(id = R.string.cd_chatdep_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(26.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ChatDep AI",
                style = MaterialTheme.typography.displaySmall,
                color = CrispWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(id = R.string.chatdep_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = CyanGlow,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Regístrate o inicia sesión con tu correo de Google o con tu número de teléfono para guardar tu historial.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedBlueGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CapabilityPill(Icons.Rounded.Code, "Código completo")
                CapabilityPill(Icons.Rounded.ImageSearch, "Análisis visual")
                CapabilityPill(Icons.Rounded.School, "Estudio y mates")
                CapabilityPill(Icons.Rounded.Description, "Resumen de docs")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Method Switcher: Google Email vs Phone Number
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AuthMethodTab(
                        label = "Correo Google",
                        icon = Icons.Rounded.AlternateEmail,
                        selected = selectedMethod == AuthMethod.GOOGLE,
                        onClick = {
                            selectedMethod = AuthMethod.GOOGLE
                            errorMessage = null
                            infoMessage = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_auth_google")
                    )
                    AuthMethodTab(
                        label = stringResource(id = R.string.register_with_phone),
                        icon = Icons.Rounded.Phone,
                        selected = selectedMethod == AuthMethod.PHONE,
                        onClick = {
                            selectedMethod = AuthMethod.PHONE
                            errorMessage = null
                            infoMessage = null
                            needsGoogleAccount = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_auth_phone")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = CrispWhite
                        )
                        if (needsGoogleAccount) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { openAddGoogleAccountSettings(context) },
                                modifier = Modifier.testTag("add_google_account_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAdd,
                                    contentDescription = null,
                                    tint = CyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.add_google_account),
                                    color = CyanGlow,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = infoMessage != null && errorMessage == null) {
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        text = infoMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CrispWhite,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (selectedMethod == AuthMethod.GOOGLE) {
                GoogleAuthSection(
                    isLoading = isLoading,
                    isCheckingSession = isCheckingSession,
                    onSignInClick = {
                        errorMessage = null
                        infoMessage = null
                        needsGoogleAccount = false
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg, missingAccount ->
                                isLoading = false
                                needsGoogleAccount = missingAccount
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                                infoMessage = "Selecciona un correo de Google en el cuadro emergente para continuar."
                            }
                        )
                    },
                    onChooseEmailClick = {
                        errorMessage = null
                        infoMessage = null
                        needsGoogleAccount = false
                        isLoading = true
                        onChooseGoogleEmailClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg, missingAccount ->
                                isLoading = false
                                needsGoogleAccount = missingAccount
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                                infoMessage = "Puedes elegir otro correo o agregar uno nuevo en tu dispositivo."
                            }
                        )
                    },
                    onAddAccountClick = { openAddGoogleAccountSettings(context) }
                )
            } else {
                PhoneAuthSection(
                    countryCode = countryCode,
                    phoneDigits = phoneDigits,
                    smsCode = smsCode,
                    verificationId = verificationId,
                    isLoading = isLoading,
                    onCountryCodeChange = { countryCode = it },
                    onPhoneDigitsChange = { phoneDigits = it.filter { ch -> ch.isDigit() } },
                    onSmsCodeChange = { smsCode = it.filter { ch -> ch.isDigit() }.take(6) },
                    onSendCode = {
                        errorMessage = null
                        infoMessage = null
                        isLoading = true
                        val fullNumber = if (phoneDigits.startsWith("+")) {
                            phoneDigits
                        } else {
                            "${countryCode.trim()}${phoneDigits.trim()}"
                        }
                        startPhoneNumberVerification(
                            context = context,
                            phoneNumberE164 = fullNumber,
                            resendToken = resendToken,
                            scope = scope,
                            onCodeSent = { verId, token ->
                                isLoading = false
                                verificationId = verId
                                resendToken = token
                                infoMessage = "Código SMS enviado a $fullNumber"
                            },
                            onAutoVerifiedSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onVerificationError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            }
                        )
                    },
                    onVerifyCode = {
                        val verId = verificationId ?: return@PhoneAuthSection
                        errorMessage = null
                        infoMessage = null
                        isLoading = true
                        verifyPhoneSmsCode(
                            verificationId = verId,
                            smsCode = smsCode,
                            scope = scope,
                            onSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            }
                        )
                    },
                    onChangeNumber = {
                        verificationId = null
                        smsCode = ""
                        errorMessage = null
                        infoMessage = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tu historial se sincroniza de forma privada en tu cuenta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedBlueGray
                )
            }
        }
    }
}

@Composable
private fun AuthMethodTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ElectricBlueBright else Color.Transparent,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) CrispWhite else MutedBlueGray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) CrispWhite else MutedBlueGray
            )
        }
    }
}

@Composable
private fun GoogleAuthSection(
    isLoading: Boolean,
    isCheckingSession: Boolean,
    onSignInClick: () -> Unit,
    onChooseEmailClick: () -> Unit,
    onAddAccountClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onSignInClick,
            enabled = !isLoading && !isCheckingSession,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricBlueBright,
                contentColor = CrispWhite,
                disabledContainerColor = NavySurface,
                disabledContentColor = MutedBlueGray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("google_sign_in_button")
        ) {
            if (isLoading || isCheckingSession) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = CrispWhite,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isCheckingSession) "Verificando sesión..." else "Mostrando correos...",
                    style = MaterialTheme.typography.titleSmall
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(id = R.string.sign_in_with_google),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onChooseEmailClick,
            enabled = !isLoading && !isCheckingSession,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanGlow),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("choose_google_email_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.AlternateEmail,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.choose_google_email),
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        TextButton(
            onClick = onAddAccountClick,
            modifier = Modifier.testTag("register_new_google_email_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.PersonAdd,
                contentDescription = null,
                tint = MutedBlueGray,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Agregar otro correo en el teléfono",
                style = MaterialTheme.typography.labelMedium,
                color = MutedBlueGray
            )
        }
    }
}

@Composable
private fun PhoneAuthSection(
    countryCode: String,
    phoneDigits: String,
    smsCode: String,
    verificationId: String?,
    isLoading: Boolean,
    onCountryCodeChange: (String) -> Unit,
    onPhoneDigitsChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onVerifyCode: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    val quickPrefixes = listOf("+52", "+1", "+34", "+57", "+54", "+51", "+56")

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = NavySurface,
        border = BorderStroke(1.dp, NavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (verificationId == null) {
                Text(
                    text = "Código de país y número móvil",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrispWhite
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrefixes.forEach { prefix ->
                        FilterChip(
                            selected = countryCode == prefix,
                            onClick = { onCountryCodeChange(prefix) },
                            label = { Text(prefix, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlueBright.copy(alpha = 0.28f),
                                selectedLabelColor = CyanGlow,
                                labelColor = MutedBlueGray
                            )
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { value ->
                            if (value.length <= 5) onCountryCodeChange(value)
                        },
                        singleLine = true,
                        label = { Text("Prefijo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite
                        ),
                        modifier = Modifier.width(96.dp)
                    )

                    OutlinedTextField(
                        value = phoneDigits,
                        onValueChange = onPhoneDigitsChange,
                        singleLine = true,
                        label = { Text("Número de teléfono") },
                        placeholder = { Text("5512345678") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("phone_number_input")
                    )
                }

                Button(
                    onClick = onSendCode,
                    enabled = !isLoading && phoneDigits.length >= 7,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlueBright,
                        contentColor = CrispWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("send_sms_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CrispWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Enviando SMS...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sms,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.send_sms_code),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            } else {
                Text(
                    text = "Ingresa el código de 6 dígitos enviado por SMS",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrispWhite
                )

                OutlinedTextField(
                    value = smsCode,
                    onValueChange = onSmsCodeChange,
                    singleLine = true,
                    label = { Text("Código SMS") },
                    placeholder = { Text("123456") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = NavyBorder,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sms_code_input")
                )

                Button(
                    onClick = onVerifyCode,
                    enabled = !isLoading && smsCode.length == 6,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlueBright,
                        contentColor = CrispWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("verify_sms_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CrispWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Verificando...")
                    } else {
                        Text(
                            text = stringResource(id = R.string.verify_sms_code),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onChangeNumber, enabled = !isLoading) {
                        Text("Cambiar número", color = MutedBlueGray)
                    }
                    TextButton(onClick = onSendCode, enabled = !isLoading) {
                        Text("Reenviar código", color = CyanGlow)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityPill(
    icon: ImageVector,
    label: String,
) {
    Surface(
        shape = CircleShape,
        color = NavySurface,
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanGlow,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = CrispWhite
            )
        }
    }
}
