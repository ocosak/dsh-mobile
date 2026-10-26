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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
 * 对话页（标准 iOS + 视频里的 Kimi 顶部药丸）：左上角返回、居中模型药丸、
 * 用户消息系统蓝气泡、AI 回复纯文本不套气泡；历史多时先只看最近
 * 几条，点“查看更早的消息”再展开；底部药丸输入框。
 */
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

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("切换模型") },
            text = {
                AppTextField(value = modelDraft, onValueChange = { modelDraft = it }, placeholder = "模型名（留空用主机默认）")
            },
            confirmButton = {
                TextButton(onClick = { onModelChange(modelDraft); showModelDialog = false }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("取消") }
            },
        )
    }

    // 标签栏在聊天详情页按 iOS 规范隐藏，这里不再给它留 96dp 空隙。
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "返回")
            }
            // 居中模型药丸：视频顶部“K3.1 Pro 极速”那种选择器，点开换模型。
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ModelPill(
                    text = if (state.streaming) "生成中…" else if (state.model.isBlank()) "默认模型" else state.model,
                    onClick = { modelDraft = state.model; showModelDialog = true },
                )
            }
            // 右侧留一个 44dp 占位，让药丸真正居中。
            Spacer(modifier = Modifier.size(44.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sessionTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            modifier = Modifier.clickable { showAll = true }.padding(8.dp),
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
                            if (state.streaming) TypingIndicator() else Text("")
                        } else {
                            AssistantMessageText(msg.text)
                            Text(
                                text = if (copiedId == msg.id) "已复制" else "复制",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(msg.text))
                                    copiedId = msg.id
                                }.padding(vertical = 4.dp),
                            )
                        }
                        msg.toolCalls.forEach { tc ->
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
            if (state.messages.isEmpty()) {
                item {
                    EmptyState(
                        title = "今天想做什么？",
                        body = "在下方输入消息，主机智能体会在这里回复，工具调用会以卡片形式展示。",
                    )
                }
            }
        }
        // 审批：底部卡片，不是系统弹窗。
        state.pendingApproval?.let { approval ->
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(modifier = Modifier.size(width = 60.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
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
        state.message?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
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
                IconButton(onClick = { viewModel.attachNote(sessionId, "attachment") }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "添加附件", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            Box {
                                if (input.isEmpty()) {
                                    Text("尽管问，做个任务…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                val canSend = input.isNotBlank() && !state.streaming
                val showStop = state.streaming
                IconButton(
                    onClick = {
                        if (showStop) viewModel.stopGenerating()
                        else if (input.isNotBlank()) { viewModel.send(sessionId, input); input = "" }
                    },
                    enabled = canSend || showStop,
                    modifier = Modifier.size(44.dp),
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
