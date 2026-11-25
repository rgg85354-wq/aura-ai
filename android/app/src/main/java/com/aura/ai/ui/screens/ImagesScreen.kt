package com.aura.ai.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aura.ai.auraApp
import com.aura.ai.data.ApiException
import com.aura.ai.data.FileLoader
import com.aura.ai.data.GeneratedImage
import com.aura.ai.ui.Routes
import com.aura.ai.ui.components.AuraTextField
import com.aura.ai.ui.components.ConfirmDialog
import com.aura.ai.ui.components.ErrorBanner
import com.aura.ai.ui.components.GlassIconButton
import com.aura.ai.ui.components.GradientButton
import com.aura.ai.ui.components.TopBar
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.glass
import kotlinx.coroutines.launch

private val styles = listOf(
    "none" to "Без стиля", "photo" to "Фото", "3d" to "3D", "anime" to "Аниме",
    "art" to "Арт", "minimal" to "Минимализм", "neon" to "Неон",
)
private val sizes = listOf("1024x1024" to "1:1", "1024x1536" to "2:3", "1536x1024" to "3:2")

@Composable
fun ImagesScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = context.auraApp.repository
    val images by repo.images.collectAsStateWithLifecycle()
    val account by repo.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("none") }
    var size by remember { mutableStateOf("1024x1024") }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var limit by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<GeneratedImage?>(null) }
    var viewing by remember { mutableStateOf<GeneratedImage?>(null) }

    fun generate() {
        error = null
        limit = false
        generating = true
        scope.launch {
            try {
                latest = repo.generateImage(prompt.trim(), style, size)
            } catch (e: ApiException) {
                if (e.isLimit) limit = true else error = e.message
            } finally {
                generating = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Генерация изображений", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            AuraTextField(prompt, { prompt = it }, "Опишите картинку: «уютная кофейня в дождливый вечер, тёплый свет»", singleLine = false, minLines = 3)
            Spacer(Modifier.height(14.dp))
            Text("Стиль", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ChipRow(styles, style) { style = it }
            Spacer(Modifier.height(14.dp))
            Text("Формат", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ChipRow(sizes, size) { size = it }
            Spacer(Modifier.height(18.dp))
            GradientButton(
                if (generating) "Создаю…" else "Создать",
                onClick = ::generate,
                enabled = prompt.isNotBlank() && !generating,
                loading = generating,
                icon = Icons.Rounded.AutoAwesome,
                modifier = Modifier.fillMaxWidth(),
            )
            account?.let {
                val left = (it.limits.dailyImages - it.usage.imagesToday).coerceAtLeast(0)
                Text(
                    "Осталось сегодня: $left из ${it.limits.dailyImages}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
                )
            }
            AnimatedVisibility(limit) {
                Column(Modifier.padding(top = 12.dp)) {
                    ErrorBanner("Дневной лимит изображений исчерпан", action = "Aura Pro") { nav.navigate(Routes.PRO) }
                }
            }
            error?.let { ErrorBanner(it, Modifier.padding(top = 12.dp)) }

            Spacer(Modifier.height(18.dp))
            if (generating) GeneratingPlaceholder(size)
            else latest?.let { img -> ImageTile(img, Modifier.fillMaxWidth().aspectRatio(ratio(img.size))) { viewing = img } }

            if (images.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("Галерея", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                images.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { img -> ImageTile(img, Modifier.weight(1f).aspectRatio(1f)) { viewing = img } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    viewing?.let { img ->
        ImageViewer(img, onClose = { viewing = null }, onDeleted = { if (latest?.id == img.id) latest = null; viewing = null })
    }
}

private fun ratio(size: String): Float {
    val (w, h) = size.split("x").map { it.toFloatOrNull() ?: 1f }
    return w / h
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            val active = id == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else Aura.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (active) Modifier.background(Aura.PrimaryGradient) else Modifier.glass(RoundedCornerShape(50)))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun ImageTile(image: GeneratedImage, modifier: Modifier, onClick: () -> Unit) {
    val api = LocalContext.current.auraApp.repository.api
    AsyncImage(
        model = api.absoluteUrl(image.url),
        contentDescription = image.prompt,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(Aura.RadiusM))
            .background(Aura.GraphiteLight)
            .border(1.dp, Aura.GlassStroke, RoundedCornerShape(Aura.RadiusM))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun GeneratingPlaceholder(size: String) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "x")
    val pulse by t.animateFloat(0.9f, 1.05f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "p")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio(size))
            .clip(RoundedCornerShape(Aura.RadiusM))
            .background(Aura.GraphiteLight)
            .border(1.dp, Aura.GlassStroke, RoundedCornerShape(Aura.RadiusM)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Aura.Violet.copy(alpha = 0.25f), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(x * 800f, 0f),
                        end = androidx.compose.ui.geometry.Offset(x * 800f + 400f, 400f),
                    ),
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Aura.Lilac, modifier = Modifier.size(40.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
            Spacer(Modifier.height(10.dp))
            Text("Рисую… обычно это 20–60 секунд", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ImageViewer(image: GeneratedImage, onClose: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val repo = context.auraApp.repository
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun withBytes(action: suspend (ByteArray) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action(repo.api.download(image.url))
            } catch (e: ApiException) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                GlassIconButton(Icons.Rounded.Close, "Закрыть", onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = repo.api.absoluteUrl(image.url),
                    contentDescription = image.prompt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Aura.RadiusM)),
                )
            }
            Text(image.prompt, style = MaterialTheme.typography.bodyMedium, maxLines = 3, modifier = Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GlassIconButton(Icons.Rounded.Download, "Сохранить", {
                    withBytes { bytes ->
                        val ok = FileLoader.saveToGallery(context, bytes, "aura-${image.id.take(8)}.png")
                        if (ok) {
                            Toast.makeText(context, "Сохранено в галерею", Toast.LENGTH_SHORT).show()
                        } else {
                            context.startActivity(FileLoader.shareIntent(context, bytes, "aura-${image.id.take(8)}.png", "image/png"))
                        }
                    }
                }, size = 56.dp)
                GlassIconButton(Icons.Rounded.Share, "Поделиться", {
                    withBytes { bytes -> context.startActivity(FileLoader.shareIntent(context, bytes, "aura-${image.id.take(8)}.png", "image/png")) }
                }, size = 56.dp)
                GlassIconButton(Icons.Rounded.Delete, "Удалить", { confirmDelete = true }, size = 56.dp, tint = Aura.Danger)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Удалить изображение?", "Оно исчезнет из галереи на всех устройствах.", "Удалить", onConfirm = {
            scope.launch {
                try {
                    repo.deleteImage(image.id)
                    onDeleted()
                } catch (e: ApiException) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }, onDismiss = { confirmDelete = false })
    }
}
