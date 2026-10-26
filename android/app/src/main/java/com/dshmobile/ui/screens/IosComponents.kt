package com.dshmobile.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshmobile.ui.theme.IosGreen

/** 大标题：iOS Large Title，34pt 粗体，页面顶部。 */
@Composable
fun LargeTitle(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(text = text, style = MaterialTheme.typography.displayLarge)
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 分组小标题：iOS 分组头，灰色小字，不用卡片。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** iOS 分组卡片：白底 12dp 圆角，放在分组灰底上（Kimi 设置页的卡片语言）。 */
@Composable
fun IosCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/**
 * iOS 分组行：左标题、右灰色值 + 箭头，行间细分隔线。
 * 对照视频里“思考等级 极致 >”“对话长度 标准 >”那类设置行。
 */
@Composable
fun IosListRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (!value.isNullOrEmpty()) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (onClick != null) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 0.dp))
        }
    }
}

/** 顶部居中模型药丸：视频里“K3.1 Pro 极速”那种顶部选择器，点开换模型。 */
@Composable
fun ModelPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** 输入框上方的快捷药丸：视频里“新项目 / 3D Make / PPT”那排，点一下只填入输入框，不假装已执行。 */
@Composable
fun QuickActionPill(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 素色输入框：10dp 圆角，细分隔线边框，聚焦时系统蓝边框，44dp 以上触控高度。 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** 主按钮：iOS 系统蓝填充，44dp 以上高度，12dp 圆角。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun VerticalSpacer(heightDp: Int = 8) {
    Spacer(modifier = Modifier.height(heightDp.dp))
}

/** 空状态：居中纯文字，不套卡片。 */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun LoadingState(label: String = "加载中…") {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ErrorState(message: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("出错了", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 打字中：三个依次闪烁的小圆点。 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 600, delayMillis = i * 200), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(
                modifier = Modifier.size(7.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
            if (i < 2) Spacer(modifier = Modifier.width(5.dp))
        }
    }
}

/** 配对状态：iOS 绿点已配对 / 灰点未配对。 */
@Composable
fun StatusPill(paired: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape)
                .background(if (paired) IosGreen else MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (paired) "已配对" else "未配对",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
