package com.aura.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aura.ai.data.ChatMode
import com.aura.ai.ui.BottomBarSpace
import com.aura.ai.ui.Routes
import com.aura.ai.ui.components.GlassCard
import com.aura.ai.ui.theme.Aura

private data class Tool(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

private val tools = listOf(
    Tool("Чат с ИИ", "Любые вопросы", Icons.Rounded.ChatBubbleOutline, Routes.chat(mode = ChatMode.CHAT)),
    Tool("Изображения", "Генерация по описанию", Icons.Rounded.Image, Routes.IMAGES),
    Tool("Написать код", "Функции, скрипты, приложения", Icons.Rounded.Code, Routes.chat(mode = ChatMode.CODE)),
    Tool("Объяснить код", "Разбор по шагам", Icons.Rounded.Search, Routes.chat(mode = ChatMode.CODE, draft = "Объясни, что делает этот код:\n\n")),
    Tool("Найти ошибку", "Отладка и исправления", Icons.Rounded.BugReport, Routes.chat(mode = ChatMode.CODE, draft = "Найди и исправь ошибку в коде:\n\n")),
    Tool("Идеи", "Мозговой штурм", Icons.Rounded.Lightbulb, Routes.chat(mode = ChatMode.IDEAS)),
    Tool("Анализ файла", "PDF, текст, фото", Icons.Rounded.Description, Routes.chat(mode = ChatMode.CHAT, draft = "attach")),
    Tool("Перевод", "На любой язык", Icons.Rounded.Translate, Routes.chat(mode = ChatMode.CHAT, draft = "Переведи на английский:\n\n")),
    Tool("Пересказ", "Кратко о главном", Icons.Rounded.Summarize, Routes.chat(mode = ChatMode.CHAT, draft = "Сделай краткий пересказ текста:\n\n")),
    Tool("Письмо", "Деловое или личное", Icons.Rounded.Mail, Routes.chat(mode = ChatMode.CHAT, draft = "Помоги написать письмо: ")),
    Tool("Улучшить текст", "Стиль и грамотность", Icons.Rounded.EditNote, Routes.chat(mode = ChatMode.CHAT, draft = "Исправь ошибки и улучши стиль текста:\n\n")),
)

@Composable
fun ToolsScreen(nav: NavController) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = BottomBarSpace + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text("Инструменты", style = MaterialTheme.typography.headlineMedium)
                    Text("Выберите, с чем помочь", style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(tools) { tool ->
                GlassCard(Modifier.height(132.dp), onClick = { nav.navigate(tool.route) }) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Aura.Violet.copy(alpha = 0.4f), Aura.VioletDeep.copy(alpha = 0.15f)))),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Icon(tool.icon, null, tint = Aura.Lilac, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(tool.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(tool.subtitle, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}
