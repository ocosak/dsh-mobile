package com.dshmobile.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dshmobile.ui.theme.IosGreen

/** 底部给标签栏留的空隙：标签栏高 = 行内边距 16 + 图标 24 + 单项内边距 8 +
 * 分隔线 0.5 + 预留 8，其中标签行高 16sp 随字号缩放、其余是 dp 不缩放；
 * 按此算，200% 字号时最后一张卡也不会被标签栏盖住。 */
@Composable
fun tabBarClearance(): Dp {
    val fontScale = LocalDensity.current.fontScale
    return 56.5.dp + 16.dp * fontScale
}

/** 大标题：iOS Large Title，34pt 粗体，页面顶部。 */
@Composable
fun LargeTitle(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        // 大标题是页面标题：给 heading 语义，TalkBack 可按标题导航。
        Text(text = text, style = MaterialTheme.typography.displayLarge, modifier = Modifier.semantics { heading() })
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 分组小标题：iOS 分组头，灰色小字，不用卡片。标题左右 20dp、分组内容左右 16dp：
 * iOS 分组样式里标题比卡片多缩进 4dp，不是写错，别对齐。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 分组头也是标题：与大标题同理，给 heading 语义。
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** iOS 分组卡片：白底 medium 圆角（12dp），放在分组灰底上（Kimi 设置页的卡片语言）。 */
@Composable
fun IosCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

// 小字按钮触控内边距：labelMedium 行高 18dp + 上下 15dp = 48dp，复制钮与
// 重命名小字钮共用；横向 8dp 让文字不贴边。放 clickable 外侧（padding 在
// clickable 之后），触控区才包含这圈内边距。
val TextButtonTouchModifier = Modifier.padding(horizontal = 8.dp, vertical = 15.dp)

// 分组内容左右边距：设置页与配对页原来各有一份同值私有定义（名字还不一样），
// 合并到共用组件文件里一处定义，两页共用，改边距只改这里。
val SectionContentModifier = Modifier.padding(horizontal = 16.dp)

/** 底部弹层内容内边距：左右 20dp、下 24dp，换模型与重命名两个弹层共用；
 * R194 把两处的 padding().padding() 链合并成一次，这里再抽成一处定义，
 * 以后改弹层边距只改这里，不会只改一处漏另一处。 */
val SheetContentModifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)

/** 底部弹层标题行：标题 + 右上角关闭，换模型与重命名两个弹层共用同一种材质。 */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
    }
}

/**
 * iOS 分组行：左标题（下面可带灰色副标题）、右灰色值 + 箭头，行间细分隔线。
 * 对照视频里设置页“标题 + 灰色副标题 + 右侧值 >”那类行。
 */
@Composable
fun IosListRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    subtitle: String? = null,
) {
    // 可点行才挂 clickable + Button 角色 + 右侧箭头：展示行（onClick 为 null）
    // 以前挂的是 enabled = false 的 clickable——无障碍树里仍留一个“已停用”
    // 的可点节点，TalkBack 可能把它报成停用的按钮；展示行根本不挂 clickable，
    // 树里就没有这个节点，比“停用”更干净。目前唯一用量就是设置页的连接状态
    // 展示行，正好命中这个情况。
    Column(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick?.invoke() } else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    // 副标题以前只给 1 行：服务器地址这种长隧道地址会被截成
                    // 一行省略号，用户核对不了完整地址；给 2 行。
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (!value.isNullOrBlank()) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                // 间隔只在有箭头时加：以前间隔跟在值后面，无箭头的展示行
                // 右边会多 8dp 死空隙；有值无箭头时值应顶到右边缘。
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** 输入框里的紧凑模型药丸：新视频里模型选择在输入区（不是顶部），点开换模型。 */
@Composable
fun CompactModelPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        // 边框与快捷药丸、聊天输入框一致：以前没边框，深色下药丸底
        // （#1C1C1E）在输入框底（#2C2C2E）上几乎看不出边界，只有文字
        // 浮着；细边线让药丸边界在深浅两色下都看得见。
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MODEL_PILL_MAX_WIDTH),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 思考状态行：视频里“思考中…”那行，状态词会轮换（思考中/梳理中/生成中/整理中），
 * 不是主机发来的具体步骤，只是生成中的等待态，流式正文一到就换成正文。
 */
private val ThinkingLabels = listOf("思考中…", "梳理中…", "生成中…", "整理中…")

private const val THINKING_ROTATE_MS = 1200L
private const val DOT_PULSE_MS = 600
private const val DOT_STAGGER_MS = 200
// 打字圆点颗数：动效版与减动效静态版两处循环同值；减动效用户看到的应与
// 动效版同颗，否则两态指示不一致——抽成常量，改一处即两处。
private const val TYPING_DOT_COUNT = 3
// 模型药丸文字最大宽：360dp 屏上输入行是 16（页边距）+ 8（行内边距）+ 48
// （+ 号）+ 4（字段左隙）+ 输入框 + 4（字段右隙）+ 药丸 + 8（药丸与发送
// 间隙，R271）+ 48（发送）+ 8 + 16，药丸文字 96dp 时药丸总宽约 148dp、
// 输入框只剩约 52dp，打字要一直换行；80dp 时药丸约 132dp、输入框约 68dp，
// 仍窄但可用；完整模型名在弹层输入框里可见，药丸截断不丢信息（4 字中文
// “默认模型”约 64dp，80dp 内完整显示）。
private const val MODEL_PILL_MAX_WIDTH = 80.dp

// 减动效：系统“移除动画”（动画缩放为 0）时返回 true；记住一次，
// 避免设置变化导致组合内动画调用次数变化。
@Composable
private fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun ThinkingStatus(modifier: Modifier = Modifier) {
    var index by remember { mutableStateOf(0) }
    val reducedMotion = isReducedMotion()
    LaunchedEffect(Unit) {
        // 减动效时不轮换：只显示第一个状态词，仍保留打字圆点（静态版见下）。
        if (reducedMotion) return@LaunchedEffect
        while (true) {
            delay(THINKING_ROTATE_MS)
            index = (index + 1) % ThinkingLabels.size
        }
    }
    Row(modifier = modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TypingIndicator()
        Spacer(modifier = Modifier.width(8.dp))
        Text(ThinkingLabels[index], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** 输入框上方的快捷药丸：视频里“新项目 / 3D Make / PPT”那排，点一下只填入输入框，不假装已执行。 */
@Composable
fun QuickActionPill(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 素色输入框：small 圆角（10dp），细分隔线边框，聚焦时系统蓝边框，48dp 以上触控高度。
 * 密码框默认遮住、右上角有眼睛开关：Material 指引是默认遮住、允许临时
 * 展开核对，配对码粘贴后看一眼再提交，比全程盲打少出错。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // 占位字号显式给 bodyMedium：输入字号就是 bodyMedium（见下 textStyle），
        // 占位若靠默认 LocalTextStyle（bodyLarge 17sp），用户一打字字号就从
        // 17 跳到 16、视觉跳变；显式对齐后占位与输入同字号（若本来已继承，
        // 则只是显式化、无视觉变化）。
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = singleLine,
        // 密码框用 Password 键盘类型：不给输入法联想和自动纠错的机会，
        // 配对码不会被输入法改写；服务器地址由调用处传 Uri 类型。
        // 单行框的回车改成“完成”并收键盘：以前回车无事可做、键盘一直盖着；
        // 多行框不改（回车要换行），AppTextField 调用处目前全是单行。
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "隐藏" else "显示",
                    )
                }
            }
        } else if (value.isNotEmpty()) {
            // HIG 输入框准则：有内容时给清除按钮。密码框已有眼睛开关，
            // 不再叠加清除钮，避免尾部图标挤在一起。
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除")
                }
            }
        } else {
            null
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** 主按钮：iOS 系统蓝填充，48dp 以上高度，medium 圆角（12dp）。 */
// containerColor 默认主色；破坏性主动作（如审批“允许”会执行主机命令）
// 传 error 色，与删除/清除凭据的红字同一套语言，免得危险动作披着主色外衣。
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** 小字脚注：灰色说明文字，带页面左右边距（如版本行）。 */
@Composable
fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** 固定高度空白：用于不是某个组件自身内边距的间隙（如最后一组后的 32dp）。 */
@Composable
fun VerticalSpacer(heightDp: Int = 8) {
    Spacer(modifier = Modifier.height(heightDp.dp))
}

/**
 * 空状态：笔记视频里“插画 + 标题 + 副标题 + 黑色药丸按钮”那套居中空状态，
 * 这里不用插画、按钮保持本应用的系统蓝（不跟视频的黑钮混色），只在有
 * 真实动作时才给按钮（没动作就不给，避免点一下没反应的假按钮）。
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 空状态标题用 titleLarge（22sp 粗体）：以前是 titleMedium（17sp），
        // 在空荡荡的页面中央看着像正文；空状态标题是页面主视觉，应与大标题
        // 语言一致地突出（聊天与会话两处空状态同改）。
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!buttonText.isNullOrBlank() && onButtonClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(text = buttonText, onClick = onButtonClick)
        }
    }
}

/** 居中加载态：主色加载圈 + 标签。加载圈用主色而不是灰色：灰色在“正在加载”时
 * 容易被当成禁用态，主色表示正在进行的操作，和主按钮的系统蓝是一套语言。 */
@Composable
fun LoadingState(label: String = "加载中…") {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ErrorState(message: String) {
    // 报错行也给红色左侧细线：纯文字报错在长列表里容易被当成正文，
    // 一条 4dp 的红线让它一眼就是报错，且不改变文字颜色语义。
    // 报错出现时报读：聊天页报错行、设置/配对页消息已有 liveRegion，
    // 会话页走 ErrorState 的报错还没补，这里一处补齐、三处同口径。
    Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        // 红线撑满整行高：以前只给 min 40dp，多行报错时红线比文字矮一截、
        // 看着像没对齐；Row 高由文字定，线 fillMaxHeight 跟着文字走。
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().heightIn(min = 40.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("出错了", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            // 报错正文可框选：长报错（如带地址的失败信息）用户想复制去反馈时，
            // 不能框选就只能凭记忆重打；标题不包，只包正文。
            SelectionContainer {
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 打字中：三个依次闪烁的小圆点。 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    // 减动效时不启动无限过渡：三个圆点静态全亮，不闪烁。
    if (isReducedMotion()) {
        Row(modifier = modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until TYPING_DOT_COUNT) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
                if (i < TYPING_DOT_COUNT - 1) Spacer(modifier = Modifier.width(4.dp))
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until TYPING_DOT_COUNT) {
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = DOT_PULSE_MS, delayMillis = i * DOT_STAGGER_MS), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
            if (i < TYPING_DOT_COUNT - 1) Spacer(modifier = Modifier.width(4.dp))
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (paired) "已配对" else "未配对",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
