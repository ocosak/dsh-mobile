package com.dshmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dshmobile.ui.PairingViewModel

/**
 * 配对页：iOS 分组表单，分组灰底 + 白色卡片。v1 只支持粘贴配对码（不支持扫码），
 * 设备凭据永远不在这里展示。
 */
@Composable
fun PairingScreen(viewModel: PairingViewModel, onPaired: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var token by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    // 配对成功后立即清掉一次性配对码，不留在内存里。
    LaunchedEffect(state.paired) { if (state.paired) token = "" }
    // 刚配对成功才跳到会话页；本来就已配对时点进本页不乱跳。
    var wasPaired by remember { mutableStateOf(state.paired) }
    LaunchedEffect(state.paired) {
        if (state.paired && !wasPaired) onPaired()
        wasPaired = state.paired
    }

    // 配对页现在是进入时的整页（没有标签栏），底部留白不用再给标签栏留 96dp。
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        LargeTitle("配对设备", subtitle = "把这台手机连接到你的 dsh 主机")
        SectionLabel("主机")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            IosCard {
                Text("服务器地址", style = MaterialTheme.typography.labelLarge)
                AppTextField(
                    value = state.serverUrl, onValueChange = viewModel::onServerUrlChange,
                    placeholder = "例如 http://192.168.x.x:3080",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "用同一局域网内的地址，或者主机插件给出的隧道地址。不要在没有配对和撤销保护的情况下把 dsh 直接暴露到公网。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = viewModel::checkStatus, enabled = !state.busy, modifier = Modifier.padding(top = 4.dp)) {
                    Text("检查服务器状态")
                }
            }
        }
        SectionLabel("配对")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            IosCard {
                Text("配对码（粘贴）", style = MaterialTheme.typography.labelLarge)
                AppTextField(value = token, onValueChange = { token = it }, placeholder = "从主机面板粘贴配对码", modifier = Modifier.padding(top = 8.dp), password = true)
                TextButton(
                    onClick = { clipboardManager.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { token = it } },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("从剪贴板粘贴")
                }
                Text("设备名称", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                AppTextField(value = deviceName, onValueChange = { deviceName = it }, placeholder = "例如 我的安卓手机", modifier = Modifier.padding(top = 8.dp))
                PrimaryButton(
                    text = if (state.paired) "重新配对" else "配对",
                    onClick = { viewModel.pair(token, deviceName) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                if (state.paired) {
                    TextButton(onClick = viewModel::disconnect, modifier = Modifier.padding(top = 8.dp)) {
                        Text("断开连接 / 撤销本设备", color = MaterialTheme.colorScheme.error)
                    }
                }
                StatusPill(paired = state.paired, modifier = Modifier.padding(top = 12.dp))
                state.message?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        SectionLabel("扫码")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            IosCard {
                Text("暂不支持扫码：v1 请粘贴上面的配对码。主机面板上的二维码里就是这个码。")
            }
        }
        VerticalSpacer(32)
    }
}
