package com.dshmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

    // 极简底部导航：细分割线 + 素色底，不用悬浮毛玻璃条，
    // 选中项黑/白、未选中灰，无 Material 药丸指示器。
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        Tab.Pair -> Icons.Filled.Link
                                        Tab.Sessions -> Icons.Filled.Home
                                        Tab.Settings -> Icons.Filled.Settings
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (store.isPaired) Tab.Sessions.route else Tab.Pair.route,
            modifier = Modifier.padding(padding),
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
    }
}
