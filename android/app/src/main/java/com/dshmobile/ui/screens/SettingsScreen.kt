package com.dshmobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dshmobile.data.PairingStore
import com.dshmobile.ui.PairingViewModel

/** 设置页：素色分段列表。 */
@Composable
fun SettingsScreen(
    pairingViewModel: PairingViewModel,
    store: PairingStore,
    onModelChange: (String) -> Unit,
) {
    val pairingState by pairingViewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("设置", subtitle = "服务器、模型与连接")
        SectionLabel("服务器")
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            AppTextField(value = pairingState.serverUrl, onValueChange = pairingViewModel::onServerUrlChange, placeholder = "服务器地址")
            StatusPill(paired = pairingState.paired, modifier = Modifier.padding(top = 12.dp))
            TextButton(
                onClick = pairingViewModel::disconnect,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("清除凭据", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "清除凭据只会清掉这台手机。还要在主机上撤销该设备（或在配对页点断开连接），主机才会停止接受它。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SectionLabel("模型")
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            AppTextField(value = store.model.orEmpty(), onValueChange = { v -> store.model = v; onModelChange(v) }, placeholder = "默认模型（可选）")
        }
        SectionLabel("连接方式")
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("局域网：用同一网络内的主机地址。隧道：用主机插件给出的隧道地址。Cloudflare / 固定中继隧道不在本应用 v1 范围内，请用主机插件的隧道或你自己控制的手动隧道，并保持配对和撤销开启。")
            Text(
                "已配对的设备拥有主机的完整操控权：可以驱动主机智能体。只配对你自己控制的设备。",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        VerticalSpacer(32)
    }
}
