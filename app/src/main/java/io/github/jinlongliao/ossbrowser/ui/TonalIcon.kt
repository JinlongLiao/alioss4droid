/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jinlongliao.ossbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import io.github.jinlongliao.ossbrowser.ui.theme.LocalThemeVisualStyle
import io.github.jinlongliao.ossbrowser.ui.theme.ThemeIconStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 随主题切换描线、柔和色块、玻璃和几何容器的语义图标。
 *
 * 统一的 44dp 容器扩大视觉层次和触控区域辨识度，图标本身保持 Material 推荐的 24dp；
 * 它只用于列表前导图标，不自行处理点击事件，避免与父级列表项产生嵌套点击语义。
 *
 * @param imageVector Material 图标矢量。
 * @param contentDescription 无障碍描述；纯装饰图标传 `null`。
 * @param modifier 作用于图标容器的修饰符。
 * @param tint 图标颜色，默认使用主题主色。
 * @param containerColor 图标容器颜色，默认使用主题主色容器。
 */
@Composable
fun TonalIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val style = LocalThemeVisualStyle.current.iconStyle
    val shape = RoundedCornerShape(if (style == ThemeIconStyle.GEOMETRIC) 5.dp else 12.dp)
    val material = when (style) {
        ThemeIconStyle.LINE -> Modifier
        ThemeIconStyle.GLASS -> Modifier
            .background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), containerColor.copy(alpha = 0.65f))),
                shape
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        ThemeIconStyle.GEOMETRIC -> Modifier
            .background(containerColor, shape)
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
        ThemeIconStyle.SOFT -> Modifier.background(containerColor, shape)
    }
    Box(
        modifier = modifier.size(44.dp).then(material),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
    }
}
