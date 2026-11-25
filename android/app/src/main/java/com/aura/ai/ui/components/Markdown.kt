package com.aura.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.ai.ui.theme.Aura
import kotlinx.coroutines.delay

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data object Rule : MdBlock
}

private val numbered = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val bulleted = Regex("""^(\s*)[-*•+]\s+(.*)$""")
private val heading = Regex("""^(#{1,6})\s+(.*)$""")

private fun parse(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                blocks += MdBlock.Code(lang, code.toString().trimEnd('\n'))
            }
            trimmed.isEmpty() -> flush()
            heading.matches(trimmed) -> {
                flush()
                val m = heading.find(trimmed)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            trimmed == "---" || trimmed == "***" -> { flush(); blocks += MdBlock.Rule }
            trimmed.startsWith(">") -> { flush(); blocks += MdBlock.Quote(trimmed.removePrefix(">").trim()) }
            numbered.matches(line) -> {
                flush()
                val m = numbered.find(line)!!
                blocks += MdBlock.Bullet("${m.groupValues[2]}.", m.groupValues[3], m.groupValues[1].length / 2)
            }
            bulleted.matches(line) -> {
                flush()
                val m = bulleted.find(line)!!
                blocks += MdBlock.Bullet("•", m.groupValues[2], m.groupValues[1].length / 2)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        i++
    }
    flush()
    return blocks
}

private val inlinePattern = Regex("""(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\*[^*\s][^*]*\*|_[^_\s][^_]*_)""")

fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlinePattern.findAll(text)) {
        append(text.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") || token.startsWith("__") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Aura.TextPrimary)) { append(token.substring(2, token.length - 2)) }
            token.startsWith("`") ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = 0.10f), color = Aura.VioletLight)) {
                    append(" ${token.substring(1, token.length - 1)} ")
                }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.substring(1, token.length - 1)) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parse(markdown) }
    val body = MaterialTheme.typography.bodyLarge
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (block in blocks) {
                when (block) {
                    is MdBlock.Heading -> Text(
                        inlineMarkdown(block.text),
                        style = body.copy(
                            fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp },
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    is MdBlock.Paragraph -> Text(inlineMarkdown(block.text), style = body)
                    is MdBlock.Bullet -> Row(Modifier.padding(start = (block.indent * 14).dp)) {
                        Text(block.marker, style = body.copy(color = Aura.Lilac, fontWeight = FontWeight.SemiBold), modifier = Modifier.width(24.dp))
                        Text(inlineMarkdown(block.text), style = body)
                    }
                    is MdBlock.Quote -> Row {
                        Spacer(
                            Modifier
                                .width(3.dp)
                                .size(width = 3.dp, height = 22.dp)
                                .background(Aura.Violet, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(inlineMarkdown(block.text), style = body.copy(color = Aura.TextSecondary, fontStyle = FontStyle.Italic))
                    }
                    is MdBlock.Code -> CodeBlock(block.language, block.code)
                    MdBlock.Rule -> Spacer(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .size(height = 1.dp, width = 1.dp)
                            .background(Color.White.copy(alpha = 0.12f)),
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0C12))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(language.ifBlank { "code" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { clipboard.setText(AnnotatedString(code)); copied = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, "Копировать", tint = Aura.TextSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (copied) "Скопировано" else "Копировать", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = Color(0xFFE4E1F5), fontSize = 13.sp, lineHeight = 19.sp),
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}
