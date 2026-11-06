package io.github.jinlongliao.ossbrowser.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.jinlongliao.ossbrowser.data.ThemePalette

/**
 * 绘制静态主题背景。晴空使用云团轮廓，彩色与玻璃使用柔和光晕；不加载图片或持续运行动画。
 *
 * 玻璃方案的底层光晕本身使用柔边，正文通过高不透明度表面承载，不模糊文本。
 * @param modifier 背景区域大小，通常覆盖整个应用窗口。
 */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    val style = LocalThemeVisualStyle.current
    Canvas(modifier.fillMaxSize()) {
        val colors = style.backgroundColors
        if (colors.size == 1) {
            drawRect(colors.first())
        } else {
            drawRect(Brush.verticalGradient(colors))
            val radius = size.width * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.first().copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(size.width, size.height * 0.12f), radius = radius
                ),
                radius = radius,
                center = Offset(size.width, size.height * 0.12f)
            )
        }
        if (style.palette == ThemePalette.SUMMER_SKY) {
            val cloud = Color.White.copy(alpha = if (style.darkTheme) 0.045f else 0.5f)
            drawCircle(cloud, size.width * 0.25f, Offset(size.width * 0.82f, size.height * 0.14f))
            drawCircle(cloud, size.width * 0.18f, Offset(size.width * 0.56f, size.height * 0.18f))
            drawCircle(cloud, size.width * 0.22f, Offset(size.width * 0.98f, size.height * 0.22f))
        }
    }
}
