package com.dshmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.dshmobile.ui.theme.IosGlass
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    data object Pair : Tab("pair", "Pair")
    data object Sessions : Tab("sessions", "Sessions")
    data object Settings : Tab("settings", "Settings")
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

    // Floating Liquid-Glass tab bar: translucent, specular-bordered,
    // 28dp rounded, soft shadow, floating above content with inset
    // padding. Scaffold still reserves its height, so content never
    // slides underneath it and no bar overlaps the chat composer.
    val darkBar = isSystemInDarkTheme()
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = IosGlass.shadow(darkBar)),
                    shape = RoundedCornerShape(28.dp),
                    color = IosGlass.surfaceStrong(darkBar),
                    border = BorderStroke(1.dp, IosGlass.border(darkBar)),
                ) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = { navController.navigate(tab.route) { launchSingleTop = true } },
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
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Pair.route,
            modifier = Modifier.padding(padding).padding(bottom = 4.dp),
        ) {
            composable(Tab.Pair.route) { PairingScreen(pairingVm) }
            composable(Tab.Sessions.route) {
                SessionsScreen(sessionsVm) { id, title ->
                    navController.navigate("chat/$id?title=" + android.net.Uri.encode(title))
                }
            }
            composable(
                route = "chat/{sessionId}?title={title}",
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "Session" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("sessionId").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                ChatScreen(id, title, chatVm)
            }
            composable(Tab.Settings.route) {
                SettingsScreen(pairingVm, store) { model -> chatVm.setModel(model) }
            }
        }
    }
}
