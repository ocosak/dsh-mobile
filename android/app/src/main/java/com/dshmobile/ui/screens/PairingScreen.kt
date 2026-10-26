package com.dshmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dshmobile.ui.PairingViewModel
import androidx.compose.ui.text.input.KeyboardType

/**
 * 配对页：iOS 分组表单，分组灰底 + 白色卡片。v1 只支持粘贴配对码（不支持扫码），
 * 设备凭据永远不在这里展示。
 */

@Composable
fun PairingScreen(viewModel: PairingViewModel, initialDeviceName: String) {
    val state by viewModel.state.collectAsState()
    var token by rememberSaveable { mutableStateOf("") }
    // 设备名从 store 预填：换主机时 R282-2 只清凭据、设备名特意保留，但字段
    // 恒空、用户留空会走默认名“安卓手机”把旧名覆盖掉，保留不可见；预填后
    // 旧名直接可用、也可改。首次配对与断开后（clear 整表）store 里本就无名，
    // 仍为空，与原来一致；配对码绝不预填（一次性、不存 store）。字段是
    // rememberSaveable：转屏保留用户改过的字，不回落到初始值。
    var deviceName by rememberSaveable { mutableStateOf(initialDeviceName) }
    var pasteNote by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    // 正在跑检查还是配对：配对按钮已有“配对中…”文案，检查按钮没有；屏上记一次
    // 点的是检查（与设置页同模式，效果键跟 busy 与 message，校验未通过时也能清掉）。
    // 转屏不丢进行中文案：与设置页 busyOp 同理，busy 在 ViewModel 里还在，
    // 屏上的 checking 若是普通 remember、转屏就丢了。
    var checking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.busy, state.message) {
        if (!state.busy) checking = false
    }
    // 配对成功清掉一次性配对码（不留在内存里）：正常路径下 MainActivity 的
    // 分支在 paired 翻 true 时直接切走、配对页组合被 dispose、token 随组合丢弃，
    // 这个效果只是安全网（万一将来配对页改成常驻，效果仍能清掉码）；切主页
    // 不用回调，MainActivity 的分支读到 paired 翻 true 自己就会切过去。
    LaunchedEffect(state.paired) {
        if (state.paired) token = ""
    }

    // 配对页现在是进入时的整页（没有标签栏），底部留白不用再给标签栏留 96dp。
    Column(
        // 键盘弹起时内容可上顶：聊天/会话/设置页已有 imePadding，配对页补齐。
        modifier = Modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        LargeTitle("配对设备", subtitle = "把这台手机连接到你的 dsh 主机")
        SectionLabel("主机")
        Column(modifier = SectionContentModifier) {
            IosCard {
                Text("服务器地址", style = MaterialTheme.typography.labelLarge)
                AppTextField(
                    value = state.serverUrl, onValueChange = viewModel::onServerUrlChange,
                    placeholder = "例如 http://192.168.x.x:3080",
                    modifier = Modifier.padding(top = 8.dp),
                    keyboardType = KeyboardType.Uri,
                )
                Text(
                    "用同一局域网内的地址，或者主机插件给出的隧道地址。不要在没有配对和撤销保护的情况下把 dsh 直接暴露到公网。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                VerticalSpacer(8)
                TextButton(onClick = { checking = true; viewModel.checkStatus() }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (checking) "检查中…" else "检查服务器状态")
                }
            }
        }
        SectionLabel("配对")
        Column(modifier = SectionContentModifier) {
            IosCard {
                Text("配对码（粘贴）", style = MaterialTheme.typography.labelLarge)
                // 手动改码清掉粘贴提示：提示是“已粘贴、请检查后点配对”，
                // 手一改码就过期了、还挂着会误导；粘贴路径是直接赋值、
                // 不走 onValueChange，所以提示不会被粘贴本身清掉。
                AppTextField(value = token, onValueChange = { token = it; pasteNote = null }, placeholder = "从主机面板粘贴配对码", modifier = Modifier.padding(top = 8.dp), password = true)
                VerticalSpacer(8)
                TextButton(
                    onClick = {
                        // 配对码从主机面板复制过来时常带换行或空格：以前只去首尾，
                        // 中间的空白会留在码里导致配对失败；码本身不含空白，直接全去掉。
                        val pasted = clipboardManager.getText()?.text?.filterNot { it.isWhitespace() }.orEmpty()
                        if (pasted.isEmpty()) pasteNote = "剪贴板里没有可粘贴的文字。"
                        else { token = pasted; pasteNote = "已粘贴配对码，请检查后点配对。" }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("从剪贴板粘贴")
                }
                pasteNote?.let {
                    // 粘贴反馈报读：点“从剪贴板粘贴”后提示是用户刚触发的操作结果，
                    // TalkBack 焦点还在按钮上，不报读就不知道粘贴成没成功。
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
                Text("设备名称", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                AppTextField(value = deviceName, onValueChange = { deviceName = it }, placeholder = "例如 我的安卓手机", modifier = Modifier.padding(top = 8.dp))
                PrimaryButton(
                    // 检查进行中时配对钮也要禁着，但文案不能写“配对中…”：busy
                    // 只有一个标志，分不出是检查还是配对在跑；检查时配对根本没跑，
                    // 写“配对中…”会和检查钮的“检查中…”自相矛盾，checking 为真时
                    // 显示闲置文案“配对”。
                    // 注意：这里没有“重新配对”分支与断开钮——MainActivity 只在未
                    // 配对时才挂配对页（paired 为真直接切主页），配对页永远见不到
                    // paired = true；以前的“重新配对”文案与断开钮是先配对后主页流
                    // 程之前的残留，从未渲染过，删掉免得后人误以为配对页要处理已配
                    // 对态；断开只走设置页的清除凭据。
                    text = if (state.busy && !checking) "配对中…" else "配对",
                    onClick = { viewModel.pair(token, deviceName) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                StatusPill(paired = state.paired, modifier = Modifier.padding(top = 16.dp))
                state.message?.let {
                    // 配对结果报读：配对成功/失败是整页最关键的状态变化，聊天页与
                    // 设置页已有同口径，配对页补齐。
                    // 同设置页：报错正文包 SelectionContainer，可框选复制。
                    SelectionContainer {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        }
        SectionLabel("扫码")
        Column(modifier = SectionContentModifier) {
            IosCard {
                Text("暂不支持扫码：v1 请粘贴上面的配对码。")
            }
        }
        VerticalSpacer(32)
    }
}
