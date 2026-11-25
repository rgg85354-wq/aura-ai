package com.aura.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.ai.auraApp
import com.aura.ai.data.ApiException
import com.aura.ai.ui.components.AuraSpark
import com.aura.ai.ui.components.AuraTextField
import com.aura.ai.ui.components.ErrorBanner
import com.aura.ai.ui.components.Glow
import com.aura.ai.ui.components.GradientButton
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val app = LocalContext.current.auraApp
    val repo = app.repository
    val scope = rememberCoroutineScope()

    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showServer by remember { mutableStateOf(false) }

    val float = rememberInfiniteTransition(label = "logo")
    val lift by float.animateFloat(0f, 1f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "lift")

    fun submit() {
        error = null
        when {
            register && name.isBlank() -> error = "Введите имя"
            !email.contains("@") -> error = "Введите корректный email"
            password.length < 8 -> error = "Пароль должен быть не короче 8 символов"
            else -> scope.launch {
                loading = true
                try {
                    if (register) repo.register(name, email, password) else repo.login(email, password)
                } catch (e: ApiException) {
                    error = e.message
                } finally {
                    loading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer { translationY = -10f * lift }) {
            Glow(Modifier.size(180.dp))
            Box(Modifier.size(104.dp).glass(RoundedCornerShape(32.dp), strong = true), contentAlignment = Alignment.Center) {
                AuraSpark(Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Aura", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Text(
            if (register) "Создайте аккаунт, чтобы история чатов синхронизировалась между устройствами"
            else "Войдите, чтобы продолжить общение с вашим ИИ-ассистентом",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        // Sign in / Sign up switch
        Row(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(18.dp))
                .padding(4.dp),
        ) {
            listOf(false to "Вход", true to "Регистрация").forEach { (isRegister, label) ->
                val selected = register == isRegister
                val bg by animateColorAsState(if (selected) Aura.Violet.copy(alpha = 0.85f) else Color.Transparent, label = "seg")
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .clickable { register = isRegister; error = null }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Aura.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(register, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                AuraTextField(
                    name, { name = it }, "Имя", leading = Icons.Rounded.Person,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
            AuraTextField(
                email, { email = it.trim() }, "Email", leading = Icons.Rounded.Mail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            AuraTextField(
                password, { password = it }, "Пароль", leading = Icons.Rounded.Lock,
                isPassword = !showPassword,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailing = {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        "Показать пароль",
                        tint = Aura.TextSecondary,
                        modifier = Modifier.size(20.dp).clickable { showPassword = !showPassword },
                    )
                },
            )
        }

        AnimatedVisibility(error != null) {
            ErrorBanner(error.orEmpty(), Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(20.dp))
        GradientButton(
            text = if (register) "Создать аккаунт" else "Войти",
            onClick = ::submit,
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { showServer = true }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Dns, null, tint = Aura.TextTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Сервер: ${repo.session.serverUrl}", style = MaterialTheme.typography.labelMedium, color = Aura.TextTertiary)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showServer) ServerDialog(onDismiss = { showServer = false })
}
