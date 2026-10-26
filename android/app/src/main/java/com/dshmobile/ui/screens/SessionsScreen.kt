package com.dshmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dshmobile.data.DshSession
import com.dshmobile.ui.SessionsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * 会话首页（参考 Codex iOS 首页布局）：大标题 + 按“今天 / 更早”
 * 分组的素色列表，底部是搜索药丸 + 蓝色新建会话按钮。
 * 实时同步：本页可见时每 30 秒轮询一次主机（主机暂无可验证的
 * 推送通道，轮询代替假装的实时推送；Android 官方指引要求减少
 * 频繁唤醒、批量/推迟后台网络工作，15 秒改 30 秒是这个方向，
 * 要立刻看最新就点右上角“刷新”）。点“新会话”建好后直接
 * 进入该会话，不用回列表再点一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onOpenSession: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<DshSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            viewModel.refresh()
        }
    }
    // 新建成功后直接进入该会话（只触发一次，随即清掉标记）。
    val lastCreated = state.lastCreated
    LaunchedEffect(lastCreated) {
        lastCreated?.let {
            onOpenSession(it.id, it.title)
            viewModel.clearCreated()
        }
    }
    val filtered by remember(query, state.sessions) {
        derivedStateOf {
            if (query.isBlank()) state.sessions
            else state.sessions.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
    val today = remember(filtered) {
        val todayDate = LocalDate.now()
        filtered.filter {
            it.updatedAtMs != null &&
                Instant.ofEpochMilli(it.updatedAtMs).atZone(ZoneId.systemDefault()).toLocalDate() == todayDate
        }
    }
    val todayIds = remember(today) { today.map { it.id }.toSet() }
    val earlier = remember(filtered, todayIds) { filtered.filter { it.id !in todayIds } }

    // 重命名改成底部弹层：和换模型弹层同一种材质，不再是居中弹窗；
    // ModalBottomSheet 默认自带拖拽小横条，这里不自己再画一个。
    renameTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { renameTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("重命名会话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { renameTarget = null }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                AppTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = "会话标题",
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "保存",
                    enabled = renameText.isNotBlank(),
                    onClick = { viewModel.rename(target.id, renameText); renameTarget = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { renameTarget = null },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text("取消") }
            }
        }
    }

    // 底部给标准 iOS 标签栏留出空隙，内容不会被它盖住；imePadding 让
    // 键盘弹起时底部的搜索框被顶到键盘上方，而不是被键盘盖住。
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(bottom = 72.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("会话", style = MaterialTheme.typography.displayLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("最近", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            // 视频里列表头会写“全部 N”：这里给真实的会话数，不写假数。
            if (filtered.isNotEmpty()) {
                Text(
                    "${filtered.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            TextButton(onClick = { viewModel.refresh(force = true) }) { Text(if (state.busy) "刷新中…" else "刷新") }
        }
        if (state.busy && state.sessions.isEmpty()) {
            LoadingState()
        }
        state.message?.let { ErrorState(it) }
        if (!state.busy && filtered.isEmpty() && state.message == null) {
            // 截图里会话页底部已经有“新会话”钮，空状态中间再加一个是重复入口，
            // 所以这里不给按钮，文案指回底部那个钮。
            EmptyState(
                title = if (query.isBlank()) "还没有会话" else "没有匹配的会话",
                body = if (query.isBlank()) "点下面的“新会话”，开始和主机智能体对话。" else "换个关键词再试试。",
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            if (today.isNotEmpty()) {
                item {
                    Text("今天", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
                }
                items(today, key = { it.id }, contentType = { "session" }) { session ->
                    SessionRow(
                        session = session,
                        onOpen = { onOpenSession(session.id, session.title) },
                        onRename = { renameTarget = session; renameText = session.title },
                        onDelete = { viewModel.delete(session.id) },
                    )
                }
            }
            if (earlier.isNotEmpty()) {
                item {
                    Text("更早", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
                }
                items(earlier, key = { it.id }, contentType = { "session" }) { session ->
                    SessionRow(
                        session = session,
                        onOpen = { onOpenSession(session.id, session.title) },
                        onRename = { renameTarget = session; renameText = session.title },
                        onDelete = { viewModel.delete(session.id) },
                    )
                }
            }
        }
        // 底部：搜索药丸 + 蓝色新建按钮（Codex 首页底部样式）。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text("搜索会话", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清除搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp).clickable { query = "" }.padding(2.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 笔记视频搜索页的“取消”：有关键词时才出现，点一下清空搜索。
            if (query.isNotEmpty()) {
                TextButton(onClick = { query = "" }) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Button(
                onClick = { viewModel.create("") },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("新会话", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 会话行：左滑删除，点“重命名”改名，点行进入对话。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: DshSession,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // iOS 式：左滑只露出删除按钮；删除是不可逆的，按 HIG 要先确认再执行，
    // 所以点删除先弹确认框（红字删除 + 取消），确认后才真正删。
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { false })
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除会话？") },
            text = { Text("删除后无法恢复，确定要删除“${session.title}”吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    scope.launch { dismissState.reset() }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(modifier = Modifier.fillMaxSize().padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = session.model ?: "点击进入对话",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    session.updatedAtMs?.let { updated ->
                        Text(
                            text = "更新于 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(updated)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "重命名",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 小字按钮也要 48dp 触控高度：labelMedium 行高 18dp，
                    // 上下各 15dp 内边距正好 48dp，之前只有 8dp、很难点中。
                    modifier = Modifier.clickable { onRename() }.padding(horizontal = 8.dp, vertical = 15.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}
