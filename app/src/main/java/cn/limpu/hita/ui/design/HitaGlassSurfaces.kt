package cn.limpu.hita.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlin.math.max
import kotlin.math.min

private object HitaCourseHazeRegistry {
    var hazeState by mutableStateOf<Any?>(null)
}

fun updateHitaCourseHazeState(hazeState: Any?) {
    HitaCourseHazeRegistry.hazeState = hazeState
}

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
        base.copy(alpha = minOf(base.alpha, alpha))
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
        BorderStroke(0.5.dp, Color.White.copy(alpha = alpha))
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
        val shadowElevation = min(elevation.value * 0.30f, 5f).dp
        this
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.035f),
                        spotColor = Color.Black.copy(alpha = 0.050f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val material = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.19f),
                        Color(0xFFDCEEFF).copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.24f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                val topEdge = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.66f),
                        Color.White.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
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
    usesRealBackdrop: Boolean = true,
): Modifier {
    return if (hitaIsAppleGlassSurface()) {
        val hazeState = HitaCourseHazeRegistry.hazeState as? HazeState
        val edgeHighlightOpacity = if (isMuted) 0.36f else 0.62f
        val edgeShadowOpacity = if (isMuted) 0.10f else 0.16f
        val motionHighlightOpacity = if (isMuted) 0.08f else 0.16f
        val hazeStyle = HazeStyle(
            tint = Color.White.copy(alpha = if (isMuted) 0.16f else 0.22f),
            blurRadius = if (isMuted) 18.dp else 28.dp,
            noiseFactor = 0.08f
        )
        this
            .then(
                if (usesRealBackdrop && hazeState != null) {
                    Modifier.hazeChild(state = hazeState, shape = shape, style = hazeStyle)
                } else {
                    Modifier
                }
            )
            .shadow(
                elevation = if (isMuted) 1.dp else 2.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.035f),
                spotColor = Color.Black.copy(alpha = 0.055f)
            )
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val material = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isMuted) 0.16f else 0.30f),
                        tint.copy(alpha = if (isMuted) 0.08f else 0.20f),
                        Color.White.copy(alpha = if (isMuted) 0.08f else 0.16f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                val edgeHighlight = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = edgeHighlightOpacity),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
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
                    drawCircle(
                        brush = motionHighlight,
                        radius = highlightRadius,
                        center = highlightCenter
                    )
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
