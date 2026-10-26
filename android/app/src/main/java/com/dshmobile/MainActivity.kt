package com.dshmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PairingStore(applicationContext)
        val api = DshRemoteClient()
        setContent {
            DshMobileTheme {
                DshApp(store, api)
            }
        }
    }
}

private sealed class Tab(val route: String, val label: String) {
    data object Pair : Tab("pair", "配对")
    data object Sessions : Tab("sessions", "会话")
    data object Settings : Tab("settings", "设置")
}

@Composable
fun DshApp(store: PairingStore, api: DshRemoteClient) {
    val navController = rememberNavController()
    @Suppress("UNCHECKED_CAST")
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(PairingViewModel::class.java) -> PairingViewModel(api, store)
            modelClass.isAssignableFrom(SessionsViewModel::class.java) -> SessionsViewModel(api, store)
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(api, store)
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        } as T
    }
    val pairingVm: PairingViewModel = viewModel(factory = factory)
    val sessionsVm: SessionsViewModel = viewModel(factory = factory)
    val chatVm: ChatViewModel = viewModel(factory = factory)
    val tabs = remember { listOf(Tab.Pair, Tab.Sessions, Tab.Settings) }
    // 聊天模型初始值与设置页保存的值一致。
    LaunchedEffect(Unit) { chatVm.setModel(store.model.orEmpty()) }

    // 标准 iOS 标签栏：贴底、半透明底 + 顶部细分隔线，不再是悬浮药丸；
    // 进聊天详情页时按 iOS 规范隐藏标签栏，靠聊天页左上角返回。
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showTabBar = current == Tab.Pair.route || current == Tab.Sessions.route || current == Tab.Settings.route
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (store.isPaired) Tab.Sessions.route else Tab.Pair.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Tab.Pair.route) {
                PairingScreen(pairingVm, onPaired = {
                    navController.navigate(Tab.Sessions.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                    }
                })
            }
            composable(Tab.Sessions.route) {
                SessionsScreen(
                    sessionsVm,
                    onOpenSession = { id, title ->
                        navController.navigate("chat/$id?title=" + android.net.Uri.encode(title))
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
                ) { model ->
                    store.model = model
                    chatVm.setModel(model)
                }
            }
            composable(Tab.Settings.route) {
                SettingsScreen(pairingVm, store) { model -> chatVm.setModel(model) }
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

/** 标准 iOS 标签栏：贴底、半透明底、顶部 0.5dp 细分隔线；选中系统蓝，未选中灰。 */
@Composable
private fun IosTabBar(
    current: String?,
    tabs: List<Tab>,
    onTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                IosTabItem(
                    selected = current == tab.route,
                    icon = when (tab) {
                        Tab.Pair -> Icons.Filled.Link
                        Tab.Sessions -> Icons.Filled.Home
                        Tab.Settings -> Icons.Filled.Settings
                    },
                    label = tab.label,
                    onClick = { onTab(tab) },
                )
            }
        }
    }
}

/** 标签栏单项：图标 24dp + 10pt 标签，无药丸、无光晕、无阴影。 */
@Composable
private fun RowScope.IosTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
