package io.github.jinlongliao.ossbrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.R

/**
 * 页面品牌摘要卡：将启动图标、页面用途和当前上下文放在同一视觉层级。
 *
 * 容器使用当前主题的浅色或深色实色渐变；不模糊正文，也不引入图片网络请求。
 *
 * @param title 页面主标题。
 * @param subtitle 当前账号、目录或页面说明；长内容允许换行并最终省略。
 * @param modifier 外层布局修饰符。
 */
@Composable
fun CloudPageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
            .border(1.dp, colors.outlineVariant, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_artwork),
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
