package com.dshmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dshmobile.data.DshRemoteClient
import com.dshmobile.data.PairingStore
import com.dshmobile.ui.ChatViewModel
import com.dshmobile.ui.PairingViewModel
import com.dshmobile.ui.SessionsViewModel
import com.dshmobile.ui.screens.ChatScreen
import com.dshmobile.ui.screens.PairingScreen
import com.dshmobile.ui.screens.SessionsScreen
import com.dshmobile.ui.screens.SettingsScreen
import com.dshmobile.ui.theme.DshMobileTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PairingStore(applicationContext)
        val api = DshRemoteClient()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            DshMobileTheme(darkTheme = darkTheme) {
                // 系统栏底色必须跟随主题：平台主题固定 Light、栏底色透明、
                // 内容又不探入栏区（未调 enableEdgeToEdge），栏区实际显示的
                // 是恒白的窗口底——R218 只同步图标深浅时，深色下白图标落
                // 在白底上会隐形（R218 前图标恒深、至少可见，R218 后深色
                // 反而不可见）。这里把栏底色也同步成主题 background（白/黑），
                // 图标深浅（!darkTheme）才成立；浅色下与原来一致（白底深图标）。
                // SideEffect 须在主题内，才能读到应用的 colorScheme。
                val barColor = MaterialTheme.colorScheme.background
                SideEffect {
                    window.statusBarColor = barColor.toArgb()
                    window.navigationBarColor = barColor.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                // 根部画布：聊天/会话页自己不画背景，以前靠窗口主题底色；
                // 平台主题是 Light，深色下窗口底仍是白色、白字会隐形；这里
                // 显式铺一层主题 background（白/纯黑），iOS 画布才有保证。
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DshApp(store, api)
                }
            }
        }
    }
}

/** 主页标签：只有会话与设置。配对是进主页前的整页，不是标签。 */
private sealed class Tab(val route: String, val label: String) {
    data object Sessions : Tab("sessions", "会话")
    data object Settings : Tab("settings", "设置")
}

/** 应用根：先配对后主页。未配对时整页只有配对页；已配对后主页只有会话/设置
 * 两个标签，聊天详情页隐藏标签栏，靠左上角返回。 */
@Composable
fun DshApp(store: PairingStore, api: DshRemoteClient) {
    val navController = rememberNavController()
    @Suppress("UNCHECKED_CAST")
    // 工厂 remember 住：以前每次重组都新建一个工厂对象，虽无害（VM 建好后
    // 不再用它），但同一身份更干净，也免得后人误以为工厂每次都要新建。
    val factory = remember {
        object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(PairingViewModel::class.java) -> PairingViewModel(api, store)
            modelClass.isAssignableFrom(SessionsViewModel::class.java) -> SessionsViewModel(api, store)
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(api, store)
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        } as T
        }
    }
    val pairingVm: PairingViewModel = viewModel(factory = factory)
    val sessionsVm: SessionsViewModel = viewModel(factory = factory)
    val chatVm: ChatViewModel = viewModel(factory = factory)
    val tabs = remember { listOf(Tab.Sessions, Tab.Settings) }
    val pairingState by pairingVm.state.collectAsState()
    // 聊天模型初始值与设置页保存的值一致。键不能是 Unit：断开会清
    // store.model，但 chatVm.model 还留着旧值；用 paired 作键，重配
    // 回主页时重新从 store 同步一次，旧模型名就不会继续发请求。
    LaunchedEffect(pairingState.paired) { chatVm.setModel(store.model.orEmpty()) }
    // 模型改动统一入口：聊天换模型与设置页打字都走这里——store 存去空格后
    // 的值、chatVm 经 setModel（内部同样 trim）；设置页字段此前直接写 raw
    // 进 store（尾空格会让 store 与 chatVm 分歧，读处虽都 trim、无害，
    // 但 store 应持规范值），现收归这里，字段只改本地草稿。remember 包一层，
    // 身份稳定，不因 DshApp 重组而连带重组聊天/设置页。
    val onModelChange: (String) -> Unit = remember(store, chatVm) {
        { model ->
            store.model = model.trim()
            chatVm.setModel(model)
        }
    }

    // 进入流程：先配对、后主页。未配对时整个应用就是配对页，没有
    // 标签栏、进不了会话和设置；配对成功（或启动时已配对）才进主页，
    // 主页只有会话和设置两个标签。设置里清除凭据后 paired 翻回 false，
    // 这里会自动回到配对页；想换主机就走设置里清除凭据再重新配对。
    if (!pairingState.paired) {
        PairingScreen(pairingVm, store.deviceName.orEmpty())
        return
    }

    // 标准 iOS 标签栏：贴底、不透明底 + 顶部细分隔线，不再是悬浮药丸；
    // 进聊天详情页时按 iOS 规范隐藏标签栏，靠聊天页左上角返回。
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    // 首帧 current 还是 null（导航未落定），起始页本就是会话标签，
    // null 时也显示标签栏，免得首帧闪一下无栏、次帧才出现。
    val showTabBar = current == null || current == Tab.Sessions.route || current == Tab.Settings.route
    // 每次从配对页回到主页（包括断开后再重配）都回到会话首页，
    // 而不是停在上次断开时所在的设置页。主分支离开组合后这个
    // Effect 会被清掉，重进时才会再跑一次，页内切标签不会触发它。
    LaunchedEffect(Unit) {
        navController.navigate(Tab.Sessions.route) {
            launchSingleTop = true
            popUpTo(navController.graph.startDestinationId) { saveState = false }
        }
    }
    // 标签栏是覆盖在导航内容上的（不是导航内容的一部分）：会话/设置页用
    // tabBarClearance 自己留出空隙，聊天详情页隐藏标签栏所以不用留。
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Tab.Sessions.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Tab.Sessions.route) {
                SessionsScreen(
                    sessionsVm,
                    onOpenSession = { id, title ->
                        // 空白标题兜底与会话行 displayTitle 同款（“未命名会话”）：
                        // 行里称“未命名会话”的会话，点进去顶栏不能变回“会话”，
                        // 否则同一会话两处叫法不一、多个空白标题会话在聊天页无法
                        // 区分；行点与新建导航都走这里，一处改即全覆盖。
                        // ChatScreen 的 ifBlank“会话”仍留作路由参数缺省的安全网。
                        val displayTitle = title.ifBlank { "未命名会话" }
                        // 快速双点同一行会压两条相同聊天页：返回时还要再经过一次
                        // 同一会话；launchSingleTop 压掉重复项，不同会话仍正常进。
                        navController.navigate("chat/${android.net.Uri.encode(id)}?title=${android.net.Uri.encode(displayTitle)}") {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Tab.Settings.route) { launchSingleTop = true } },
                )
            }
            composable(
                route = "chat/{sessionId}?title={title}",
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "会话" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("sessionId").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                ChatScreen(
                    id, title, chatVm,
                    onBack = { navController.popBackStack() },
                    onModelChange = onModelChange,
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(pairingVm, store, onModelChange)
            }
        }
        if (showTabBar) {
            IosTabBar(
                current = current,
                tabs = tabs,
                onTab = { tab ->
                    navController.navigate(tab.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 标准 iOS 标签栏：贴底、不透明底、顶部 0.5dp 细分隔线；选中系统蓝，未选中灰。 */
@Composable
private fun IosTabBar(
    current: String?,
    tabs: List<Tab>,
    onTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 标签栏用不透明底：半透明底会让滚动内容在它下面透出来，
    // 截图里配对按钮和设置页最后一张卡就是被它盖住一半。
    Column(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            tabs.forEach { tab ->
                IosTabItem(
                    // 首帧 current 还是 null（导航未落定），起始页本就是会话标签；
                    // null 时默认选中会话，免得首帧两个标签全灰、次帧才亮一个。
                    selected = (current ?: Tab.Sessions.route) == tab.route,
                    icon = when (tab) {
                        Tab.Sessions -> Icons.Filled.ChatBubble
                        Tab.Settings -> Icons.Filled.Settings
                    },
                    label = tab.label,
                    onClick = { onTab(tab) },
                )
            }
        }
    }
}

/** 标签栏单项：图标 24dp + 11sp 标签，无药丸、无光晕、无阴影；图标不单独报读，
 * 文字标签已是名字，再报一次会让 TalkBack 读两遍。 */
@Composable
private fun RowScope.IosTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // 选中色一处算：图标与文字原来各写一遍三元，改一边漏另一边就会半蓝半灰。
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    // 注意 Modifier 顺序：clickable 在 padding 之前表示 clickable 在外、触控区
    // 包含这圈内边距（图标 24 + 标签 16 + 上下 4 + 4 = 48dp）；若把 padding 移到
    // clickable 之前，触控区反而只剩内容 40dp、不到 48dp——本轮曾误改成后者，
    // 经复核已 revert 回 clickable 在前。
    Box(
        modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button) { onClick() }
            // 选中态要报出来：以前 TalkBack 只读“会话，按钮”，听不出当前在哪个标签。
            // 这版 Compose 里 selected 扩展属性只有 getter、不能赋值，改按语义键
            // 直接写，TalkBack 读到的选中态不变。
            .semantics { this[SemanticsProperties.Selected] = selected }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 标签栏图标不单独报读：下面的文字标签已经是这个标签的名字，
            // 图标再报一次会让 TalkBack 把“会话”读两遍。
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = color,
            )
        }
    }
}
