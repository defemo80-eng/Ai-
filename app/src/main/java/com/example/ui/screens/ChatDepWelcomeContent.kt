package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue

private data class StarterPrompt(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val action: StarterAction,
)

private sealed interface StarterAction {
    data class SendPrompt(val prompt: String) : StarterAction
    data object PickImage : StarterAction
    data object PickDocument : StarterAction
}

@Composable
fun ChatDepWelcomeContent(
    userName: String?,
    isWideScreen: Boolean,
    onSendPrompt: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstName = userName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }

    val starters = listOf(
        StarterPrompt(
            id = "code",
            title = "Programar app o juego",
            subtitle = "Crea código organizado, interfaces y corrige errores paso a paso",
            icon = Icons.Rounded.Code,
            action = StarterAction.SendPrompt(
                "Quiero crear un juego o aplicación moderna paso a paso. Ayúdame a elegir la arquitectura, estructura de archivos y código inicial."
            )
        ),
        StarterPrompt(
            id = "math",
            title = "Matemáticas y estudio",
            subtitle = "Resuelve operaciones y explica temas escolares con claridad",
            icon = Icons.Rounded.Calculate,
            action = StarterAction.SendPrompt(
                "Explícame paso a paso cómo resolver ecuaciones cuadráticas y dame 3 ejercicios resueltos para practicar."
            )
        ),
        StarterPrompt(
            id = "image",
            title = "Analizar una imagen",
            subtitle = "Sube una foto, diagrama o captura para interpretarla",
            icon = Icons.Rounded.ImageSearch,
            action = StarterAction.PickImage
        ),
        StarterPrompt(
            id = "doc",
            title = "Resumir un documento",
            subtitle = "Adjunta PDF, código, Markdown o texto para analizarlo",
            icon = Icons.Rounded.Description,
            action = StarterAction.PickDocument
        ),
        StarterPrompt(
            id = "business",
            title = "Negocios y emprendimiento",
            subtitle = "Ideas originales, plan de acción y organización de proyectos",
            icon = Icons.Rounded.BusinessCenter,
            action = StarterAction.SendPrompt(
                "Ayúdame a estructurar un plan de negocio por etapas para lanzar un emprendimiento digital rentable con bajo presupuesto."
            )
        ),
        StarterPrompt(
            id = "translate",
            title = "Redactar y traducir",
            subtitle = "Cartas, proyectos, documentos o traducciones naturales",
            icon = Icons.Rounded.Translate,
            action = StarterAction.SendPrompt(
                "Ayúdame a redactar una propuesta profesional clara y persuasiva para presentar un proyecto."
            )
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CyanGlow.copy(alpha = 0.42f),
                                    ElectricBlue.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension * 0.65f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.5.dp, CyanGlow.copy(alpha = 0.65f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(112.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.chatdep_logo),
                        contentDescription = stringResource(id = R.string.cd_chatdep_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (firstName != null) "¡Hola, $firstName! Soy ChatDep" else "¡Hola! Soy ChatDep",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "¿En qué proyecto, tarea o duda te ayudo hoy?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            val columns = if (isWideScreen) 2 else 2
            val rows = starters.chunked(columns)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rows.forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { starter ->
                            StarterCard(
                                starter = starter,
                                onClick = {
                                    when (val action = starter.action) {
                                        is StarterAction.SendPrompt -> onSendPrompt(action.prompt)
                                        StarterAction.PickImage -> onPickImage()
                                        StarterAction.PickDocument -> onPickDocument()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarterCard(
    starter: StarterPrompt,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("starter_card_${starter.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = starter.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = starter.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = starter.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
