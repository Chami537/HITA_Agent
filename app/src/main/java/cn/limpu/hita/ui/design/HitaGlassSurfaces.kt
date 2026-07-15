package cn.limpu.hita.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun hitaIsAppleGlassSurface(): Boolean {
    return MaterialTheme.colorScheme.surface.alpha < 0.98f
}

@Composable
fun hitaGlassContainerColor(
    base: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.58f,
): Color {
    return if (hitaIsAppleGlassSurface()) {
        if (HitaTheme.isDark) {
            base.copy(alpha = minOf(base.alpha, alpha))
        } else {
            base.copy(alpha = alpha.coerceIn(0.32f, 0.72f))
        }
    } else {
        base
    }
}

@Composable
fun hitaGlassCardColors(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    glassAlpha: Float = 0.58f,
): CardColors {
    return CardDefaults.cardColors(
        containerColor = hitaGlassContainerColor(containerColor, glassAlpha)
    )
}

@Composable
fun hitaGlassCardBorder(alpha: Float = 0.34f): BorderStroke? {
    return if (hitaIsAppleGlassSurface()) {
        BorderStroke(
            0.5.dp,
            if (HitaTheme.isDark) {
                Color.White.copy(alpha = alpha)
            } else {
                Color(0xFF6F8EAC).copy(alpha = alpha.coerceAtLeast(0.22f))
            }
        )
    } else {
        null
    }
}

@Composable
fun Modifier.hitaGlassCardModifier(
    shape: Shape,
    elevation: Dp = 14.dp,
): Modifier {
    return if (hitaIsAppleGlassSurface()) {
        val isDark = HitaTheme.isDark
        val shadowElevation = min(elevation.value * if (isDark) 0.30f else 0.38f, 6f).dp
        this
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.035f else 0.050f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.050f else 0.080f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val material = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.13f),
                            Color.White.copy(alpha = 0.07f),
                            Color(0xFFDCEEFF).copy(alpha = 0.05f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color(0xFFDCEEFF).copy(alpha = 0.20f),
                            Color(0xFFBFDFFF).copy(alpha = 0.12f)
                        )
                    },
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                val topEdge = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.52f),
                            Color(0xFF9CC8F2).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    },
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                val bottomEdge = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.085f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                val stroke1 = Stroke(width = 1.dp.toPx())
                val strokeHairline = Stroke(width = 0.5.dp.toPx())
                onDrawWithContent {
                    drawOutline(outline = outline, brush = material)
                    drawContent()
                    drawOutline(outline = outline, brush = topEdge, style = stroke1)
                    drawOutline(outline = outline, brush = bottomEdge, style = strokeHairline)
                }
            }
    } else {
        this
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = shape)
            .clip(shape)
    }
}

@Composable
fun Modifier.hitaCourseCrystalGlassModifier(
    shape: Shape,
    tint: Color,
    isMuted: Boolean = false,
    gradientEnabled: Boolean = true,
    opacity: Float = 1f,
): Modifier {
    return if (hitaIsAppleGlassSurface()) {
        val isDark = HitaTheme.isDark
        val opacityScale = opacity.coerceIn(0.2f, 1f)
        val edgeHighlightOpacity = (
            if (isDark) {
                if (isMuted) 0.36f else 0.62f
            } else {
                if (isMuted) 0.30f else 0.48f
            }
        ) * opacityScale
        val edgeShadowOpacity = (
            if (isDark) {
                if (isMuted) 0.10f else 0.16f
            } else {
                if (isMuted) 0.08f else 0.12f
            }
        ) * opacityScale
        val motionHighlightOpacity = (
            if (isDark) {
                if (isMuted) 0.08f else 0.16f
            } else {
                if (isMuted) 0.06f else 0.11f
            }
        ) * opacityScale
        this
            .shadow(
                elevation = if (isMuted) 1.dp else if (isDark) 2.dp else 3.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.035f else 0.055f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.055f else 0.085f)
            )
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val material = if (gradientEnabled) {
                    Brush.linearGradient(
                        colors = if (isDark) {
                            listOf(
                                Color.White.copy(alpha = (if (isMuted) 0.16f else 0.30f) * opacityScale),
                                tint.copy(alpha = (if (isMuted) 0.08f else 0.20f) * opacityScale),
                                Color.White.copy(alpha = (if (isMuted) 0.08f else 0.16f) * opacityScale)
                            )
                        } else {
                            listOf(
                                tint.copy(alpha = (if (isMuted) 0.10f else 0.18f) * opacityScale),
                                tint.copy(alpha = (if (isMuted) 0.16f else 0.34f) * opacityScale),
                                Color.White.copy(alpha = (if (isMuted) 0.08f else 0.12f) * opacityScale)
                            )
                        },
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                } else {
                    SolidColor(
                        tint.copy(
                            alpha = (
                                if (isDark) {
                                    if (isMuted) 0.10f else 0.24f
                                } else {
                                    if (isMuted) 0.16f else 0.34f
                                }
                            ) * opacityScale
                        )
                    )
                }
                val edgeHighlight = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = edgeHighlightOpacity),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = edgeHighlightOpacity),
                            tint.copy(alpha = 0.30f * opacityScale),
                            tint.copy(alpha = 0.16f * opacityScale)
                        )
                    },
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                val edgeShadow = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = edgeShadowOpacity)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                val highlightRadius = max(min(size.width, size.height) * 0.52f, 18.dp.toPx())
                val highlightCenter = Offset(size.width * 0.20f, size.height * 0.22f)
                val motionHighlight = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = motionHighlightOpacity),
                        Color.White.copy(alpha = motionHighlightOpacity * 0.32f),
                        Color.Transparent
                    ),
                    center = highlightCenter,
                    radius = highlightRadius
                )
                val highlightStroke = Stroke(width = 1.1.dp.toPx())
                val shadowStroke = Stroke(width = 1.dp.toPx())
                onDrawBehind {
                    drawOutline(outline = outline, brush = material)
                    if (gradientEnabled) {
                        drawCircle(
                            brush = motionHighlight,
                            radius = highlightRadius,
                            center = highlightCenter
                        )
                    }
                    drawOutline(outline = outline, brush = edgeHighlight, style = highlightStroke)
                    drawOutline(outline = outline, brush = edgeShadow, style = shadowStroke)
                }
            }
    } else {
        this
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = shape)
            .clip(shape)
    }
}
