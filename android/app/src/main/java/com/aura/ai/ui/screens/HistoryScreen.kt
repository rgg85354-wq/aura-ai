package com.aura.ai.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.ai.auraApp
import com.aura.ai.data.ApiException
import com.aura.ai.data.Chat
import com.aura.ai.ui.BottomBarSpace
import com.aura.ai.ui.Routes
import com.aura.ai.ui.components.AuraTextField
import com.aura.ai.ui.components.ConfirmDialog
import com.aura.ai.ui.components.EmptyState
import com.aura.ai.ui.components.ErrorBanner
import com.aura.ai.ui.components.GlassIconButton
import com.aura.ai.ui.components.InputDialog
import com.aura.ai.ui.components.dayGroup
import com.aura.ai.ui.components.relativeTime
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(nav: NavController) {
    val repo = LocalContext.current.auraApp.repository
    val chats by repo.chats.collectAsStateWithLifecycle()
    val syncing by repo.syncing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Chat>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    var deleting by remember { mutableStateOf<Chat?>(null) }

    // Search on the server (titles and message text) with a short debounce.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(350)
        results = try {
            repo.searchChats(query.trim())
        } catch (e: ApiException) {
            chats.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    val shown = results ?: chats

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("История", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.Add, "Новый чат", { nav.navigate(Routes.chat()) }, filled = true)
        }
        AuraTextField(
            query, { query = it }, "Поиск по чатам",
            leading = Icons.Rounded.Search,
            modifier = Modifier.padding(horizontal = 20.dp),
            trailing = if (query.isNotEmpty()) {
                { Icon(Icons.Rounded.Close, "Очистить", tint = Aura.TextSecondary, modifier = Modifier.size(20.dp).combinedClickable { query = "" }) }
            } else null,
        )
        error?.let { ErrorBanner(it, Modifier.padding(20.dp, 10.dp), "OK") { error = null } }

        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { scope.launch { try { repo.sync() } catch (e: ApiException) { error = e.message } } },
            modifier = Modifier.weight(1f),
        ) {
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Rounded.History,
                        if (query.isBlank()) "Пока нет чатов" else "Ничего не найдено",
                        if (query.isBlank()) "Начните разговор, и он появится здесь на всех ваших устройствах" else "Попробуйте другой запрос",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = BottomBarSpace + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val groups = shown.groupBy { dayGroup(it.updatedAt) }
                    groups.forEach { (group, items) ->
                        item(key = "h-$group") {
                            Text(group, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                        }
                        items(items, key = { it.id }) { chat ->
                            Box {
                                HistoryRow(
                                    chat,
                                    onClick = { nav.navigate(Routes.chat(chat.id, chat.mode)) },
                                    onLongClick = { menuFor = chat.id },
                                )
                                DropdownMenu(expanded = menuFor == chat.id, onDismissRequest = { menuFor = null }, containerColor = Aura.GraphiteLight) {
                                    DropdownMenuItem(
                                        text = { Text("Переименовать") },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                        onClick = { menuFor = null; renaming = chat },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить", color = Aura.Danger) },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Aura.Danger) },
                                        onClick = { menuFor = null; deleting = chat },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { chat ->
        InputDialog("Название чата", chat.title, "Название", "Сохранить", onConfirm = { title ->
            scope.launch { try { repo.renameChat(chat.id, title) } catch (e: ApiException) { error = e.message } }
        }, onDismiss = { renaming = null })
    }
    deleting?.let { chat ->
        ConfirmDialog(
            "Удалить «${chat.title}»?",
            "Чат удалится на всех ваших устройствах.",
            "Удалить",
            onConfirm = {
                scope.launch {
                    try {
                        repo.deleteChat(chat.id)
                        results = results?.filter { it.id != chat.id }
                    } catch (e: ApiException) {
                        error = e.message
                    }
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(chat: Chat, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(Aura.RadiusM))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).glass(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(modeIcon(chat.mode), null, tint = Aura.Lilac, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(chat.updatedAt), style = MaterialTheme.typography.labelMedium)
            }
            if (chat.preview.isNotBlank()) {
                Text(chat.preview.replace('\n', ' '), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
