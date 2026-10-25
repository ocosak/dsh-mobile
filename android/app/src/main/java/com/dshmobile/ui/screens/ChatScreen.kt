package com.dshmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dshmobile.data.ChatMessage
import com.dshmobile.ui.ChatViewModel

/**
 * 聊天页（ChatGPT iOS 版式）：白画布、用户右侧浅灰气泡、
 * AI 左侧纯文本 + 头像、底部悬浮圆角输入框、黑色圆形发送键。
 * 模型不在这里改，去设置里改；顶部只显示当前模型名。
 */
@Composable
fun ChatScreen(
    sessionId: String,
    sessionTitle: String,
    viewModel: ChatViewModel,
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 有新消息且用户本来就在底部时，跟随到底部；用户往上翻时不抢滚动。
    LaunchedEffect(state.messages.size) {
        val lastIndex = state.messages.size - 1
        if (lastIndex >= 0) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible >= lastIndex - 1) listState.scrollToItem(lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Text(
            text = sessionTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        Text(
            text = if (state.model.isBlank()) "默认模型" else state.model,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            itemsIndexed(state.messages, key = { _, m -> m.id }) { index, msg ->
                val isUser = msg.role == ChatMessage.Role.USER
                val groupedWithPrevious = index > 0 && state.messages[index - 1].role == msg.role
                val topPadding = if (groupedWithPrevious) 4.dp else 12.dp
                if (isUser) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 2.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 2.dp)) {
                        AssistantAvatar()
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (msg.text.isEmpty()) {
                                if (state.streaming) TypingIndicator() else Text("")
                            } else {
                                Text(text = msg.text, style = MaterialTheme.typography.bodyLarge)
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
                                        Text(tc.status.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.messages.isEmpty()) {
                item {
                    EmptyState(
                        title = "开始对话",
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
        // 悬浮圆角输入框：+ 附件、多行输入、黑色圆形发送键。
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
                                    Text("给智能体发消息…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                val canSend = input.isNotBlank() && !state.streaming
                IconButton(
                    onClick = { viewModel.send(sessionId, input); input = "" },
                    enabled = canSend,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "发送",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
