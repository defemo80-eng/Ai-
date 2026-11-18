package com.example.data.ai

import com.example.data.model.ChatDepModel
import com.example.data.model.ChatMessage
import com.example.data.model.PendingAttachment
import com.example.data.model.ResponseStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.util.concurrent.ConcurrentHashMap

class ChatDepAiService {

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private fun getModel(model: ChatDepModel, style: ResponseStyle): GenerativeModel {
        val cacheKey = "${model.modelId}:${style.key}"
        return modelCache.getOrPut(cacheKey) {
            Firebase.ai.generativeModel(
                modelName = model.modelId,
                generationConfig = generationConfig {
                    temperature = when (style) {
                        ResponseStyle.CODING -> 0.4f
                        ResponseStyle.CONCISE -> 0.5f
                        ResponseStyle.TUTOR -> 0.65f
                        ResponseStyle.BALANCED -> 0.7f
                    }
                },
                systemInstruction = content {
                    text(buildSystemInstruction(style))
                }
            )
        }
    }

    private fun buildSystemInstruction(style: ResponseStyle): String {
        val styleGuidance = when (style) {
            ResponseStyle.BALANCED ->
                "Mantén un equilibrio natural entre claridad, profundidad y concisión. Estructura tus respuestas con títulos, listas y bloques de código cuando mejoren la lectura."
            ResponseStyle.CODING ->
                "Prioriza soluciones de ingeniería sólidas: entrega código completo, organizado por archivos cuando aplique, interfaces modernas, manejo de errores, y explicaciones paso a paso. Señala posibles bugs y cómo probarlos."
            ResponseStyle.TUTOR ->
                "Actúa como un tutor paciente y brillante: explica conceptos difíciles de manera sencilla con analogías y ejemplos, resuelve operaciones matemáticas paso a paso verificando cada cálculo y guía el aprendizaje escolar."
            ResponseStyle.CONCISE ->
                "Sé sumamente directo y claro: ve al grano con viñetas breves o el código exacto sin rodeos innecesarios, manteniendo total precisión."
        }

        return """
            IDENTIDAD:
            Tu nombre es ChatDep. Eres una inteligencia artificial avanzada, moderna y conversacional ("Tu IA Inteligente Siempre Contigo"), diseñada para ayudar en español de forma rápida, amable, precisa y útil.

            PERSONALIDAD Y REGLAS ESTRICTAS:
            - Habla español de forma natural y cercana (aunque puedes traducir o responder en cualquier idioma cuando el usuario lo solicite).
            - Comprende preguntas simples y complejas, recuerda el contexto de toda la conversación y adapta tus respuestas a lo que la persona necesita.
            - Explica temas difíciles de forma sencilla y organizada (usa encabezados Markdown, listas claras y bloques de código con lenguaje etiquetado como ```kotlin, ```python, ```js, etc.).
            - NO inventes información ni datos falsos. Si no sabes algo o no tienes acceso a información en tiempo real de internet, dilo con honestidad y claridad.
            - Si cometes un error o el usuario te corrige, reconócelo con naturalidad y corrígelo de inmediato.

            CAPACIDADES PRINCIPALES:
            1. Responder preguntas generales, técnicas, científicas y creativas.
            2. Resolver operaciones matemáticas y problemas lógicos paso a paso.
            3. Ayudar con tareas escolares y universitarias.
            4. Redactar cartas, textos, guiones, correos, documentos y proyectos.
            5. Programar aplicaciones y videojuegos en cualquier lenguaje, generando código funcional y bien estructurado, corrigiendo errores y explicando paso a paso.
            6. Asesorar en negocios, emprendimientos, planes de acción y estrategias.
            7. Analizar imágenes proporcionadas por el usuario y resumir o analizar documentos adjuntos.
            8. Traducir entre idiomas conservando el tono y sentido natural.
            9. Organizar proyectos grandes por etapas claras sin perder el contexto previo.

            ESTILO ACTIVO:
            $styleGuidance
        """.trimIndent()
    }

    fun streamReply(
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?,
        model: ChatDepModel,
        style: ResponseStyle,
    ): Flow<String> {
        val generativeModel = getModel(model, style)
        val chatHistory: List<Content> = history
            .takeLast(24)
            .filter { it.content.isNotBlank() }
            .map { msg ->
                val role = if (msg.role == "model") "model" else "user"
                val textBody = if (msg.hasAttachment && !msg.attachmentLabel.isNullOrBlank()) {
                    "[Adjunto previo: ${msg.attachmentLabel}]\n${msg.content}"
                } else {
                    msg.content
                }
                content(role) { text(textBody) }
            }

        val chat = generativeModel.startChat(history = chatHistory)
        val outgoingContent = content("user") {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> {
                    image(attachment.bitmap)
                    val promptText = userPrompt.ifBlank {
                        "Analiza esta imagen en detalle y explícame lo más importante que observas."
                    }
                    text(promptText)
                }
                is PendingAttachment.TextDocumentAttachment -> {
                    val promptText = userPrompt.ifBlank {
                        "Resume y analiza el contenido de este documento de manera clara y organizada."
                    }
                    text(
                        "Documento adjunto (${attachment.label}):\n\n" +
                            attachment.extractedText +
                            "\n\nSolicitud del usuario:\n$promptText"
                    )
                }
                is PendingAttachment.BinaryDocumentAttachment -> {
                    inlineData(attachment.bytes, attachment.mimeType)
                    val promptText = userPrompt.ifBlank {
                        "Resume y analiza este documento adjunto (${attachment.label}) de forma clara y organizada."
                    }
                    text(promptText)
                }
                null -> {
                    text(userPrompt)
                }
            }
        }

        return chat.sendMessageStream(outgoingContent)
            .mapNotNull { chunk -> chunk.text }
    }

    suspend fun suggestTitle(firstPrompt: String, attachmentLabel: String?): String {
        val fallback = firstPrompt.trim()
            .replace("\n", " ")
            .ifBlank { attachmentLabel ?: "Nueva conversación" }
            .take(44)
        return try {
            val liteModel = getModel(ChatDepModel.TURBO_LITE, ResponseStyle.CONCISE)
            val response = liteModel.generateContent(
                "Genera un título muy breve en español (máximo 5 palabras, sin comillas ni punto final) que resuma este mensaje: \"$fallback\""
            )
            val generated = response.text?.trim()?.removeSurrounding("\"")?.take(60)
            if (!generated.isNullOrBlank()) generated else fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
