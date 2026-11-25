package com.aura.ai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.ai.BuildConfig
import com.aura.ai.auraApp
import com.aura.ai.data.ApiException
import com.aura.ai.ui.BottomBarSpace
import com.aura.ai.ui.Routes
import com.aura.ai.ui.components.AuraTextField
import com.aura.ai.ui.components.Avatar
import com.aura.ai.ui.components.ConfirmDialog
import com.aura.ai.ui.components.ErrorBanner
import com.aura.ai.ui.components.relativeTime
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass
import kotlinx.coroutines.launch

val responseStyles = listOf("concise" to "Кратко", "balanced" to "Баланс", "detailed" to "Подробно")
val responseLanguages = listOf(
    "auto" to "Как в вопросе", "ru" to "Русский", "en" to "English", "uk" to "Українська",
    "kk" to "Қазақша", "de" to "Deutsch", "es" to "Español", "fr" to "Français",
)

@Composable
fun SettingsScreen(nav: NavController) {
    val app = LocalContext.current.auraApp
    val repo = app.repository
    val account by repo.account.collectAsStateWithLifecycle()
    val syncing by repo.syncing.collectAsStateWithLifecycle()
    val lastSync by repo.lastSync.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user = account?.user

    var haptics by remember { mutableStateOf(repo.session.hapticsEnabled) }
    var languageMenu by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save(style: String? = null, language: String? = null) = scope.launch {
        try {
            repo.updateProfile(responseStyle = style, language = language)
        } catch (e: ApiException) {
            error = e.message
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))

        // Profile card
        Row(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(Aura.RadiusM))
                .clickable { nav.navigate(Routes.PROFILE) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user?.name ?: "A", 54.dp, pro = user?.isPro == true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(user?.name ?: "—", style = MaterialTheme.typography.titleMedium)
                Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Aura.TextTertiary)
        }
        Spacer(Modifier.height(12.dp))

        // Pro banner
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Aura.RadiusM))
                .background(Aura.HeroGradient)
                .clickable { nav.navigate(Routes.PRO) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WorkspacePremium, null, tint = Aura.Gold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (user?.isPro == true) "Aura Pro активен" else "Aura Pro", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (user?.isPro == true) "Спасибо за поддержку!" else "Больше сообщений, изображений и глубины",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Aura.VioletLight),
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Aura.VioletLight)
        }

        error?.let { ErrorBanner(it, Modifier.padding(top = 12.dp), "OK") { error = null } }

        SettingsGroup("Ответы ИИ") {
            SettingsRow(Icons.Rounded.Tune, "Стиль ответов") {}
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(14.dp))
                    .padding(3.dp),
            ) {
                responseStyles.forEach { (id, label) ->
                    val selected = user?.preferences?.responseStyle == id
                    val bg by animateColorAsState(if (selected) Aura.Violet.copy(alpha = 0.8f) else Color.Transparent, label = "style")
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(bg)
                            .clickable { save(style = id) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Aura.TextSecondary)
                    }
                }
            }
            Box {
                SettingsRow(
                    Icons.Rounded.Translate, "Язык ответов",
                    value = responseLanguages.firstOrNull { it.first == user?.preferences?.language }?.second ?: "Как в вопросе",
                ) { languageMenu = true }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }, containerColor = Aura.GraphiteLight) {
                    responseLanguages.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { languageMenu = false; save(language = id) })
                    }
                }
            }
        }

        SettingsGroup("Приложение") {
            SettingsRow(Icons.Rounded.Vibration, "Тактильный отклик", trailing = {
                Switch(
                    checked = haptics,
                    onCheckedChange = { haptics = it; repo.session.hapticsEnabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Aura.Violet, checkedThumbColor = Color.White),
                )
            }) { haptics = !haptics; repo.session.hapticsEnabled = haptics }
            SettingsRow(Icons.Rounded.Dns, "Сервер", value = repo.session.serverUrl.removePrefix("http://").removePrefix("https://")) { showServer = true }
        }

        SettingsGroup("Данные") {
            SettingsRow(
                Icons.Rounded.Sync, "Синхронизация",
                value = if (lastSync > 0) "Обновлено ${relativeTime(lastSync)}" else "Не выполнялась",
                trailing = { if (syncing) CircularProgressIndicator(Modifier.size(20.dp), color = Aura.Lilac, strokeWidth = 2.dp) },
            ) {
                scope.launch { try { repo.sync() } catch (e: ApiException) { error = e.message } }
            }
            SettingsRow(Icons.Rounded.DeleteSweep, "Очистить историю", tint = Aura.Danger) { clearing = true }
        }

        SettingsGroup("О приложении") {
            SettingsRow(Icons.Rounded.Info, "Версия", value = BuildConfig.VERSION_NAME) {}
            SettingsRow(Icons.AutoMirrored.Rounded.Logout, "Выйти из аккаунта", tint = Aura.Danger) { loggingOut = true }
        }
        Spacer(Modifier.height(BottomBarSpace + 24.dp))
    }

    if (clearing) {
        ConfirmDialog("Очистить историю?", "Все чаты будут удалены на всех ваших устройствах. Это нельзя отменить.", "Очистить", onConfirm = {
            scope.launch { try { repo.clearHistory() } catch (e: ApiException) { error = e.message } }
        }, onDismiss = { clearing = false })
    }
    if (loggingOut) {
        ConfirmDialog("Выйти из аккаунта?", "История останется на сервере и вернётся после входа.", "Выйти", onConfirm = {
            app.appScope.launch { repo.logout() }
        }, onDismiss = { loggingOut = false })
    }
    if (showServer) ServerDialog { showServer = false }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(Aura.RadiusM)), content = content)
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    tint: Color = Aura.Lilac,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == Aura.Danger) Aura.Danger else Aura.TextPrimary, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 180.dp))
        }
        trailing?.invoke()
    }
}

/** Lets the user point the app at their Aura server and checks that it answers. */
@Composable
fun ServerDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.auraApp
    val repo = app.repository
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(repo.session.serverUrl) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aura.GraphiteLight,
        title = { Text("Адрес сервера Aura", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Например http://192.168.1.20:8787 для сервера на вашем компьютере или https://aura.example.com",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AuraTextField(url, { url = it.trim() }, "http://…", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (it.startsWith("✓")) Aura.Success else Aura.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !checking && url.startsWith("http"), onClick = {
                checking = true
                status = null
                scope.launch {
                    val ok = repo.api.checkServer(url)
                    checking = false
                    if (ok) {
                        val changed = url.trimEnd('/') != repo.session.serverUrl
                        repo.session.serverUrl = url
                        status = "✓ Сервер доступен"
                        if (changed && repo.loggedIn.value) repo.signOutLocally()
                        onDismiss()
                    } else {
                        status = "Сервер не отвечает. Проверьте адрес и что телефон в той же сети."
                    }
                }
            }) { Text(if (checking) "Проверяю…" else "Сохранить", color = Aura.Lilac) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Aura.TextSecondary) } },
    )
}
