package cn.limpu.hita.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 经典风格专用的受控课程色选择器；特殊风格不应调用此弹窗。 */
@Composable
fun HitaCoursePaletteDialog(
    selectedColor: Color,
    onSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = hitaCoursePalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择课程颜色", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HitaTheme.tokens.spacing.md)) {
                Text(
                    text = "颜色来自当前基础配色；切换基础配色后，课程气泡会同步映射。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    palette.forEachIndexed { index, color ->
                        val selected = color.toArgb() == selectedColor.toArgb()
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = "课程颜色 ${index + 1}" }
                                .background(color, shape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                    shape = shape,
                                )
                                .clickable { onSelected(color) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Text(
                                    text = "✓",
                                    color = if (color.luminance() > 0.48f) Color.Black else Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
