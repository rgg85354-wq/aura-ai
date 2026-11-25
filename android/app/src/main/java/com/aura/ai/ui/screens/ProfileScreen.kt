package com.aura.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.ai.auraApp
import com.aura.ai.data.ApiException
import com.aura.ai.ui.Routes
import com.aura.ai.ui.components.AuraTextField
import com.aura.ai.ui.components.Avatar
import com.aura.ai.ui.components.ConfirmDialog
import com.aura.ai.ui.components.ErrorBanner
import com.aura.ai.ui.components.Glow
import com.aura.ai.ui.components.InputDialog
import com.aura.ai.ui.components.TopBar
import com.aura.ai.ui.components.formatDate
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(nav: NavController) {
    val app = LocalContext.current.auraApp
    val repo = app.repository
    val account by repo.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user = account?.user

    var editName by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Профиль", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Glow(Modifier.padding(0.dp).height(160.dp).fillMaxWidth(0.6f))
                Avatar(user?.name ?: "A", 104.dp, pro = user?.isPro == true)
            }
            Text(user?.name ?: "", style = MaterialTheme.typography.headlineMedium)
            Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                if (user?.isPro == true) "Aura Pro" else "Бесплатный план",
                style = MaterialTheme.typography.labelLarge,
                color = if (user?.isPro == true) Color.White else Aura.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (user?.isPro == true) Modifier.background(Aura.PrimaryGradient) else Modifier.glass(RoundedCornerShape(50)))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )

            error?.let { ErrorBanner(it, Modifier.padding(top = 14.dp), "OK") { error = null } }
            info?.let { Text(it, color = Aura.Success, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp)) }

            account?.let { acc ->
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(Aura.RadiusM)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Сегодня", style = MaterialTheme.typography.titleMedium)
                    UsageBar("Сообщения", acc.usage.messagesToday, acc.limits.dailyMessages)
                    UsageBar("Изображения", acc.usage.imagesToday, acc.limits.dailyImages)
                }
            }

            Column(Modifier.fillMaxWidth()) {
                SettingsGroup("Аккаунт") {
                    SettingsRow(Icons.Rounded.Edit, "Имя", value = user?.name) { editName = true }
                    SettingsRow(Icons.Rounded.Mail, "Email", value = user?.email) {}
                    SettingsRow(Icons.Rounded.CalendarMonth, "С нами с", value = user?.createdAt?.let { formatDate(it) }) {}
                    SettingsRow(Icons.Rounded.Lock, "Сменить пароль") { changePassword = true }
                }
                SettingsGroup("Подписка") {
                    SettingsRow(
                        Icons.Rounded.WorkspacePremium, "Aura Pro",
                        value = if (user?.isPro == true) "до ${user.proUntil?.let { formatDate(it) } ?: "—"}" else "Не оформлена",
                        tint = Aura.Gold,
                    ) { nav.navigate(Routes.PRO) }
                }
                SettingsGroup("") {
                    SettingsRow(Icons.AutoMirrored.Rounded.Logout, "Выйти", tint = Aura.Danger) {
                        app.appScope.launch { repo.logout() }
                    }
                    SettingsRow(Icons.Rounded.PersonRemove, "Удалить аккаунт", tint = Aura.Danger) { deleteAccount = true }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editName) {
        InputDialog("Ваше имя", user?.name.orEmpty(), "Имя", "Сохранить", onConfirm = { name ->
            scope.launch { try { repo.updateProfile(name = name) } catch (e: ApiException) { error = e.message } }
        }, onDismiss = { editName = false })
    }
    if (changePassword) {
        PasswordDialog(onDismiss = { changePassword = false }) { current, new ->
            scope.launch {
                try {
                    repo.changePassword(current, new)
                    info = "Пароль изменён. Другие устройства вышли из аккаунта."
                } catch (e: ApiException) {
                    error = e.message
                }
            }
        }
    }
    if (deleteAccount) {
        ConfirmDialog(
            "Удалить аккаунт?",
            "Все чаты, файлы и изображения будут удалены навсегда. Подписку в Google Play нужно отменить отдельно.",
            "Удалить навсегда",
            onConfirm = { app.appScope.launch { try { repo.deleteAccount() } catch (e: ApiException) { error = e.message } } },
            onDismiss = { deleteAccount = false },
        )
    }
}

@Composable
private fun UsageBar(label: String, used: Int, limit: Int) {
    val fraction = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$used / $limit", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Aura.PrimaryGradient))
        }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aura.GraphiteLight,
        title = { Text("Смена пароля", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuraTextField(current, { current = it }, "Текущий пароль", isPassword = true)
                AuraTextField(new, { new = it }, "Новый пароль (от 8 символов)", isPassword = true)
            }
        },
        confirmButton = {
            TextButton(enabled = current.isNotEmpty() && new.length >= 8, onClick = { onConfirm(current, new); onDismiss() }) {
                Text("Сменить", color = Aura.Lilac)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Aura.TextSecondary) } },
    )
}
