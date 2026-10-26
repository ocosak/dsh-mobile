package com.dshmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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

// 日期只写月-日 时:分，不写年：会话都是近期的，年是噪声；今天组与更早组都用同一格式。
private val SessionDateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private val SessionTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val SessionYearFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** 会话行时间：今天组只显示时:分（组头已说明是今天，重复月日是噪音）；
 * 更早组同年显示月-日 时:分、跨年补年份（去年会话不写年会与今年同月同日混淆）。 */
private fun formatSessionUpdated(ms: Long, isToday: Boolean, todayDate: LocalDate): String {
    if (isToday) return SessionTimeFormat.format(Date(ms))
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (date.year == todayDate.year) SessionDateFormat.format(Date(ms)) else SessionYearFormat.format(Date(ms))
}

private const val POLL_INTERVAL_MS = 30000L
// 搜索无结果回显截断字数：回显只取 gist、完整词在搜索框里；与文件内其余调参
// （轮询间隔等）同款具名常量，不内联数字。
private const val SEARCH_ECHO_MAX_CHARS = 50

// 今天/更早组头是标题：给 heading 语义，TalkBack 可按标题导航。
private val GroupHeaderModifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp).semantics { heading() }

/**
 * 会话首页（参考 Codex iOS 首页布局）：大标题 + 按“今天 / 更早”
 * 分组的素色列表，底部是搜索药丸 + 蓝色新建会话按钮。
 * 实时同步：本页可见时每 30 秒轮询一次主机（主机暂无可验证的
 * 推送通道，轮询代替假装的实时推送；Android 官方指引要求减少
 * 频繁唤醒、批量/推迟后台网络工作，15 秒改 30 秒是这个方向，
 * 要立刻看最新就点“最近”行右侧的“刷新”）。点“新会话”建好后直接
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
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    // 重命名目标改存 id（String 可进 Saveable）：以前存整个 DshSession 对象，
    // 转屏时弹层直接消失；现在 id 存得住，弹层按 id 从列表里取回目标。
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    // 按 id 取目标只在 id 或列表变时算：以前每次重组（重命名框里每打一字）
    // 都全表扫一遍，列表长了也是白算。
    val renameTarget = remember(renameTargetId, state.sessions) { state.sessions.firstOrNull { it.id == renameTargetId } }
    // 重命名输入框用 rememberSaveable：转屏不丢已打的字。
    var renameText by rememberSaveable { mutableStateOf("") }
    // 前台才轮询：应用退后台后组合还在（Activity 只 stop、不销毁），30 秒
    // 循环若不管前后台、会在后台继续发请求（后台网络唤醒，与本页注释引用
    // 的官方指引相悖）。不用 lifecycle-runtime-compose（未声明、沙箱无法
    // 验证新依赖）：lifecycle-runtime-ktx 已声明，其 api 链透出 Lifecycle /
    // LifecycleEventObserver，直接挂 Activity 的 observer 即可，不加依赖。
    // resumed 初值按挂载时真实状态定（进程恢复时可能在后台创建组合）；
    // observer 只在前后台切换时触发，不与进入页面的立即刷新重复（挂载时
    // 不触发事件，所以不会和下面的立即刷新双发；回前台那次刷新若撞上
    // 进行中的轮询，refresh 的 busy 门会挡住，也不会双发）。
    // resumed 用普通 remember（不用 Saveable）：转屏发生在前台，挂载时状态
    // 本就是 RESUMED，重建后 observer 重新按真实状态定一次，不会错。
    val context = LocalContext.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        // object 形式不用 SAM 转换：LifecycleEventObserver 是否为 fun interface
        // 在不同版本有出入，object 写法两种情况都能编过。
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        resumed = true
                        // 回前台立刻刷一次：光靠循环门要等下一个 30 秒周期，
                        // 列表会 stale 一段时间。
                        viewModel.refresh()
                    }
                    Lifecycle.Event.ON_PAUSE -> resumed = false
                    else -> Unit
                }
            }
        }
        resumed = activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    // 立即刷新一次，之后每 30 秒轮询一次（仅前台；后台循环只空转 delay，
    // 不发请求）；合并成一个 effect，两个同键 effect 易让人误以为是同一个。
    LaunchedEffect(Unit) {
        viewModel.refresh()
        while (true) {
            delay(POLL_INTERVAL_MS)
            if (resumed) viewModel.refresh()
        }
    }
    // 新建成功后直接进入该会话（只触发一次，随即清掉标记；空 id 不导航，见 ViewModel）。
    val lastCreated = state.lastCreated
    LaunchedEffect(lastCreated) {
        lastCreated?.let {
            onOpenSession(it.id, it.title)
            viewModel.clearCreated()
        }
    }
    // 这里原来是 remember 包着 derivedStateOf：remember 的键已经是
    // query 和 sessions，derivedStateOf 的缓存是多余的一层，直接用
    // remember 算过滤结果，行为不变、少一层状态对象。
    val filtered = remember(query, state.sessions) {
        // 搜索关键词去首尾空格再比：用户手滑多打了空格，搜索就不该
        // 因为空格对不上而返回空结果。
        val q = query.trim()
        val base = if (q.isBlank()) state.sessions
        // 搜索同时匹配标题与模型：行里本来就显示模型名，只搜标题时按模型名
        // 搜会返回空，用户会以为这批会话不存在。
        else state.sessions.filter { it.title.contains(q, ignoreCase = true) || (it.model?.contains(q, ignoreCase = true) == true) }
        // 表头叫“最近”，列表就该最新在前：主机顺序未必如此，按更新时间
        // 倒序排；无时间戳的沉底，稳定排序保原有相对顺序（无固定字段，
        // 不猜测置顶之类主机没有的语义）。
        base.sortedWith(compareByDescending { it.updatedAtMs ?: Long.MIN_VALUE })
    }
    // 时区显式写出：下面 updatedAtMs 转日期用的就是 systemDefault，
    // 今天判定若用隐式默认时区，两处时区来源写法不一致、后人易改偏。
    val todayDate = LocalDate.now(ZoneId.systemDefault())
    // 无时间戳的会话进不了“今天”（无法证明是今天），统一归到“更早”。
    val today = remember(filtered, todayDate) {
        filtered.filter {
            it.updatedAtMs != null &&
                Instant.ofEpochMilli(it.updatedAtMs).atZone(ZoneId.systemDefault()).toLocalDate() == todayDate
        }
    }
    // 今天组的 id 集合，供更早组排除（更早 = 全部减今天）。
    val todayIds = remember(today) { today.map { it.id }.toSet() }
    val earlier = remember(filtered, todayIds) { filtered.filter { it.id !in todayIds } }

    // 重命名改成底部弹层：和换模型弹层同一种材质，不再是居中弹窗；
    // ModalBottomSheet 默认自带拖拽小横条，这里不自己再画一个。
    // 目标在列表刷新后找不到了（如刚被删），弹层自然不出现，不弹空表。
    renameTarget?.let { target ->
        ModalBottomSheet(
            // 圆角与对话框同理：M3 弹层默认顶角 28dp，本应用卡片语言是 12dp，
            // 弹层顶角同样给 medium，免得一张卡片两种圆角。
            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { renameTargetId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = SheetContentModifier) {
                SheetHeader(title = "重命名会话", onClose = { renameTargetId = null })
                AppTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = "会话标题",
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "保存",
                    enabled = renameText.isNotBlank() && renameText.trim() != target.title,
                    onClick = { viewModel.rename(target.id, renameText); renameTargetId = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { renameTargetId = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("取消") }
            }
        }
    }

    // 底部给标准 iOS 标签栏留出空隙（tabBarClearance，按字号算，不是固定 dp），
    // 内容不会被它盖住；imePadding 让键盘弹起时底部的搜索框被顶到键盘上方，
    // 而不是被键盘盖住。
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(bottom = tabBarClearance())) {
        Row(
            // 顶边距与 LargeTitle 对齐（16dp）：设置/配对页标题顶是 16，会话页手写
            // 标题顶是 12，切标签时标题会上下跳 4dp；标题样式本就是同一个 displayLarge，
            // 顶距也应一致（底边距仍 8 + 最近行顶 16，标题到内容 24 不变）。
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 页面主标题同样给 heading 语义：设置/配对页用 LargeTitle（自带 heading）、
            // 聊天页顶栏标题已补（R269），会话页这个手写标题漏了——TalkBack 按标题
            // 导航时会跳过会话页主标题。
            Text("会话", style = MaterialTheme.typography.displayLarge, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 搜关键词时这行不再是“最近”，而是搜索结果；标题跟着状态变，
            // 不然用户会以为下面还是按时间排的最近列表。
            // 列表分区标题同样是标题：今天/更早组头已有 heading（GroupHeaderModifier），
            // 这行分区标题漏了；层级是 会话（页）→ 最近/搜索结果（区）→ 今天/更早（组）。
            Text(if (query.isBlank()) "最近" else "搜索结果", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            // 视频里列表头会写“全部 N”：这里给真实的会话数，不写假数。
            if (filtered.isNotEmpty()) {
                Text(
                    "${filtered.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            // 刷新钮不禁 busy：ViewModel 的 force 本就是“用户手动要最新数据、
            // 绕过 busy 守卫”，钮一禁、force 路就永远点不到，自相矛盾；
            // 文案仍按 busy 显示“刷新中…”（点它就是再刷一次）。
            TextButton(onClick = { viewModel.refresh(force = true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.busy) "刷新中…" else "刷新") }
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
                // 搜索词原样回显会撑爆空状态：粘贴一段长文本当关键词时，
                // 正文会跟着拉长；截断到 50 字 + 省略号，完整词仍在上方搜索框
                // 里可见（单行框可横滑查看），回显只取个 gist。
                body = if (query.isBlank()) "点下面的“新会话”，开始和主机智能体对话。" else {
                    val q = query.trim()
                    val shown = if (q.length > SEARCH_ECHO_MAX_CHARS) q.take(SEARCH_ECHO_MAX_CHARS) + "…" else q
                    "没有找到“$shown”的会话，换个关键词再试试。"
                },
            )
        }
        // 搜关键词后列表变短了，滚动位置还停在原来那么深的地方的话，
        // 用户看到的会是过滤后列表的底部、而不是从头看；关键词一变就回顶。
        // 但只认“变化”不认“恢复”：转屏重组时效果也会重跑，若无条件回顶，
        // 会把 LazyListState 恢复好的阅读位置一把拉回顶部；上次关键词记在
        // 普通 remember 里（转屏丢掉、首跑跳过），只有真变化时才回顶。
        var prevQuery by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(query) {
            // 空列表时不调 scrollToItem(0)：搜不到结果时列表是空的，往空列表
            // 的第 0 项滚是否安全未在真机验证（无真机验证记录），加门最稳。
            // 门改用 filtered（组合数据）而不用 layoutInfo 总数：布局信息滞后于
            // 组合（聊天页跟随分支同款问题），关键词刚改、布局还没追上时旧总数
            // 仍 > 0、门会被旧值骗过；filtered 在效果运行时已是新值，空与非空
            // 判断不受布局滞后影响；非空时第 0 项一定存在，滚过去安全。
            if (prevQuery != null && prevQuery != query && filtered.isNotEmpty()) listState.scrollToItem(0)
            prevQuery = query
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            // 今天/更早两组原来是两段复制粘贴（行回调一字不差）：合成一个循环，
            // 组名与列表成对给，改行回调只改一处；item 键与 contentType 不变。
            for ((header, list) in listOf("今天" to today, "更早" to earlier)) {
                if (list.isNotEmpty()) {
                    item {
                        Text(header, style = MaterialTheme.typography.titleMedium, modifier = GroupHeaderModifier)
                    }
                    items(list, key = { it.id }, contentType = { "session" }) { session ->
                        SessionRow(
                            session = session,
                            isToday = header == "今天",
                            todayDate = todayDate,
                            isDeleting = session.id in state.deletingIds,
                            isRenaming = session.id in state.renamingIds,
                            onOpen = { onOpenSession(session.id, session.title) },
                            onRename = { renameTargetId = session.id; renameText = session.title },
                            onDelete = { viewModel.delete(session.id) },
                        )
                    }
                }
            }
        }
        // 底部：搜索药丸 + 蓝色新建按钮（Codex 首页底部样式）。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Surface 的默认内容色是灰（onSurfaceVariant），但搜索框子项颜色都已显式指定
            // （图标灰、文字黑、占位灰），不依赖继承；改子项颜色时不要删掉显式色。
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
                // 边框与聊天输入框、各类药丸一致：以前没边框，深色下搜索底
                // （#1C1C1E）在页面黑底上几乎看不出边界，只有图标和占位浮着；
                // 搜索框是输入控件（AppTextField 都有边框），不应比它更弱。
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                // 整颗药丸都可点聚焦：放大镜以前在输入框外面（装饰性图标不接
                // 触摸），点放大镜与两侧 12dp 内边距都聚焦不了；图标挪进
                // decorationBox（前置图标）、内边距挪到输入框自身修饰符上，
                // 整颗药丸的触摸都落在输入框 bounds 内、点哪都聚焦；高度
                // 仍是 13 + 22 + 13 = 48dp；无障碍描述与占位处理不变。
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp).semantics { contentDescription = "搜索会话" },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    // 光标显式给系统蓝：与聊天输入框同理，默认光标色在深色下可能不可见。
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    // 搜索框键盘给“搜索”键：点它收起键盘、留在结果上，
                    // 而不是让回车无事可做、键盘一直盖着列表。
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    decorationBox = { innerTextField ->
                        // Row 显式 fillMaxWidth：目前靠 Box(weight 1f) 把 Row 撑满，
                        // 隐含依赖 weight 的撑满行为；后人若简化掉 weight，Row 会
                        // 缩成内容宽、药丸右侧空一截；显式写满，意图不依赖隐含行为。
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isBlank()) {
                                    // 占位与输入框的 contentDescription 是同一个词（“搜索会话”）：
                                    // 占位若留在无障碍树里，TalkBack 会把同一个词连读两遍
                                    // （先读输入框描述、再读占位文本）；占位只是视觉提示，
                                    // 从无障碍树里拿掉，输入框本身的描述已足够。
                                    Text("搜索会话", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
                                }
                                innerTextField()
                            }
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 搜索框里原来还有个 18dp 的小叉号，和后面的“取消”钮是重复的
            // 清除入口（小叉号只有 22dp 触控高度，很难点中）；只留“取消”。
            // 笔记视频搜索页的“取消”：有关键词时才出现，点一下清空搜索
            // 并收起键盘（只清文字不收键盘，用户还得再点一下空白处）。
            // 取消钮只在真有关键词时出现：全空格时过滤已按空白处理，
            // 但 isNotEmpty 会让取消钮还挂着，点它等于白点一次。
            // 取消钮后不再补 Spacer：行尾已有 16dp 行边距，再补 8dp 右边就比
            // 左边多出 8dp、不对称；取消与药丸之间的间隔由前面的 Spacer 给。
            if (query.isNotBlank()) {
                TextButton(onClick = { query = ""; focusManager.clearFocus() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Button(
                onClick = { viewModel.create("") },
                enabled = !state.creating,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.creating) "新建中…" else "新会话", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 会话行：左滑删除，点“重命名”改名，点行进入对话。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: DshSession,
    isToday: Boolean,
    todayDate: LocalDate,
    isDeleting: Boolean = false,
    isRenaming: Boolean = false,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // iOS 式：左滑只露出删除按钮；删除是不可逆的，按 HIG 要先确认再执行，
    // 所以点删除先弹确认框（红字删除 + 取消），确认后才真正删。
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { false })
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 空白标题兜底：主机回包标题为空时行里不留空行、删除确认框里不出现
    // “删除“”吗？”这种空名；兜底与 parseSessions 的解析兜底同名
    // （“未命名会话”），不要另起一个 fallback——两处同义不同名，用户会在
    // 列表与别处看到同一会话的两个名字。创建时空白标题是“新会话”，那是
    // 发送侧的默认值，与这里的显示兜底场景不同；聊天顶栏的“会话”只是
    // 导航缺省，同样不混用。
    val displayTitle = session.title.ifBlank { "未命名会话" }
    if (showDeleteConfirm) {
        AlertDialog(
            // 点框外关闭也复位左滑状态：否则行还保持左滑展开、露出删除钮，
            // 用户会以为删除流程还挂着；取消钮同理（见下）。
            // 圆角显式给 medium（12dp）：M3 对话框默认是 extraLarge（28dp），
            // 与本应用卡片（12dp）与 iOS 弹窗（约 13dp）都不对，太圆像药丸。
            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { showDeleteConfirm = false; scope.launch { dismissState.reset() } },
            // M3 对话框默认标题是 headlineSmall（24sp），与 iOS 17 号半粗体对不上；
            // 标题与正文都显式指定，免得对话框字阶跑出整套 iOS 字阶。
            title = { Text("删除会话？", style = MaterialTheme.typography.titleMedium) },
            text = { Text("删除后无法恢复，确定要删除“${displayTitle}”吗？", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    scope.launch { dismissState.reset() }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; scope.launch { dismissState.reset() } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }
            },
        )
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(modifier = Modifier.fillMaxSize().padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                // 删除进行中时左滑删除钮不再弹确认框：行上已有“删除中…”反馈，
                // 再弹框、再确认只会多发一次重复请求（ViewModel 层也拦，但行上
                // 不应再给这个入口）。
                TextButton(onClick = { if (!isDeleting && !isRenaming) showDeleteConfirm = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        // 左滑删除对 TalkBack 用户不可用：补一个“删除”自定义动作，点它弹出
        // 同一个删除确认框、走同一条删除路，不另起流程；重命名已有可见小字钮，
        // 不需要再补动作。删除进行中时动作直接拿掉（不给重复入口），点行进对话
        // 也拦住：正在删的会话打开聊天页只会拉到 404，没意义。
        Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { if (!isDeleting) onOpen() }.semantics {
            customActions = if (isDeleting || isRenaming) emptyList() else listOf(CustomAccessibilityAction("删除") { showDeleteConfirm = true; true })
        }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // 删除/重命名进行中时标题变灰：对话框已关、请求还在跑，行上
                    // 若无任何变化、用户会以为刚才的确认没生效；变灰 + 右侧文案
                    // 一起表示“这行正在处理”，刷新回来自然恢复。
                    Text(displayTitle, style = MaterialTheme.typography.bodyLarge, color = if (isDeleting || isRenaming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        // 模型名只判 null 不够：主机回空串时行里会留一行 18dp 的空白，
                        // 看着像缺了一行；空白与 null 同按缺省处理。
                        text = session.model?.takeIf { it.isNotBlank() } ?: "点击进入对话",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 日期格式化只在时间戳变时算：以前每次重组（轮询、滚动复用）
                    // 都重新 format；remember 起来，key 变才重算。注意 remember
                    // 必须无条件调用（不能放在 let 里），否则时间戳由空变有时
                    // 调用次数会变、组合会崩。key 里补 todayDate 与 isToday：跨午夜后
                    // 今天组变昨天组、格式算法跟着变，只 key 时间戳的话午夜后仍按
                    // 旧算法显示（今天组的时间格式在午夜后不刷新）。
                    val updatedText = remember(session.updatedAtMs, isToday, todayDate) {
                        session.updatedAtMs?.let { "更新于 ${formatSessionUpdated(it, isToday, todayDate)}" }
                    }
                    updatedText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // 副标题行（模型/提示）已有 top 4dp，日期行与它一致。
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // 进行中时这个槽位改显示状态文案、不可点：删除中点“重命名”会给
                // 一个正在消失的会话改名，重命名中再点会多发一次请求；状态文案
                // 与标题变灰一起构成行上的进行中反馈（删除/重命名确认后对话框
                // 已关，以前行上完全没有反馈）。
                if (isDeleting) {
                    Text(
                        text = "删除中…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.then(TextButtonTouchModifier),
                    )
                } else if (isRenaming) {
                    Text(
                        text = "重命名中…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.then(TextButtonTouchModifier),
                    )
                } else {
                    Text(
                        text = "重命名",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 小字按钮也要 48dp 触控高度：labelMedium 行高 18dp，
                        // 上下各 15dp 内边距正好 48dp，之前只有 8dp、很难点中。
                        modifier = Modifier.clickable(role = Role.Button) { onRename() }.then(TextButtonTouchModifier),
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}
