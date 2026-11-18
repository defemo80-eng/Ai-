package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NavyBorder
import kotlinx.coroutines.delay

private sealed interface MarkdownBlock {
    data class Prose(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
}

@Composable
fun MarkdownMessageContent(
    markdown: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }
                is MarkdownBlock.Prose -> {
                    ProseBlock(text = block.text, textColor = textColor)
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MidnightNavy,
        border = BorderStroke(1.dp, NavyBorder),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = DeepNavy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = language.ifBlank { "código" },
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanGlow
                        )
                    }
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        },
                        modifier = Modifier.testTag("copy_code_button")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copiar código",
                            tint = if (copied) CyanGlow else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (copied) "Copiado" else "Copiar",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (copied) CyanGlow else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text(
                        text = code,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFFE6F0FF)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProseBlock(
    text: String,
    textColor: Color,
) {
    val lines = remember(text) { text.lines() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest

    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            lines.forEach { rawLine ->
                val trimmed = rawLine.trimStart()
                when {
                    trimmed.isEmpty() -> {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    trimmed.startsWith("### ") -> {
                        Text(
                            text = parseInlineStyles(trimmed.removePrefix("### "), primaryColor, codeBg),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                    trimmed.startsWith("## ") -> {
                        Text(
                            text = parseInlineStyles(trimmed.removePrefix("## "), primaryColor, codeBg),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                    trimmed.startsWith("# ") -> {
                        Text(
                            text = parseInlineStyles(trimmed.removePrefix("# "), primaryColor, codeBg),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                    trimmed.startsWith("> ") -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = parseInlineStyles(trimmed.removePrefix("> "), primaryColor, codeBg),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = textColor.copy(alpha = 0.9f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = primaryColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = parseInlineStyles(trimmed.drop(2), primaryColor, codeBg),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = parseInlineStyles(rawLine, primaryColor, codeBg),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = input.lines()
    val proseBuffer = StringBuilder()
    val codeBuffer = StringBuilder()
    var inCode = false
    var currentLang = ""

    fun flushProse() {
        val text = proseBuffer.toString().trim('\n')
        if (text.isNotBlank()) {
            result.add(MarkdownBlock.Prose(text))
        }
        proseBuffer.clear()
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushProse()
                inCode = true
                currentLang = trimmed.removePrefix("```").trim()
                codeBuffer.clear()
            } else {
                result.add(
                    MarkdownBlock.Code(
                        language = currentLang,
                        code = codeBuffer.toString().trimEnd('\n')
                    )
                )
                inCode = false
                currentLang = ""
                codeBuffer.clear()
            }
        } else if (inCode) {
            codeBuffer.append(line).append('\n')
        } else {
            proseBuffer.append(line).append('\n')
        }
    }

    if (inCode && codeBuffer.isNotEmpty()) {
        // Still streaming an unclosed code block
        result.add(
            MarkdownBlock.Code(
                language = currentLang,
                code = codeBuffer.toString().trimEnd('\n')
            )
        )
    } else {
        flushProse()
    }

    return result
}

private fun parseInlineStyles(
    text: String,
    accentColor: Color,
    codeBg: Color,
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                background = codeBg,
                                color = accentColor,
                                fontSize = 13.5.sp
                            )
                        ) {
                            append(" ${text.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append('`')
                        i += 1
                    }
                }
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('*')
                        i += 1
                    }
                }
                else -> {
                    append(text[i])
                    i += 1
                }
            }
        }
    }
}
