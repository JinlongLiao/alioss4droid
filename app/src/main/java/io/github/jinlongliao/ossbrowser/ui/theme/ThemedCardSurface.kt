package io.github.jinlongliao.ossbrowser.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/**
 * 为可点击列表容器应用主题材质和圆角，点击与语义仍由调用方决定。
 * @param color 明确的高亮容器色；未指定时使用主题正文表面。
 * @return 保留外部布局修饰符的裁切、背景和细边框。
 */
fun Modifier.themedCardSurface(color: Color = Color.Unspecified): Modifier = composed {
    val style = LocalThemeVisualStyle.current
    val shape = RoundedCornerShape(style.cornerRadius)
    val surface = if (color == Color.Unspecified) MaterialTheme.colorScheme.surface else color
    clip(shape).background(surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}
