package com.aura.ai.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.ai.auraApp
import com.aura.ai.data.Chat
import com.aura.ai.data.ChatMode
import com.aura.ai.data.Draft
import com.aura.ai.ui.BottomBarSpace
import com.aura.ai.ui.Routes
import com.aura.ai.ui.components.AuraInlineField
import com.aura.ai.ui.components.AuraLogoTile
import com.aura.ai.ui.components.AuraSpark
import com.aura.ai.ui.components.Avatar
import com.aura.ai.ui.components.GlassCard
import com.aura.ai.ui.components.GlassIconButton
import com.aura.ai.ui.components.Glow
import com.aura.ai.ui.components.ProChip
import com.aura.ai.ui.components.SectionTitle
import com.aura.ai.ui.components.relativeTime
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass

@Composable
fun HomeScreen(nav: NavController) {
    val repo = LocalContext.current.auraApp.repository
    val account by repo.account.collectAsStateWithLifecycle()
    val chats by repo.chats.collectAsStateWithLifecycle()
    val user = account?.user
    var input by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuraLogoTile(68.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Aura", style = MaterialTheme.typography.displaySmall)
                    Text("Твой ИИ-ассистент", style = MaterialTheme.typography.bodyMedium)
                }
                ProChip(isPro = user?.isPro == true) { nav.navigate(Routes.PRO) }
                Spacer(Modifier.width(10.dp))
                Avatar(user?.name ?: "A", 46.dp, pro = user?.isPro == true) { nav.navigate(Routes.PROFILE) }
            }
            Spacer(Modifier.height(22.dp))

            HeroCard(name = user?.name?.substringBefore(' ')) { nav.navigate(Routes.chat(mode = ChatMode.CHAT)) }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard("Чат", "Общайся с ИИ", Icons.Rounded.ChatBubbleOutline, Modifier.weight(1f)) {
                    nav.navigate(Routes.chat(mode = ChatMode.CHAT))
                }
                FeatureCard("Изображения", "Создавай картинки", Icons.Rounded.Image, Modifier.weight(1f)) {
                    nav.navigate(Routes.IMAGES)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard("Код", "Пиши и разбирай", Icons.Rounded.Code, Modifier.weight(1f)) {
                    nav.navigate(Routes.chat(mode = ChatMode.CODE))
                }
                FeatureCard("Идеи", "Получай идеи", Icons.Rounded.Lightbulb, Modifier.weight(1f)) {
                    nav.navigate(Routes.chat(mode = ChatMode.IDEAS))
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionTitle("Недавние чаты") {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { nav.navigate(Routes.HISTORY) }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Все", color = Aura.Lilac, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Rounded.ChevronRight, null, tint = Aura.Lilac)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (chats.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Здесь появятся ваши разговоры", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    chats.take(3).forEach { chat ->
                        ChatRow(chat) { nav.navigate(Routes.chat(chat.id, chat.mode)) }
                    }
                }
            }
            Spacer(Modifier.height(BottomBarSpace + 90.dp))
        }

        // Quick composer above the tab bar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = BottomBarSpace - 8.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), strong = true)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Rounded.AttachFile, "Прикрепить файл", onClick = {
                repo.pendingDraft = Draft(input, autoSend = false)
                input = ""
                nav.navigate(Routes.chat(mode = ChatMode.CHAT, draft = "attach"))
            })
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                AuraInlineField(input, { input = it }, "Написать сообщение…")
            }
            GlassIconButton(
                Icons.AutoMirrored.Rounded.Send, "Отправить",
                filled = true,
                enabled = input.isNotBlank(),
                onClick = {
                    repo.pendingDraft = Draft(input.trim())
                    input = ""
                    nav.navigate(Routes.chat(mode = ChatMode.CHAT))
                },
            )
        }
    }
}

@Composable
private fun HeroCard(name: String?, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "hero")
    val float by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "float")
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(Aura.RadiusL))
            .background(Aura.HeroGradient)
            .border(1.dp, Aura.GlassStroke, RoundedCornerShape(Aura.RadiusL))
            .clickable(onClick = onClick),
    ) {
        Glow(Modifier.size(260.dp).align(Alignment.CenterEnd).graphicsLayer { translationX = 120f }, Aura.Violet)
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 22.dp)
                .graphicsLayer {
                    translationY = -12f * float
                    rotationZ = -8f + 4f * float
                }
                .size(112.dp)
                .glass(RoundedCornerShape(34.dp), strong = true),
            contentAlignment = Alignment.Center,
        ) {
            AuraSpark(Modifier.size(58.dp))
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 150.dp)) {
            Text(if (name.isNullOrBlank()) "Привет!" else "Привет, $name!", style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text("Чем я могу помочь тебе сегодня?", style = MaterialTheme.typography.bodyLarge.copy(color = Aura.VioletLight))
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Aura.PrimaryGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Новый чат", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.height(86.dp), onClick = onClick, contentPadding = 14.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Aura.Violet.copy(alpha = 0.35f), Aura.VioletDeep.copy(alpha = 0.15f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Aura.Lilac, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ChatRow(chat: Chat, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).glass(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(modeIcon(chat.mode), null, tint = Aura.Lilac, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    relativeTime(chat.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Aura.TextTertiary)
        }
    }
}

fun modeIcon(mode: String): ImageVector = when (mode) {
    ChatMode.CODE -> Icons.Rounded.Code
    ChatMode.IDEAS -> Icons.Rounded.Lightbulb
    else -> Icons.Rounded.ChatBubbleOutline
}

fun modeTitle(mode: String): String = when (mode) {
    ChatMode.CODE -> "Код"
    ChatMode.IDEAS -> "Идеи"
    else -> "Чат"
}
