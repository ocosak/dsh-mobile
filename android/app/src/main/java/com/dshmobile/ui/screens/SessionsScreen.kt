package com.dshmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dshmobile.ui.SessionsViewModel

/**
 * 会话页：素色历史列表，不套卡片；左滑删除，底部细分割线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onOpenSession: (String, String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        LargeTitle("会话", subtitle = "与主机智能体的对话记录")
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            AppTextField(value = newTitle, onValueChange = { newTitle = it }, placeholder = "新会话标题")
            PrimaryButton(
                text = "新建会话",
                onClick = { viewModel.create(newTitle); newTitle = "" },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Text(
            text = "左滑会话可删除",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        if (state.busy && state.sessions.isEmpty()) {
            LoadingState()
        }
        state.message?.let { ErrorState(it) }
        if (!state.busy && state.sessions.isEmpty() && state.message == null) {
            EmptyState(
                title = "还没有会话",
                body = "新建一个会话，开始和主机智能体对话。",
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
            items(state.sessions, key = { it.id }) { session ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.delete(session.id); true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(modifier = Modifier.fillMaxSize().padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                            Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    },
                ) {
                    Column(modifier = Modifier.fillMaxWidth().clickable { onOpenSession(session.id, session.title) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = session.model ?: "点击进入对话",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
