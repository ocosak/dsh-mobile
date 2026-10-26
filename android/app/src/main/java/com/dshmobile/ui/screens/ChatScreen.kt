package com.dshmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dshmobile.data.ChatMessage
import com.dshmobile.data.ToolCall
import com.dshmobile.ui.ChatViewModel

/** Tool-call status in Chinese (the host only sends the enum name). */
private fun toolStatusText(status: ToolCall.Status): String = when (status) {
    ToolCall.Status.PENDING -> "待处理"
    ToolCall.Status.APPROVED -> "已批准"
    ToolCall.Status.DENIED -> "已拒绝"
    ToolCall.Status.RUNNING -> "进行中"
    ToolCall.Status.DONE -> "已完成"
    ToolCall.Status.FAILED -> "失败"
}

private data class MdBlock(val code: Boolean, val text: String)

private fun splitMdBlocks(text: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val current = StringBuilder()
    var inCode = false
    for (line in text.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (current.isNotEmpty()) {
                out += MdBlock(inCode, current.toString().trimEnd('\n'))
                current.clear()
            }
            inCode = !inCode
        } else {
            current.append(line).append('\n')
        }
    }
    if (current.isNotEmpty()) out += MdBlock(inCode, current.toString())
    return out
}

/** Only **bold** and `code` are interpreted; unmatched markers stay literal, never dropped. */
private fun inlineAnnotated(text: String, codeBg: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    builder.append("**")
                    i += 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    builder.append('`')
                    i++
                }
            }
            else -> {
                builder.append(text[i])
                i++
            }
        }
    }
    return builder.toAnnotatedString()
}

/** Minimal markdown for assistant replies: fences, headers, bullets, bold, inline code. */
@Composable
private fun AssistantMessageText(text: String) {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    Column {
        for (block in splitMdBlocks(text)) {
            if (block.code) {
                Surface(
                    modifier = Modifier.padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = codeBg,
                ) {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                for (line in block.text.lines()) {
                    when {
                        line.startsWith("# ") -> Text(inlineAnnotated(line.removePrefix("# "), codeBg), style = MaterialTheme.typography.titleLarge)
                        line.startsWith("## ") -> Text(inlineAnnotated(line.removePrefix("## "), codeBg), style = MaterialTheme.typography.titleMedium)
                        line.startsWith("### ") -> Text(inlineAnnotated(line.removePrefix("### "), codeBg), style = MaterialTheme.typography.titleMedium)
                        line.startsWith("- ") || line.startsWith("* ") -> Row {
                            Text("•  ", style = MaterialTheme.typography.bodyLarge)
                            Text(inlineAnnotated(line.substring(2), codeBg), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                        else -> Text(inlineAnnotated(line, codeBg), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/**
 * 对话页（标准 iOS + 新视频的聊天语言）：顶部单行栏（返回 + 居中会话标题）、
 * 用户消息系统蓝气泡、AI 回复纯文本不套气泡；生成中先显示“思考中…”轮换状态，
 * 工具调用收成一行摘要（点开看详情，名字和状态都来自主机实发数据）；历史多时
 * 先只看最近几条，点“查看更早的消息”再展开；底部药丸输入框里带模型药丸。
 */
@Composable
private fun ToolCallsSummary(toolCalls: List<ToolCall>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    if (toolCalls.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "已用 ${toolCalls.size} 个工具 · ${toolCalls.first().name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            toolCalls.forEach { tc ->
                Surface(
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 300.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("工具：${tc.name}", style = MaterialTheme.typography.labelLarge)
                        Text(toolStatusText(tc.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    sessionTitle: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onModelChange: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var input by remember(sessionId) { mutableStateOf("") }
    // 发送失败（传输中断）时把原文本还回输入框，免得重打；只认本会话的，
    // 别会话的失败不串到这里（切回原会话时再恢复）。
    val failedDraft = state.failedDraft
    LaunchedEffect(failedDraft) {
        if (failedDraft != null && failedDraft.first == sessionId) {
            input = failedDraft.second
            viewModel.clearFailedDraft()
        }
    }
    // 按会话区分：历史消息 id 是 h-序号，各会话会重复，直接 remember 会串。
    var copiedId by remember(sessionId) { mutableStateOf<String?>(null) }
    var showModelDialog by remember { mutableStateOf(false) }
    var modelDraft by remember { mutableStateOf("") }
    var showAll by remember(sessionId) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // 打开会话即加载历史（桌面端行为）；切会话时重新加载。
    LaunchedEffect(sessionId) { viewModel.loadHistory(sessionId) }

    // 历史折叠：超过 8 条时先只看最近 6 条，和 Codex 的“更早消息”一致。
    val visibleMessages = remember(state.messages, showAll) {
        if (showAll || state.messages.size <= 8) state.messages
        else state.messages.takeLast(6)
    }
    val collapsedCount = state.messages.size - visibleMessages.size

    // 首次加载历史后定位到底部（桌面端打开会话的行为）；之后有新消息
    // 且用户本来就在底部时，跟随到底部；用户往上翻时不抢滚动。
    var initialScrollDone by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(visibleMessages.size, showAll) {
        if (!initialScrollDone && visibleMessages.isNotEmpty()) {
            val expected = visibleMessages.size + if (collapsedCount > 0) 1 else 0
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= expected }
            listState.scrollToItem(expected - 1)
            initialScrollDone = true
        } else {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0 && visibleMessages.isNotEmpty()) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                if (lastVisible >= total - 2) listState.scrollToItem(total - 1)
            }
        }
    }

    // 换模型是底部弹层：Material 3 的 ModalBottomSheet 默认自带拖拽小横条，
    // 这里不再自己再画一个，否则会出现上下两个横条。右上角 X + 保存/取消保留。
    if (showModelDialog) {
        ModalBottomSheet(
            onDismissRequest = { showModelDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("切换模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showModelDialog = false }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                AppTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    placeholder = "模型名（留空用主机默认）",
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "保存",
                    onClick = { onModelChange(modelDraft); showModelDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { showModelDialog = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text("取消") }
            }
        }
    }

    // 标签栏在聊天详情页按 iOS 规范隐藏，这里不再给它留 96dp 空隙。
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // 新视频的顶部单行栏：左返回、中间居中会话标题、右 48dp 占位保居中；
        // 模型选择改到下面的输入框里（新视频里模型药丸就在输入区）。
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "返回")
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    sessionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.size(48.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (collapsedCount > 0) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "查看更早的 $collapsedCount 条消息 ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // 同上：小字链接也要 48dp 触控高度（行高 18dp + 上下 15dp）。
                            modifier = Modifier.clickable { showAll = true }.padding(horizontal = 8.dp, vertical = 15.dp),
                        )
                    }
                }
            }
            itemsIndexed(visibleMessages, key = { _, m -> m.id }, contentType = { _, m -> m.role }) { index, msg ->
                val isUser = msg.role == ChatMessage.Role.USER
                val groupedWithPrevious = index > 0 && visibleMessages[index - 1].role == msg.role
                val topPadding = if (groupedWithPrevious) 4.dp else 12.dp
                if (isUser) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 2.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 300.dp),
                            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 2.dp)) {
                        if (msg.text.isEmpty()) {
                            // 生成中且正文未到：视频里“思考中…”那行（状态词轮换）。
                            if (state.streaming) ThinkingStatus() else Text("")
                        } else {
                            // 工具痕迹放正文上方，像视频里“搜索网页 …”那行，点开看详情。
                            ToolCallsSummary(msg.toolCalls)
                            AssistantMessageText(msg.text)
                            Text(
                                text = if (copiedId == msg.id) "已复制" else "复制",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                // 小字按钮也要 48dp 触控高度：labelMedium 行高 18dp，
                                // 上下各 15dp 内边距正好 48dp，之前只有 4dp、很难点中。
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(msg.text))
                                    copiedId = msg.id
                                }.padding(vertical = 15.dp),
                            )
                        }
                    }
                }
            }
            if (state.messages.isEmpty()) {
                item {
                    EmptyState(
                        title = "今天想做什么？",
                        body = "在下方输入消息，主机智能体会在这里回复，工具调用会以摘要行展示，点开看详情。",
                    )
                }
            }
        }
        // 审批：底部卡片，不是系统弹窗。
        state.pendingApproval?.let { approval ->
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    // 审批卡是固定在输入框上方的卡片，不能拖拽，所以不画拖拽小横条
                    // （小横条只属于可拖拽的底部弹层，画在这里会误导人去拖它）。
                    Text("需要审批", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text("工具：${approval.toolName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(approval.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        PrimaryButton(text = "允许", onClick = { viewModel.answerApproval(sessionId, true) }, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.answerApproval(sessionId, false) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("拒绝") }
                    }
                    Text(
                        "允许后主机会执行这项操作（可能读写文件或运行命令）。不认识就点拒绝。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        // 报错时给重试：历史加载失败、发送失败都只是一行字、没地方重试，
        // 用户之前只能退出重进；消息为空时（历史没加载出来）才给重试钮。
        state.message?.let {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                if (state.messages.isEmpty()) {
                    TextButton(onClick = { viewModel.loadHistory(sessionId) }) { Text("重试") }
                }
            }
        }
        // 空会话时的快捷药丸：视频里输入框上方那排，点一下只填入输入框，不假装已执行。
        if (state.messages.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                QuickActionPill(icon = Icons.Filled.Code, text = "解释代码", onClick = { input = "帮我解释这段代码" })
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionPill(icon = Icons.Filled.Edit, text = "写个脚本", onClick = { input = "帮我写一个脚本" })
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionPill(icon = Icons.Filled.Description, text = "总结内容", onClick = { input = "帮我总结这段内容" })
            }
        }
        // Kimi 式药丸输入框：白底 + 细边线，+ 附件、多行输入、系统蓝圆形发送键。
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.attachNote(sessionId, "attachment") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "添加附件", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    // 注意：这里刻意不给键盘设 ImeAction.Send。ImeAction.Send 虽然是
                    // 给聊天应用用的，但它会把回车键变成发送键；本输入框是多行
                    // （maxLines = 5），用户还要靠回车换行写多行提示，所以回车
                    // 保持换行，发送只走右边的蓝色发送钮，和 ChatGPT 类应用一致。
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            Box {
                                if (input.isEmpty()) {
                                    // 生成中时占位换成“正在回复…”，和视频里输入区的状态一致。
                                    Text(
                                        if (state.streaming) "正在回复…" else "尽管问，做个任务…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                // 新视频的输入区模型药丸：点开换模型，之前在顶部的药丸改到这里。
                CompactModelPill(
                    text = if (state.model.isBlank()) "默认模型" else state.model,
                    onClick = { modelDraft = state.model; showModelDialog = true },
                )
                val canSend = input.isNotBlank() && !state.streaming
                val showStop = state.streaming
                IconButton(
                    onClick = {
                        if (showStop) viewModel.stopGenerating()
                        else if (input.isNotBlank()) { viewModel.send(sessionId, input); input = "" }
                    },
                    enabled = canSend || showStop,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(if (canSend || showStop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (showStop) Icons.Filled.Stop else Icons.Filled.Send,
                            contentDescription = if (showStop) "停止生成" else "发送",
                            tint = if (canSend || showStop) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
