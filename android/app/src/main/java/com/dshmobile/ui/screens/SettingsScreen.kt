package com.dshmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dshmobile.data.PairingStore
import com.dshmobile.ui.PairingViewModel
import androidx.compose.ui.text.input.KeyboardType

/** 设置页：iOS 分组列表，分组灰底 + 白色卡片，值在右、箭头在右那类行用 IosListRow。 */

@Composable
fun SettingsScreen(
    pairingViewModel: PairingViewModel,
    store: PairingStore,
    onModelChange: (String) -> Unit,
) {
    val pairingState by pairingViewModel.state.collectAsState()
    // 正在跑哪个耗时操作（检查/断开）：ViewModel 只有一个 busy 标志，分不出是哪个
    // 操作在跑，按钮文案就没法跟着变；屏上自己记一次点的是哪个。单操作互斥
    // （ViewModel 里 busy 时其余操作直接 return），记一个就够。busy 一停
    // （成功、失败、校验未通过导致根本没跑）就清掉——校验未通过时 busy 根本
    // 没变过，所以效果键要同时跟 message（校验失败会写 message），只跟 busy
    // 会在校验失败时把“检查中…”卡住。
    // 转屏不丢进行中文案：busy 是 ViewModel 状态（转屏还在），busyOp 只是屏上
    // 的 remember、转屏会丢——丢了按钮就变回闲置文案、却还禁着，容易误解为卡住。
    var busyOp by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pairingState.busy, pairingState.message) {
        if (!pairingState.busy) busyOp = null
    }
    // 模型输入必须用本地状态：store 不是 State，直接读它做输入框的值
    // 会导致打字不显示（上一个版本的真 bug）。
    var modelText by rememberSaveable { mutableStateOf(store.model.orEmpty()) }
    // 回来时若 store 被别处改过（聊天页换模型），恢复的草稿已过期：设置页
    // 自己的打字会同步写 store，所以草稿与 store 的差异（去空格后比）只能
    // 来自外部改动，此时以 store 为准。注意不能只靠 rememberSaveable 加键：
    // 返回栈恢复时注册表按位置键取值、键改了也一样恢复旧草稿，键解决不了
    // 恢复场景；这里用重进时的一次性比对兜底。首帧可能闪一下旧草稿、随后纠正。
    LaunchedEffect(Unit) {
        if (modelText.trim() != store.model?.trim().orEmpty()) modelText = store.model.orEmpty()
    }
    val context = LocalContext.current
    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
    }
    // 底部留白按 tabBarClearance 算（随字号），与会话页一致；标签栏是覆盖层，
    // 不是导航内容的一部分，内容页必须自己留出空隙，否则最后一组会被盖住。
    Column(
        // 键盘弹起时内容可上顶：聊天/会话页已有 imePadding，设置页的模型输入框
        // 在小屏上同样可能被键盘盖住，这里补齐、与另两页一致。
        modifier = Modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState()).padding(bottom = tabBarClearance()),
    ) {
        LargeTitle("设置", subtitle = "服务器、模型与连接")
        SectionLabel("服务器")
        Column(modifier = SectionContentModifier) {
            IosCard {
                IosListRow(
                    title = "连接状态",
                    value = if (pairingState.paired) "已配对" else "未配对",
                    showDivider = false,
                    // 状态行显示已保存的地址，不是输入框草稿：地址框绑的是 state
                    // （草稿），用户改了地址还没点保存时，状态行若读 state 就会把
                    // 没保存的草稿当成已连接地址显示；读 store 的才是真正配对的地址。
                    // 保存/配对成功都会同步 state 并触发重组，读 store 总是 fresh。
                    subtitle = store.serverUrl?.trim().orEmpty().ifBlank { "尚未连接主机" },
                )
            }
            Column(modifier = Modifier.padding(top = 12.dp)) {
                AppTextField(value = pairingState.serverUrl, onValueChange = pairingViewModel::onServerUrlChange, placeholder = "例如 http://192.168.x.x:3080", keyboardType = KeyboardType.Uri)
                // 保存钮补 busy 禁用：检查状态/清除凭据两钮都有，保存钮漏了，
                // 检查进行中点保存会把“已保存”文案盖掉、随后又被检查结果盖掉。
                VerticalSpacer(8)
                TextButton(onClick = pairingViewModel::saveServerUrl, enabled = !pairingState.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("保存服务器地址")
                }
                // 连接状态已经在上面的“连接状态”行里显示了（已配对/未配对 +
                // 服务器地址），这里再放一个状态药丸是重复信息，删掉。
                store.deviceName?.takeIf { it.isNotBlank() }?.let {
                    Text("本设备：$it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                VerticalSpacer(8)
                TextButton(onClick = { busyOp = "检查"; pairingViewModel.checkStatus() }, enabled = !pairingState.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (busyOp == "检查") "检查中…" else "检查服务器状态")
                }
                pairingState.message?.let {
                    // 报错/提示出现时报读：聊天页报错行已有 liveRegion，设置页的
                    // 检查结果与保存反馈同样是用户需要立刻知道的状态变化。
                    // 报错正文包 SelectionContainer（与会话页 ErrorState、聊天页消息行同款）：
                    // 检查/配对失败的原文用户可能要复制去反馈，纯 Text 框选不了。
                    SelectionContainer {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = if (pairingState.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                VerticalSpacer(8)
                TextButton(onClick = { busyOp = "断开"; pairingViewModel.disconnect() }, enabled = !pairingState.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (busyOp == "断开") "断开中…" else "清除凭据", color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "清除凭据时会先尝试在主机上撤销该设备；如果撤销未确认，请在主机上手动撤销。清除后应用会回到配对页，想换主机就在那里重新配对。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        SectionLabel("模型")
        Column(modifier = SectionContentModifier) {
            IosCard {
                // store 写在 onModelChange 统一入口里（去空格后写），字段这里只改本地草稿。
                AppTextField(value = modelText, onValueChange = { v -> modelText = v; onModelChange(v) }, placeholder = "模型名（留空用主机默认）")
                Text(
                    "模型名由主机提供，留空用主机默认；填错会在聊天时报错。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        SectionLabel("连接方式")
        Column(modifier = SectionContentModifier) {
            IosCard {
                Text("局域网：用同一网络内的主机地址。隧道：用主机插件给出的隧道地址。Cloudflare / 固定中继隧道不在本应用 v1 范围内，请用主机插件的隧道或你自己控制的手动隧道，并保持配对和撤销开启。")
                Text(
                    "已配对的设备拥有主机的完整操控权：可以驱动主机智能体。只配对你自己控制的设备。",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Footnote("版本 ${versionName ?: "未知"}")
        VerticalSpacer(32)
    }
}
