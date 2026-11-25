package com.aura.ai.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aura.ai.auraApp
import com.aura.ai.data.ChatMode
import com.aura.ai.data.Message
import com.aura.ai.data.MessageStatus
import com.aura.ai.ui.Routes
import com.aura.ai.ui.components.AuraInlineField
import com.aura.ai.ui.components.AuraSpark
import com.aura.ai.ui.components.ConfirmDialog
import com.aura.ai.ui.components.ErrorBanner
import com.aura.ai.ui.components.GlassIconButton
import com.aura.ai.ui.components.Glow
import com.aura.ai.ui.components.InputDialog
import com.aura.ai.ui.components.MarkdownText
import com.aura.ai.ui.components.TopBar
import com.aura.ai.ui.components.TypingDots
import com.aura.ai.ui.components.formatSize
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass

private val suggestions = mapOf(
    ChatMode.CHAT to listOf(
        "Объясни простыми словами, как работает нейросеть",
        "Составь план тренировок на неделю",
        "Помоги написать вежливое письмо преподавателю",
    ),
    ChatMode.CODE to listOf(
        "Напиши функцию на Kotlin, которая проверяет палиндром",
        "Объясни, что такое async/await, с примером",
        "Как сделать REST API на Python за 10 минут?",
    ),
    ChatMode.IDEAS to listOf(
        "Идеи для мобильного приложения",
        "Чем заняться на выходных с друзьями",
        "Идеи подарка на день рождения",
    ),
)

@Composable
fun ChatScreen(nav: NavController, chatId: String, mode: String, draft: String) {
    val context = LocalContext.current
    val app = context.auraApp
    val repo = app.repository
    val vm: ChatViewModel = viewModel {
        ChatViewModel(repo, chatId.takeIf { it != "new" }, mode, if (draft == "attach") "" else draft)
    }
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var menu by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attach(context, uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attach(context, uri)
    }
    LaunchedEffect(Unit) {
        if (draft == "attach") attachMenu = true
    }
    // Keep the newest message in view.
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    fun buzz() {
        if (repo.session.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            title = vm.chat?.title ?: "Новый чат · ${modeTitle(vm.currentMode)}",
            onBack = { nav.popBackStack() },
        ) {
            if (vm.chat != null) {
                Box {
                    GlassIconButton(Icons.Rounded.MoreVert, "Меню", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Aura.GraphiteLight) {
                        DropdownMenuItem(
                            text = { Text("Переименовать") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить чат", color = Aura.Danger) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Aura.Danger) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Aura.Lilac)
                vm.messages.isEmpty() -> EmptyChat(vm.currentMode) { buzz(); vm.send(it) }
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val reversed = vm.messages.asReversed()
                    items(reversed, key = { it.id }) { message ->
                        val isLast = message.id == vm.messages.lastOrNull()?.id
                        MessageItem(
                            message = message,
                            isLast = isLast,
                            streaming = vm.streaming && isLast,
                            onCopy = { clipboard.setText(AnnotatedString(message.content)); buzz() },
                            onRetry = { buzz(); vm.retry() },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(vm.limitReached) {
            LimitBanner { nav.navigate(Routes.PRO) }
        }
        vm.error?.let { err ->
            ErrorBanner(
                err,
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                action = "Закрыть",
                onAction = { vm.error = null },
            )
        }

        // Composer
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(26.dp), strong = true)
                .animateContentSize()
                .padding(6.dp),
        ) {
            if (vm.pending.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.pending.forEach { file -> PendingChip(file) { vm.removePending(file.localId) } }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Box {
                    GlassIconButton(Icons.Rounded.AttachFile, "Прикрепить", { attachMenu = true })
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }, containerColor = Aura.GraphiteLight) {
                        DropdownMenuItem(
                            text = { Text("Фото") },
                            leadingIcon = { Icon(Icons.Rounded.Image, null) },
                            onClick = {
                                attachMenu = false
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Файл (PDF, текст, код)") },
                            leadingIcon = { Icon(Icons.Rounded.Description, null) },
                            onClick = {
                                attachMenu = false
                                pickFile.launch(arrayOf("application/pdf", "text/*", "application/json", "application/xml", "application/octet-stream", "image/*"))
                            },
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    AuraInlineField(vm.input, { vm.input = it }, placeholderFor(vm.currentMode), maxLines = 6)
                }
                if (vm.streaming) {
                    GlassIconButton(Icons.Rounded.Stop, "Остановить", { buzz(); vm.stop() }, filled = true)
                } else {
                    GlassIconButton(
                        Icons.AutoMirrored.Rounded.Send, "Отправить",
                        onClick = { buzz(); vm.send() },
                        filled = vm.canSend,
                        enabled = vm.canSend,
                    )
                }
            }
        }
    }

    if (renaming) {
        InputDialog("Название чата", vm.chat?.title.orEmpty(), "Название", "Сохранить", onConfirm = { vm.rename(it) }, onDismiss = { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(
            "Удалить чат?",
            "Чат удалится на всех ваших устройствах. Это действие нельзя отменить.",
            "Удалить",
            onConfirm = { vm.delete { nav.popBackStack() } },
            onDismiss = { deleting = false },
        )
    }
}

private fun placeholderFor(mode: String) = when (mode) {
    ChatMode.CODE -> "Опишите задачу или вставьте код…"
    ChatMode.IDEAS -> "Для чего нужны идеи?"
    else -> "Спросите что-нибудь…"
}

@Composable
private fun EmptyChat(mode: String, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Glow(Modifier.size(170.dp))
            Box(Modifier.size(88.dp).glass(RoundedCornerShape(28.dp), strong = true), contentAlignment = Alignment.Center) {
                AuraSpark(Modifier.size(46.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when (mode) {
                ChatMode.CODE -> "Напишу, объясню и исправлю код"
                ChatMode.IDEAS -> "Придумаем что-нибудь классное"
                else -> "Чем могу помочь?"
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        suggestions[mode].orEmpty().forEach { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(color = Aura.TextPrimary),
                modifier = Modifier
                    .padding(vertical = 5.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp))
                    .clickable { onPick(text) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun MessageItem(message: Message, isLast: Boolean, streaming: Boolean, onCopy: () -> Unit, onRetry: () -> Unit) {
    val api = LocalContext.current.auraApp.repository.api
    if (message.isUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (message.attachments.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    message.attachments.forEach { att ->
                        if (att.isImage) {
                            AsyncImage(
                                model = api.absoluteUrl(att.url),
                                contentDescription = att.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Aura.GlassStroke, RoundedCornerShape(16.dp)),
                            )
                        } else {
                            FileChip(att.name, formatSize(att.size))
                        }
                    }
                }
            }
            if (message.content.isNotBlank()) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                        .background(Brush.linearGradient(listOf(Aura.Violet.copy(alpha = 0.85f), Aura.VioletDeep.copy(alpha = 0.9f))))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
        return
    }

    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 6 }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(32.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                AuraSpark(Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .animateContentSize(),
                ) {
                    if (message.content.isEmpty() && message.status == MessageStatus.STREAMING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TypingDots()
                            Spacer(Modifier.width(10.dp))
                            Text("Aura думает…", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (message.content.isNotEmpty()) {
                        MarkdownText(message.content)
                    }
                    val note = when (message.status) {
                        MessageStatus.STOPPED -> "Генерация остановлена"
                        MessageStatus.ERROR -> "Ответ не получен"
                        MessageStatus.REFUSED -> "Aura не может ответить на этот запрос"
                        MessageStatus.TRUNCATED -> "Ответ получился слишком длинным и был обрезан"
                        else -> null
                    }
                    if (note != null) {
                        if (message.content.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        Text(note, style = MaterialTheme.typography.labelMedium, color = if (message.status == MessageStatus.ERROR) Aura.Danger else Aura.TextTertiary)
                    }
                }
                if (!streaming && message.status != MessageStatus.STREAMING) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (message.content.isNotEmpty()) SmallAction(Icons.Rounded.ContentCopy, "Копировать", onCopy)
                        if (isLast) SmallAction(Icons.Rounded.Refresh, "Повторить", onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Aura.TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FileChip(name: String, subtitle: String) {
    Row(
        Modifier.glass(RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp).widthIn(max = 200.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Description, null, tint = Aura.Lilac, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PendingChip(file: PendingFile, onRemove: () -> Unit) {
    Box {
        val bitmap = remember(file.preview) { file.preview?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        if (bitmap != null) {
            Image(
                bitmap, file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Aura.GlassStroke, RoundedCornerShape(14.dp)),
            )
        } else {
            FileChip(file.name, file.error ?: if (file.size > 0) formatSize(file.size) else "")
        }
        if (file.uploading) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
        if (file.error != null && bitmap != null) {
            Text("Ошибка", color = Aura.Danger, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Убрать", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}


@Composable
private fun LimitBanner(onUpgrade: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), strong = true)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WorkspacePremium, null, tint = Aura.Gold)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Дневной лимит исчерпан", style = MaterialTheme.typography.labelLarge)
            Text("С Aura Pro можно общаться почти без ограничений", style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "Pro",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Aura.PrimaryGradient).clickable(onClick = onUpgrade).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
