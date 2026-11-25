package com.aura.ai.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Aura design tokens. The iOS app uses the same values (see docs/design-system.md). */
object Aura {
    val Background = Color(0xFF07060B)
    val Graphite = Color(0xFF131219)
    val GraphiteLight = Color(0xFF1C1A24)
    val Violet = Color(0xFF8B5CF6)
    val VioletDeep = Color(0xFF6D28D9)
    val VioletLight = Color(0xFFC4B5FD)
    val Lilac = Color(0xFFA78BFA)
    val TextPrimary = Color(0xFFF5F3FF)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextTertiary = Color(0xFF71717A)
    val Danger = Color(0xFFF87171)
    val Success = Color(0xFF34D399)
    val Gold = Color(0xFFFBBF24)

    val GlassFill = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.035f)))
    val GlassFillStrong = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f)))
    val GlassStroke = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f), Violet.copy(alpha = 0.35f)),
    )
    val PrimaryGradient = Brush.linearGradient(listOf(Color(0xFFA78BFA), Violet, VioletDeep))
    val HeroGradient = Brush.linearGradient(
        listOf(Color(0xFF6D28D9).copy(alpha = 0.85f), Color(0xFF3B1E7A).copy(alpha = 0.75f), Color(0xFF16121F).copy(alpha = 0.9f)),
    )

    val RadiusL = 28.dp
    val RadiusM = 22.dp
    val RadiusS = 16.dp
}

private val AuraTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = Aura.TextPrimary),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Aura.TextPrimary),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Aura.TextPrimary),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Aura.TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = Aura.TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Aura.TextSecondary),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Aura.TextPrimary),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Aura.TextSecondary),
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Aura.Violet,
            onPrimary = Color.White,
            secondary = Aura.Lilac,
            background = Aura.Background,
            surface = Aura.Graphite,
            surfaceVariant = Aura.GraphiteLight,
            onSurface = Aura.TextPrimary,
            onSurfaceVariant = Aura.TextSecondary,
            error = Aura.Danger,
            outline = Color.White.copy(alpha = 0.15f),
        ),
        typography = AuraTypography,
        content = content,
    )
}

/** Translucent glass surface with a light-catching edge. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(Aura.RadiusM),
    strong: Boolean = false,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(if (strong) Aura.GlassFillStrong else Aura.GlassFill, shape)
    .border(borderWidth, Aura.GlassStroke, shape)

/** Black-graphite background with slowly drifting violet light. */
@Composable
fun AuraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "glow")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Aura.Background, Aura.Graphite, Aura.Background)))
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Aura.VioletDeep.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(w * (0.1f + 0.15f * drift), h * 0.08f),
                        radius = w * 0.9f,
                    ),
                    radius = w * 0.9f,
                    center = Offset(w * (0.1f + 0.15f * drift), h * 0.08f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Aura.Violet.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(w * (0.95f - 0.2f * drift), h * (0.75f + 0.05f * drift)),
                        radius = w * 0.8f,
                    ),
                    radius = w * 0.8f,
                    center = Offset(w * (0.95f - 0.2f * drift), h * (0.75f + 0.05f * drift)),
                )
            },
        content = content,
    )
}

