package com.aura.ai.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.ai.auraApp
import com.aura.ai.data.BillingStatus
import com.aura.ai.ui.components.ErrorBanner
import com.aura.ai.ui.components.Glow
import com.aura.ai.ui.components.GradientButton
import com.aura.ai.ui.components.TopBar
import com.aura.ai.ui.components.formatDate
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass
import kotlinx.coroutines.launch

@Composable
fun ProScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.auraApp
    val billing = app.billing
    val account by app.repository.account.collectAsStateWithLifecycle()
    val plans by billing.plans.collectAsStateWithLifecycle()
    val status by billing.status.collectAsStateWithLifecycle()
    val available by billing.available.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user = account?.user
    val limits = account?.limits

    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(plans) {
        if (selected == null) selected = plans.lastOrNull()?.productId
    }
    LaunchedEffect(Unit) {
        billing.resetStatus()
        billing.connect()
    }

    val t = rememberInfiniteTransition(label = "crown")
    val float by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "f")

    Column(Modifier.fillMaxSize()) {
        TopBar("Aura Pro", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer { translationY = -8f * float }) {
                Glow(Modifier.size(200.dp), Aura.Violet)
                Box(Modifier.size(108.dp).glass(RoundedCornerShape(34.dp), strong = true), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = Aura.Gold, modifier = Modifier.size(56.dp))
                }
            }
            Text(
                if (user?.isPro == true) "Pro активен" else "Раскройте Aura полностью",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (user?.isPro == true) {
                Text("Действует до ${user.proUntil?.let { formatDate(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))

            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(Aura.RadiusM)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Benefit(Icons.Rounded.AllInclusive, "До ${if (limits != null && user?.isPro == true) limits.dailyMessages else 1000} сообщений в день", "Вместо 30 в бесплатном плане")
                Benefit(Icons.Rounded.Image, "100 изображений в день", "Вместо 5 в бесплатном плане")
                Benefit(Icons.Rounded.Psychology, "Более глубокое мышление", "ИИ дольше думает над кодом и сложными вопросами")
                Benefit(Icons.Rounded.Sync, "На всех устройствах", "Одна подписка для iPhone и Android в вашем аккаунте")
            }
            Spacer(Modifier.height(18.dp))

            when {
                user?.isPro == true -> {
                    GradientButton("Управлять подпиской", onClick = {
                        val pkg = context.packageName
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=$pkg")))
                    }, modifier = Modifier.fillMaxWidth())
                }
                plans.isNotEmpty() -> {
                    plans.forEach { plan ->
                        val active = plan.productId == selected
                        Row(
                            Modifier
                                .padding(bottom = 10.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Aura.RadiusM))
                                .background(if (active) Aura.Violet.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
                                .border(if (active) 2.dp else 1.dp, if (active) Aura.PrimaryGradient else Aura.GlassStroke, RoundedCornerShape(Aura.RadiusM))
                                .clickable { selected = plan.productId }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape)
                                    .background(if (active) Aura.Violet else Color.Transparent)
                                    .border(2.dp, if (active) Aura.Violet else Aura.TextTertiary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (active) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(plan.title, style = MaterialTheme.typography.titleMedium)
                                if (plan.productId.contains("year")) Text("Выгоднее всего", style = MaterialTheme.typography.labelMedium.copy(color = Aura.Lilac))
                            }
                            Text("${plan.price} ${plan.period}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    GradientButton(
                        "Оформить Aura Pro",
                        onClick = {
                            val plan = plans.firstOrNull { it.productId == selected } ?: return@GradientButton
                            (context as? Activity)?.let { billing.buy(it, plan) }
                        },
                        loading = status is BillingStatus.Working,
                        icon = Icons.Rounded.AutoAwesome,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                available == false -> ErrorBanner("Google Play недоступен. Установите приложение из Google Play и войдите в аккаунт Google.")
                else -> Text(
                    "Загружаю тарифы… Если они не появляются, приложение должно быть установлено из Google Play, а подписки aura_pro_monthly и aura_pro_yearly — созданы в Play Console.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            when (val s = status) {
                is BillingStatus.Failed -> ErrorBanner(s.message, Modifier.padding(top = 12.dp))
                is BillingStatus.Success -> Text(s.message, color = Aura.Success, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                else -> {}
            }

            TextButton(onClick = { scope.launch { billing.restore(silent = false) } }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Восстановить покупки", color = Aura.Lilac)
            }
            Text(
                "Подписка продлевается автоматически, пока вы не отмените её в Google Play. Оплата списывается с аккаунта Google Play при подтверждении покупки.",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).glass(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Aura.Lilac, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium)
        }
    }
}
