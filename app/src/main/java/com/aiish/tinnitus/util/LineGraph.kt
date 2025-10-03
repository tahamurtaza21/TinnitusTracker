package com.aiish.tinnitus.util

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas

@Composable
fun LineGraph(
    title: String,
    data: List<Int>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (data.isEmpty()) return@Canvas

            val maxVal = data.maxOrNull() ?: 10
            val minVal = data.minOrNull() ?: 0

            val stepX = size.width / (data.size - 1).coerceAtLeast(1)
            val stepY = size.height / (maxVal - minVal).toFloat().coerceAtLeast(1f)

            val paths = mutableListOf<Path>()
            var currentPath: Path? = null

            data.forEachIndexed { index, value ->
                val x = index * stepX
                if (value != null) {
                    val y = size.height - ((value - minVal) * stepY)
                    if (currentPath == null) {
                        currentPath = Path().apply { moveTo(x, y) }
                    } else {
                        currentPath?.lineTo(x, y)
                    }
                } else {
                    currentPath?.let { paths.add(it) }
                    currentPath = null
                }
            }
            currentPath?.let { paths.add(it) }

            paths.forEach { path ->
                drawPath(
                    path = path,
                    color = Color(0xFF3F51B5),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            paths.forEach { path ->
                drawPath(
                    path = path,
                    color = Color(0xFF3F51B5), // deep blue line
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }


            // Draw circles + value labels
            data.forEachIndexed { index, value ->
                val x = index * stepX
                val label = value?.toString() ?: "N/A"
                val y = if (value != null) size.height - ((value - minVal) * stepY) else size.height / 2

                if (value != null) {
                    drawCircle(Color.Red, radius = 6f, center = Offset(x, y))
                }

                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    y - 10f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}
