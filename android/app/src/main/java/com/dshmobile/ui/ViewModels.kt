package com.dshmobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dshmobile.data.ApprovalRequest
import com.dshmobile.data.ChatEvent
import com.dshmobile.data.ChatMessage
import com.dshmobile.data.DshRemoteApi
import com.dshmobile.data.DshSession
import com.dshmobile.data.PairingStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 报错文案统一：异常的 message 可能是 null，直接拼会把“null”两个字
// 显示给用户；常见网络英文错也换成可操作的中文。这里统一处理，各处报错都走它。
private fun Throwable.userMessage(): String = message.toUserMessage()

// 网络英文错翻译成中文，供 Throwable 与 String 两路共用：Result 失败走前者，
// 流式 ChatEvent.Error（本身是 String，不是异常）走后者，两路文案一致。
private fun String?.toUserMessage(): String {
    val raw = this?.takeIf { it.isNotBlank() } ?: return UNKNOWN_ERROR_MSG
    // HTTP 码（如 HTTP 401）不进这几条，原样返回，authHint 还能继续识别。
    return when {
        raw.contains("Unable to resolve host", ignoreCase = true) || raw.contains("UnknownHost", ignoreCase = true) ->
            "无法解析主机地址，请检查服务器地址是否正确。"
        raw.contains("Connection refused", ignoreCase = true) || raw.contains("Failed to connect", ignoreCase = true) ||
            raw.contains("Network is unreachable", ignoreCase = true) ||
            // 路由不可达（错网段、VPN 分流时常见）：与 refused 同属“连不上主机”，
            // 旧表只认 refused/failed/unreachable，这两条系统串会原样甩英文。
            raw.contains("No route to host", ignoreCase = true) || raw.contains("Host unreachable", ignoreCase = true) ->
            "无法连接到主机，请检查主机是否在线、地址和端口是否正确。"
        // 连接被主机重置（主机重启、NAT 超时断连）：以前不在翻译表里，
        // 用户会直接看到英文 "Connection reset by peer"；这是移动网络下
        // 常见的错，给中文 + 可执行动作（稍后重试）。
        raw.contains("Connection reset", ignoreCase = true) ||
            // 写侧断连（主机中途关了连接）：与 reset 同属“主机侧已断”，指引相同。
            raw.contains("Broken pipe", ignoreCase = true) ||
            // 套接字已关：客户端主动取消已在流式层转成取消、到不了这里，剩下的
            // 是主机侧关闭；与 reset 同分支（R274 同款标准 socket 串补齐）。
            raw.contains("Socket closed", ignoreCase = true) || raw.contains("Socket is closed", ignoreCase = true) ->
            "连接被主机重置，主机可能重启了，请稍后重试。"
        raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
            "连接超时，请检查网络或主机是否在线。"
        // 自签证书的 https 主机：OkHttp 握手失败的英文错用户看不懂；局域网
        // 主机常见自签证书，直接指到“改用局域网 http 地址”这个可执行动作。
        raw.contains("PKIX", ignoreCase = true) || raw.contains("SSLHandshake", ignoreCase = true) ||
            raw.contains("Trust anchor", ignoreCase = true) || raw.contains("certificate", ignoreCase = true) ||
            // 裸 SSL 兜底：SSLException / SSLProtocolException 这类不带上面关键词的
            // TLS 层失败，以前会原样甩英文；指引同样适用（核对地址、改走局域网 http）。
            raw.contains("SSL", ignoreCase = true) ->
            "无法验证主机证书（可能是自签证书），请检查地址是否正确，或改用局域网 http 地址。"
        // 裸词拒绝（主机只给 Unauthorized/Forbidden、不给数字码）：以前原样甩英文词，
        // 用户只看到英文；译成中文并内嵌（401）/（403）码——内嵌码是故意的，
        // authHint 在翻译后的文案上仍能靠数字码识别、继续给清凭据指引；若只译词
        // 不嵌码，authHint 在翻译后文案上认不出词、指引就丢了。带详情的长串
        // （如 "Forbidden: model not allowed"）不译、原样保留详情 + hint。
        raw.trim().equals("Unauthorized", ignoreCase = true) -> "未授权（401）"
        raw.trim().equals("Forbidden", ignoreCase = true) -> "禁止访问（403）"
        else -> raw
    }
}

private const val UNPAIRED_MSG = "尚未配对，请先在配对页连接主机。"
private const val SERVER_REQUIRED_MSG = "请先填写服务器地址。"
private const val DEFAULT_DEVICE_NAME = "安卓手机"
private const val UNKNOWN_ERROR_MSG = "未知错误"
private const val INVALID_BASE_MSG = "服务器地址不对：填 http:// 或 https:// 开头的地址，或者直接填主机地址，例如 192.168.1.10:3080。"

// 401/403 不是普通网络抖动：凭据被主机撤销/失效时，重试同一请求只会
// 再失败一次；直接告诉用户去设置里清凭据重配，比只给 HTTP 码可恢复。
// 主机也可能只给英文词不给码（如 {"error": "Unauthorized"}），同样认。
// 状态码必须按独立数字匹配：以前是子串匹配，端口号里的 1401、错误号里的
// 4012 也会误判成凭据失效（甚至把一次连接失败指成“清凭据重配”，方向全错）。
// 翻译后的文案里只有 HTTP 码带数字（如 "HTTP 401"），独立匹配不受影响。
private val AuthCodeRegex = Regex("(?<!\d)(401|403)(?!\d)")
private val NotFoundCodeRegex = Regex("(?<!\d)404(?!\d)")
private fun authHint(msg: String): String =
    if (AuthCodeRegex.containsMatchIn(msg) ||
        msg.contains("Unauthorized", ignoreCase = true) || msg.contains("Forbidden", ignoreCase = true)
    ) "，凭据可能已失效，请在设置里清除凭据后重新配对。" else ""

class PairingViewModel(
    private val api: DshRemoteApi,
    private val store: PairingStore,
) : ViewModel() {
    private val _state = MutableStateFlow(PairingUiState(store.serverUrl.orEmpty(), store.isPaired))
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    data class PairingUiState(
        val serverUrl: String = "",
        val paired: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
        // 报错要不要用红色，不能靠文案前缀猜（文案一改就错）；
        // 成功/信息为 false，失败/警告为 true，由写消息的地方显式给。
        val messageIsError: Boolean = false,
    )

    fun onServerUrlChange(v: String) = _state.update { it.copy(serverUrl = v, message = null, messageIsError = false) }

    /**
     * 设置页的服务器地址原来只改 ViewModel 状态、不写 store：
     * 切到会话页用的是 store 里的旧地址，重启后改动直接丢了。
     * 这个保存按钮把当前地址（去首尾空格、去尾斜杠）写进 store，
     * 配对页的 pair() 成功时同样只存整理后的地址。
     */
    // 服务器地址先整理再校验：去掉首尾空格和尾斜杠（尾斜杠会让拼接
    // 的接口路径多一个斜杠），再按 isValidBase 校验（带 scheme 须为 http(s)，
    // 裸主机也放行、由客户端归一化；旧注释说必须带 scheme 已过时）。
    // 与 ApiEndpoints.cleanBase 同规则、再推广一步：去首尾空格、去尾斜杠、
    // 去用户手写的 /remote 后缀（不区分大小写），再去路径/查询/片段、只留
    // scheme + authority（与客户端 normalisedBase 的 authority 口径一致）。
    // 请求层本就会再去一次、功能上不差这一处；这里去是为了存下来、显示
    // 出来的地址就是规范形——否则地址栏里带个 /dsh、?x=1 之类路径，请求层
    // 剥掉后实际走的地址与显示的对不上（R268 的 /remote 同款问题，推广到
    // 一般路径）。ApiEndpoints.cleanBase 是私有的，这里按同规则写一遍。
    private fun cleanedBase(): String {
        val trimmed = _state.value.serverUrl.trim().trimEnd('/')
        val noRemote = if (trimmed.endsWith("/remote", ignoreCase = true)) trimmed.dropLast("/remote".length) else trimmed
        val afterScheme = if (noRemote.contains("://")) noRemote.substringAfter("://") else noRemote
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return if (noRemote.contains("://")) noRemote.substringBefore("://") + "://" + authority else authority
    }
    // 上一轮这里要求必须带 scheme，会拦住“192.168.x.x:3080”这种裸局域网
    // 地址；但客户端 normalisedBase 本来就会把裸地址归一化（本地走 http、
    // 其他走 https），所以只拦真正错的：带了非 http(s) scheme、含空格、
    // 或主机部分为空。裸主机必须含点、冒号或就是 localhost。
    private fun isValidBase(url: String): Boolean {
        // 空白字符全量检查：以前只拦空格，粘贴带制表符/换行的地址会漏网，
        // 带着空白进归一化、最终拼出坏地址；isWhitespace 覆盖空格与制表换行。
        if (url.isBlank() || url.any { it.isWhitespace() }) return false
        val scheme = url.substringBefore("://", missingDelimiterValue = "")
        // scheme 与 localhost 比较不区分大小写：客户端归一化（R241/R235）早已
        // 接受 "HTTP://..." 与 "LOCALHOST"，但这里的校验还是大小写敏感——
        // 大写地址到不了客户端、在校验这一步就被拦住，两处口径不一致。
        if (scheme.isNotEmpty() && !scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false
        val hostPart = url.substringAfter("://", url).substringBefore('/')
        // 主机部分只认四种形：方括号 IPv6（含端口与否）、含点的主机名、localhost、
        // 主机:数字端口。以前是“含冒号即过”，会放行 http:、host:、host:abc、
        // host:8080:9090 这类退化输入（冒号后不是端口）；这类地址到归一化会拼
        // 出坏地址、请求层再报错，不如在校验这一步就拦住、给明确的地址错误。
        // 单标签无端口主机名仍拦（防手滑，与既定 typo 拦截一致）。
        if (hostPart.isEmpty()) return false
        if (hostPart.startsWith("[") && hostPart.contains("]")) return true
        if (hostPart.equals("localhost", ignoreCase = true)) return true
        if (hostPart.contains('.')) return true
        // 剩下的只能是 主机:端口：恰好一个冒号、端口非空全数字。
        if (hostPart.count { it == ':' } != 1) return false
        val port = hostPart.substringAfter(':')
        return port.isNotEmpty() && port.all { it.isDigit() }
    }

    fun saveServerUrl() {
        // 其余三操作（检查/配对/断开）都有 busy 守卫，这里漏了：目前唯一调用点
        // （设置页保存钮）已是 busy 禁用、竞态走不到这里，但 ViewModel 不应依赖
        // 调用方替它守门——检查进行中点保存会把“已保存”盖掉、随后又被检查结果
        // （还是旧地址的）盖掉；守卫与兄弟操作一致，未来调用点增多也不出竞态。
        if (_state.value.busy) return
        val cleaned = cleanedBase()
        if (cleaned.isBlank()) {
            _state.update { it.copy(message = SERVER_REQUIRED_MSG, messageIsError = true) }
            return
        }
        if (!isValidBase(cleaned)) {
            _state.update { it.copy(message = INVALID_BASE_MSG, messageIsError = true) }
            return
        }
        // 凭据与主机绑定：地址真变了且已配对时，旧凭据若留着，下次请求
        // 会把它发给新主机——新主机若与旧主机同网段，可拿它冒充本设备；
        // 即使无恶意，旧凭据在新主机上也只会 401、误导用户去清凭据。
        // 所以只清凭据（设备名与模型偏好保留），回未配对、提示用新主机
        // 的配对码重配；地址没变时不碰凭据（重复保存是无操作）。
        // 比较不区分大小写：cleaned 已剥路径、只剩 scheme + authority，
        // 两者均大小写不敏感，HTTP://Host 与 http://host 是同一主机。
        val oldUrl = store.serverUrl
        val hadCredential = !store.deviceCredential.isNullOrBlank()
        store.serverUrl = cleaned
        if (hadCredential && (oldUrl == null || !oldUrl.equals(cleaned, ignoreCase = true))) {
            store.deviceCredential = null
            _state.update { it.copy(serverUrl = cleaned, paired = false, message = "服务器地址已更换，旧主机凭据已清除，请用新主机的配对码重新配对。", messageIsError = false) }
        } else {
            _state.update { it.copy(serverUrl = cleaned, message = "服务器地址已保存，请点“检查服务器状态”确认可达。", messageIsError = false) }
        }
    }

    /** Check the host is reachable and report whether any device is paired and whether LAN requires pairing, without pairing. */
    fun checkStatus() {
        if (_state.value.busy) return
        val base = cleanedBase()
        if (base.isBlank()) {
            _state.update { it.copy(message = SERVER_REQUIRED_MSG, messageIsError = true) }
            return
        }
        if (!isValidBase(base)) {
            _state.update { it.copy(message = INVALID_BASE_MSG, messageIsError = true) }
            return
        }
        // busy 同步置位（R211 的 send 同款）：置位若在协程内，同帧双点时第二次
        // 调用仍看到 busy=false、会再起一个协程；同步置位后直接被守卫拦住。
        _state.update { it.copy(busy = true, message = null, messageIsError = false) }
        viewModelScope.launch {
            api.pairStatus(base)
                .onSuccess { status ->
                    val pairedText = if (status.paired) "已有设备配对" else "尚未配对"
                    val lanText = if (status.requirePairingForLan) "局域网需要配对" else "局域网无需配对"
                    // 下一步按本设备配对态区分：主机说“尚未配对”但本设备已配对时，
                    // 说明主机端凭据丢了，用户在设置页根本到不了配对页；直接指
                    // 清除凭据重配，比“请填写配对码完成配对”少绕一圈。
                    // 主机说“已有设备配对”但本设备未配对时同样要指路：以前 nextStep
                    // 为空，“已有设备配对”会被误读成本设备已配对（配对页还挂着、
                    // 其实是别台设备配上了）；本设备未配对时无论主机态都指去配对页。
                    val nextStep = when {
                        !status.paired && _state.value.paired -> "本设备凭据可能已失效，请在设置里清除凭据后重新配对。"
                        // 设置页恒已配对（MainActivity 按同一 paired 分支），此臂只在
                        // 配对页触发（或检查中途断开、回落到配对页时）；“请在配对页
                        // 完成配对”是对已在配对页的人原地指路，改成填码指引——配对
                        // 表单就在下方，填码才是下一步。
                        !_state.value.paired -> "请填写配对码完成配对。"
                        else -> ""
                    }
                    _state.update { it.copy(busy = false, message = "服务器可达：$pairedText，$lanText。$nextStep", messageIsError = false) }
                }
                // 前缀不能写“服务器不可达”：HTTP 401/500 与解析错同样走这里，
                // 服务器明明可达（只是拒绝或回包异常），写不可达会把用户往查网
                // 络的方向误导；“检查失败”对三类都成立，具体原因看 m 本身。
                // 其余失败路（刷新/创建/删除/重命名/发送/审批）全补了 authHint，
                // 这里漏了：已配对时检查 401 就是凭据失效，用户正在设置页、
                // 清凭据重配的入口就在手边，不给指引说不过去。
                .onFailure { e -> val m = e.userMessage(); _state.update { it.copy(busy = false, message = "检查失败：$m${authHint(m)}", messageIsError = true) } }
        }
    }

    /** Pair with a one-time token: busy-guarded, token/device name trimmed first;
     * on success stores the cleaned base, the device credential and the device name,
     * so later calls use them; on 401/403 the failure hint says the token may be
     * expired (pairing has no stored credential to clear, unlike later 401s). */
    fun pair(token: String, deviceName: String) {
        if (_state.value.busy) return
        val base = cleanedBase()
        if (base.isBlank()) {
            _state.update { it.copy(message = SERVER_REQUIRED_MSG, messageIsError = true) }
            return
        }
        if (!isValidBase(base)) {
            _state.update { it.copy(message = INVALID_BASE_MSG, messageIsError = true) }
            return
        }
        // 校验按表单自上而下：地址在上、配对码在下；以前先报配对码空，
        // 地址也错时用户要跑两趟才报完，顺序与界面不一致。
        if (token.isBlank()) {
            _state.update { it.copy(message = "请先填写配对码。", messageIsError = true) }
            return
        }
        // busy 同步置位（同 checkStatus）：双点会重复消费一次性配对码，第二发必
        // 失败、还可能盖掉第一发的成功文案；同步置位后第二发直接被守卫拦住。
        _state.update { it.copy(busy = true, message = null, messageIsError = false) }
        viewModelScope.launch {
            val cleanName = deviceName.trim().ifBlank { DEFAULT_DEVICE_NAME }
            val result = api.acceptPairing(base, token.filterNot { it.isWhitespace() }, cleanName)
            result.onSuccess { credential ->
                store.serverUrl = base
                store.deviceCredential = credential
                store.deviceName = cleanName
                // state 里的地址同步成整理后的 base：用户手滑带空格/尾斜杠时，
                // store 里已是 cleaned，state 若还留原文，设置页显示的地址就与
                // 实际配对地址对不上（saveServerUrl 早已是同步写法，这里补齐）。
                _state.update { it.copy(busy = false, paired = true, serverUrl = base, message = "已配对。本设备现在可以操控主机，请保持手机锁定。", messageIsError = false) }
            }.onFailure { e ->
                val m = e.userMessage()
                // 与 authHint 同口径：除数字码外，主机只给 Unauthorized/Forbidden
                // 英文词时同样认（authHint 早已双认，配对提示只认数字码会漏
                // word-only 的拒绝，提示就只剩干巴巴的失败原因）。
                val hint = if (AuthCodeRegex.containsMatchIn(m) || m.contains("Unauthorized", ignoreCase = true) || m.contains("Forbidden", ignoreCase = true)) "，配对码可能已过期或不正确，请在主机面板重新生成配对码。" else ""
                _state.update { it.copy(busy = false, message = "配对失败：$m$hint", messageIsError = true) }
            }
        }
    }

    /** Disconnect: tries to revoke this device on the host first, then clears local
     * credentials either way (a failed revoke must not leave credentials behind);
     * the message says manual revoke is needed only when the revoke was unconfirmed. */
    fun disconnect() {
        if (_state.value.busy) return
        val base = store.serverUrl
        val credential = store.deviceCredential
        // busy 同步置位（同 checkStatus）：双点会发两次撤销，第二发因凭据已撤销
        // 而报错、盖掉第一发的成功文案，误报“主机撤销未确认”；同步置位拦住第二发。
        // 置位同时清掉旧消息：撤销可能要等 15s 超时，旧消息（如上次配对失败）
        // 还挂着会让用户以为这次断开又报了同一个错；终局消息随后会写。
        _state.update { it.copy(busy = true, message = null, messageIsError = false) }
        viewModelScope.launch {
            var revokeError: String? = null
            if (!base.isNullOrBlank() && !credential.isNullOrBlank()) {
                // 撤销失败文案也走翻译：以前直接取原始 message，OkHttp 英文错
                // （如 timeout）会原样进“主机撤销未确认（…）”里，与其余失败路不一致。
                api.revokeDevice(base, credential).onFailure { revokeError = it.userMessage() }
            }
            store.clear()
            val message = if (revokeError == null) {
                "已断开连接，本地凭据已清除。"
            } else {
                "本地凭据已清除，但主机撤销未确认（$revokeError），请在主机上手动撤销该设备。"
            }
            _state.update { it.copy(paired = false, busy = false, message = message, messageIsError = revokeError != null) }
        }
    }
}

class SessionsViewModel(
    private val api: DshRemoteApi,
    private val store: PairingStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    // busy（刷新/加载列表）与 creating（新建会话）是两个独立标志：新建按钮只看 creating，
    // 刷新按钮只看 busy，两者互不阻塞；合并成一个标志会让新建时刷新按钮也被误禁。
    data class SessionsUiState(
        val sessions: List<DshSession> = emptyList(),
        val busy: Boolean = false,
        val creating: Boolean = false,
        // 进行中的删除/重命名会话 id 集合：删除与重命名确认后对话框立刻关闭，
        // 请求却还要跑几秒，行上没有任何反馈、用户会以为没点上而重复点；
        // 用集合不用单个 id：不同行的删除/重命名可以并行，单个标志会被后一个
        // 操作盖掉、前一个操作的反馈提前消失。
        val deletingIds: Set<String> = emptySet(),
        val renamingIds: Set<String> = emptySet(),
        val message: String? = null,
        /** Just-created session, so the list screen can open it at once. */
        val lastCreated: DshSession? = null,
    )

    // 刷新序号：与 loadHistory 的 historyLoadSeq 同模式。轮询与手动刷新
    // （force 绕过 busy 门）可以并行在飞：先发的慢请求若后到，会把后发的
    // 新快照盖回旧数据（用户点了刷新、结果列表反而变旧）；只有最新一次
    // 刷新的结果才落地，旧完成（成功或失败）一律跳过、不碰列表/报错/busy
    // （busy 由最新那次收尾清，旧完成清 busy 会把进行中的刷新指示提前灭掉）。
    private var refreshSeq = 0
    /** Polling calls use the default (guarded) path so a slow host can't pile up requests.
     * Manual refresh (force) bypasses the busy guard on purpose: the user asked for fresh data now. */
    fun refresh(force: Boolean = false) {
        if (_state.value.busy && !force) return
        val base = store.serverUrl ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        val seq = ++refreshSeq
        viewModelScope.launch {
            // Polling must not wipe an error the user is reading; a manual
            // refresh may. A later success always clears a stale error.
            _state.update { it.copy(busy = true, message = if (force) null else it.message) }
            api.listSessions(base, credential)
                // 主机可能返回重复 id；保留第一次出现，UI 再按更新时间倒序。
                .onSuccess { list -> if (seq == refreshSeq) _state.update { it.copy(sessions = list.distinctBy { s -> s.id }, busy = false, message = null) } }
                // 轮询失败不覆盖用户正在看的旧报错（如重命名失败）：上面注释的
                // “轮询不擦掉用户正在读的错”以前只管了成功后清错、没管失败覆盖；
                // 手动刷新开头已把 message 清掉，所以 force 路仍会正常报新错。
                // 旧完成（序号已变）直接跳过：旧失败不能盖掉新刷新的结果或报错。
                .onFailure { e -> if (seq == refreshSeq) { val m = e.userMessage(); _state.update { it.copy(busy = false, message = it.message ?: "加载会话失败：$m${authHint(m)}") } } }
        }
    }

    /** Create a session with a trimmed title (blank becomes “新会话”); on success
     * the new session opens at once via lastCreated, then the list refreshes. */
    fun create(title: String) {
        if (_state.value.creating) return
        val base = store.serverUrl ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        // creating 同步置位（同 checkStatus）：双点会在主机上建出两个会话
        // （新建不是幂等的，lastCreated 只导航进第二个、第一个成孤儿）；同步
        // 置位后第二发直接被 creating 守卫拦住。
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            api.createSession(base, credential, title.trim().ifBlank { "新会话" })
                .onSuccess { created ->
                    // 主机回包 id 为空时不导航：空 id 的聊天页打不开，
                    // 只刷新列表，让用户在列表里点真实会话。
                    if (created.id.isNotBlank()) _state.update { it.copy(lastCreated = created, creating = false) }
                    else _state.update { it.copy(creating = false) }
                    refresh(force = true)
                }
                .onFailure { e -> val m = e.userMessage(); _state.update { it.copy(creating = false, message = "创建失败：$m${authHint(m)}") } }
        }
    }

    fun clearCreated() = _state.update { it.copy(lastCreated = null) }

    fun delete(id: String) {
        if (id.isBlank()) return
        // 同一行重复删除直接拦住：确认框关后行上已有“删除中…”反馈，重复点
        // 只会多发一次幂等请求、再多刷一次列表；不同行不受影响（集合语义）。
        if (id in _state.value.deletingIds) return
        val base = store.serverUrl ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        // 标志同步置位（同 checkStatus）：双点会多发一次删除（虽幂等、404 也按
        // 成功处理，但无谓多一次请求与刷新）；同步置位后第二发直接被集合守卫拦住。
        _state.update { it.copy(deletingIds = it.deletingIds + id) }
        viewModelScope.launch {
            api.deleteSession(base, credential, id)
                // 删除成功先本地去掉该行：以前要等随后那次列表刷新回来行才消失，
                // 网络慢时用户会以为删除没点上、再点一次；随后刷新再确认一次。
                // 失败路不动（行留着 + 报错），不会把没删掉的会话变没。
                // 标志在刷新前清掉：刷新是独立操作（busy 另有标志），不等它结束。
                .onSuccess {
                    _state.update { it.copy(sessions = it.sessions.filterNot { s -> s.id == id }, deletingIds = it.deletingIds - id) }
                    refresh(force = true)
                }
                // 删除是幂等的：主机已无此会话（404）等于删成功，直接刷新，不报失败；
                // 404 路同样本地先去掉该行（与成功路一致——主机说没有，就是没有了），
                // 不然行要等刷新回来才消失，与成功路表现不一致。
                .onFailure { e -> val m = e.userMessage(); if (NotFoundCodeRegex.containsMatchIn(m)) { _state.update { it.copy(sessions = it.sessions.filterNot { s -> s.id == id }, deletingIds = it.deletingIds - id) }; refresh(force = true) } else _state.update { it.copy(deletingIds = it.deletingIds - id, message = "删除失败：$m${authHint(m)}") } }
        }
    }

    fun rename(id: String, title: String) {
        if (id in _state.value.renamingIds) return
        val base = store.serverUrl ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = UNPAIRED_MSG) }; return }
        // 标题去首尾空格再存：否则“ 标题 ”这种带空格的标题会原样进列表，
        // 排序和搜索时都会对不上；全是空格的按空标题处理，不发请求。
        val cleanTitle = title.trim()
        if (id.isBlank() || cleanTitle.isBlank()) return
        // 标志同步置位（同 checkStatus）：弹层保存即关、双点窗口虽窄，但同帧内
        // 第二发仍可溜进协程、多发一次幂等改名；同步置位后直接被集合守卫拦住。
        _state.update { it.copy(renamingIds = it.renamingIds + id) }
        viewModelScope.launch {
            api.renameSession(base, credential, id, cleanTitle)
                .onSuccess {
                    // 成功先本地改掉标题（与删除的乐观去行同款）：刷新回来之前行上
                    // 还是旧标题，慢网络下用户会以为重命名没生效、再改一次；随后刷
                    // 新再确认一次（刷新失败时乐观标题仍留着——主机确实已改名，标题
                    // 本身是对的，报错说的是刷新失败，不是重命名失败）。
                    _state.update { it.copy(sessions = it.sessions.map { s -> if (s.id == id) s.copy(title = cleanTitle) else s }, renamingIds = it.renamingIds - id) }
                    refresh(force = true)
                }
                .onFailure { e -> val m = e.userMessage(); _state.update { it.copy(renamingIds = it.renamingIds - id, message = "重命名失败：$m${authHint(m)}") } }
        }
    }
}

class ChatViewModel(
    private val api: DshRemoteApi,
    private val store: PairingStore,
) : ViewModel() {
    /** Which session the in-flight history load belongs to; stale loads are ignored. */
    private var historySessionId: String? = null
    // 序号与 historySessionId 分工不同：后者只挡跨会话的旧加载，前者挡同会话
    // 连续两次加载的旧快照（A→B→A 时第一次 A 的旧快照可能晚于第二次 A 到达）。
    private var historyLoadSeq = 0
    /** The in-flight stream job, so generation can be stopped by the user. */
    private var streamJob: Job? = null
    // 流序号：每次 send 与每次 loadHistory（它会取消旧流）都 +1；旧流的
    // catch 在跑时若序号已变，说明已有新流接管，旧收尾不许再碰状态——
    // 否则切会话后旧流的取消收尾会把新会话的 streaming 直接清掉。
    private var streamSeq = 0
    // 本地消息序号：以前消息 id 用纯时间戳，时钟回拨（NTP 校准）时两次发送
    // 可能落在同一毫秒、id 撞键（LazyColumn 键、复制态都认 id）；进程内单调
    // 递增的计数器不可能重复。id 只作 UI 键、从不发给主机，格式换掉无影响。
    private var localMsgSeq = 0L
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        // 待审批按会话分开存：以前是单个字段，切会话时 loadHistory 必须清掉它
        // （否则 A 的审批卡会弹在 B 的聊天页里、点允许会拿着 A 的请求 id 发给 B）；
        // 代价是回 A 时卡没了、主机还在等答复，用户再也没法答。现在按会话 id
        // 分开存：切会话只换显示的卡（B 没有就自然不显示），回 A 时卡还在。
        val pendingApprovals: Map<String, ApprovalRequest> = emptyMap(),
        val answeringApproval: Boolean = false,
        val streaming: Boolean = false,
        // 历史正在加载：与 streaming 独立（边加载历史边生成时两者可同时为真），
        // 界面据此在加载时显示加载态而不是空状态，避免空会话闪一下又弹出消息。
        val historyLoading: Boolean = false,
        // 历史加载是否失败过：重试钮的门。以前按 messages.isEmpty() 给重试，
        // R251 的 SWR 之后同会话刷新失败时旧消息还留着、重试钮就没了——
        // 旧消息在、刷新失败，恰恰是最需要“再拉一次”的时候；改成显式标志。
        val historyFailed: Boolean = false,
        val message: String? = null,
        // 报错是否用红色，不能靠文案猜（配对页同款规则）：聊天页消息目前全是
        // 报错、默认 true；上传提示这类信息性消息要显式给 false，否则它会
        // 披着红字、看着像出错。message 为 null 时本标志无意义，不用清。
        val messageIsError: Boolean = true,
        val model: String = "",
        /** (sessionId, text) lost when a send dies mid-stream, so the screen can restore it. */
        val failedDraft: Pair<String, String>? = null,
    )

    fun setModel(v: String) = _state.update { it.copy(model = v.trim()) }

    /** Load history when a session is opened; a failed load shows an error, never fake messages. */
    fun loadHistory(sessionId: String) {
        if (sessionId.isBlank()) {
            // 空 id 不会发起加载：加载态也要清掉，否则没有加载会来清掉它、
            // 加载圈一直转；该分支目前 UI 上不可达，属防御。
            _state.update { it.copy(messages = emptyList(), streaming = false, message = null, historyLoading = false, historyFailed = false) }
            return
        }
        // 切会话先停掉旧会话的流：旧流的迟到审批事件即使落袋，也只进旧会话
        // 的格子（pendingApprovals 按会话分开存），不会弹到新会话的页里；
        // 旧卡也不会被这次加载清掉，回旧会话时卡还在。
        // 取消只是断开客户端接收，主机那边的任务继续，回来靠历史补齐。
        // 序号 +1 让旧流随后跑到的 catch 收尾直接跳过，不碰新会话状态。
        streamSeq++
        streamJob?.cancel()
        // 未配对同样不发起加载：加载态一并清掉，理由同空 id 分支（防御，
        // 目前 UI 上不可达——未配对时进不了聊天页）。
        val base = store.serverUrl ?: run { _state.update { it.copy(message = UNPAIRED_MSG, messageIsError = true, historyLoading = false, historyFailed = false) }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = UNPAIRED_MSG, messageIsError = true, historyLoading = false, historyFailed = false) }; return }
        // 旧会话先记下来：同会话重载时保留旧消息（见下），换会话才清空。
        val prevSessionId = historySessionId
        historySessionId = sessionId
        // 同会话保留的旧消息 id：成功合并时只把“加载开始后新到的”接在快照后面，
        // 保留的旧消息不重复接（id 在调用时主线程快照，无交错）。
        val keepOld = prevSessionId == sessionId
        val keptIds = if (keepOld) _state.value.messages.map { it.id }.toSet() else emptySet()
        val loadSeq = ++historyLoadSeq
        viewModelScope.launch {
            _state.update { s ->
                // 同会话重载（转屏、重试、重进）保留旧消息：以前启动就先清空，
                // 转屏时列表会闪一下空、加载圈一闪而过；旧消息先留着、新快照
                // 到了再替换（成功合并见下）。换会话时必须清空，否则旧会话的
                // 消息会串到新会话页里。
                s.copy(messages = if (keepOld) s.messages else emptyList(), streaming = false, message = null, historyLoading = true, historyFailed = false)
            }
            api.listMessages(base, credential, sessionId)
                .onSuccess { history ->
                    // Ignore a stale load (user already switched sessions).
                    if (historySessionId != sessionId || loadSeq != historyLoadSeq) return@onSuccess
                    _state.update { s ->
                        // 合并分两步：先按 id 滤掉加载开始前就已有的消息（同会话保留
                        // 的旧消息；清空重载时 keptIds 为空、这步不过滤），只剩加载
                        // 开始后新到的；再按（角色，正文）比对快照尾部与新到头部的
                        // 重叠段——快照可能晚于新发送（慢网络下请求到主机时已发完），
                        // 那时快照尾部已有这段对话，只追加不重叠的部分。两步合起来：
                        // 旧消息不重复、新发送不丢、快照已含的不再追加。历史 id 是
                        // h-序号、新发是 u-/a-时间戳，保留的旧消息与快照比 id 对不上，
                        // 所以第二步必须按内容比；空占位（流式中）快照里没有，一定追加。
                        // 但纯占位（空文本且无工具）不参与比对：历史解析已跳过纯占位
                        // （空文本且无工具才跳过），历史里不可能有它；可历史尾巴能是
                        // 空文本带工具的消息（工具-only 回复），与流式占位同角色同空
                        // 文本——若前一条用户文本又恰好相同（重发同一句），比对会误判
                        // 整段重叠、把正在流式的新发送整个丢掉，增量随后无处可落
                        // （占位已不在列表里）、流式回复从视图上消失。带工具或非空
                        // 文本的新到消息仍可比对（已完成的工具-only 交换应去重）。
                        val fresh = s.messages.filterNot { it.id in keptIds }
                        val overlap = (0..minOf(history.size, fresh.size)).lastOrNull { k ->
                            k == 0 || (0 until k).all { i ->
                                val h = history[history.size - k + i]
                                val m = fresh[i]
                                h.role == m.role && h.text == m.text &&
                                    (m.text.isNotBlank() || m.toolCalls.isNotEmpty())
                            }
                        } ?: 0
                        s.copy(messages = history + fresh.drop(overlap), historyLoading = false, historyFailed = false)
                    }
                }
                .onFailure { e ->
                    if (historySessionId != sessionId || loadSeq != historyLoadSeq) return@onFailure
                    val m = e.userMessage()
                    // 404 不是普通失败：会话已被删（多设备删、主机重启丢会话），
                    // 通用“加载历史失败：HTTP 404”只会让用户对着已删的会话反复
                    // 点重试；直接告诉他会话可能没了、回列表看看。重试钮仍保留
                    // 作 fallback（404 若是路由毛刺，重试能恢复），文案是主指引。
                    val msg = if (NotFoundCodeRegex.containsMatchIn(m)) "这个会话可能已被删除，请返回会话列表。" else "加载历史失败：$m${authHint(m)}"
                    _state.update { it.copy(message = msg, messageIsError = true, historyLoading = false, historyFailed = true) }
                }
        }
    }

    /** Send a message: blank text ignored, streaming ignored, unpaired keeps the
     * text as failedDraft, deltas append to the placeholder, Done/Error clear streaming,
     * host Error and transport failure also keep failedDraft, stop cancels the stream job. */
    fun send(sessionId: String, text: String) {
        if (text.isBlank()) return
        val cleanText = text.trim()
        // 未配对时聊天页本来会先清输入框再调 send，等于把用户刚打的字丢了；
        // 这里把原文记成 failedDraft，聊天页的恢复逻辑会把它还回输入框。
        val base = store.serverUrl ?: run {
            _state.update { it.copy(message = UNPAIRED_MSG, messageIsError = true, failedDraft = sessionId to cleanText) }
            return
        }
        val credential = store.deviceCredential ?: run {
            _state.update { it.copy(message = UNPAIRED_MSG, messageIsError = true, failedDraft = sessionId to cleanText) }
            return
        }
        // 双击/快速连点时两次 send 可能都在首个协程把 streaming 置 true 之前
        // 跑过检查，导致两条流并行、消息 id 撞键；检查与置位都在主线程
        // 同步完成（协程里的置位仍保留、无害），第二下直接 return。
        if (_state.value.streaming) return
        _state.update { it.copy(streaming = true) }
        val mySeq = ++streamSeq
        val msgSeq = ++localMsgSeq
        val userMsg = ChatMessage(id = "u-$msgSeq", role = ChatMessage.Role.USER, text = cleanText)
        val assistantId = "a-$msgSeq"
        streamJob = viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        messages = it.messages + userMsg +
                            ChatMessage(id = assistantId, role = ChatMessage.Role.ASSISTANT, text = ""),
                        streaming = true, message = null, failedDraft = null,
                    )
                }
                api.sendMessage(base, credential, sessionId, cleanText, _state.value.model.ifBlank { null })
                    .collect { event ->
                        when (event) {
                            // 增量只在流式进行时追加：错误/完成/停止后若还有增量到达
                            // （如主机在错误后又发了行），直接丢弃，不污染已终局的回复。
                            is ChatEvent.TextDelta -> _state.update { s ->
                                if (!s.streaming) s else s.copy(messages = s.messages.map { m ->
                                    if (m.id == assistantId) m.copy(text = m.text + event.text) else m
                                })
                            }
                            // 工具事件同样只在流式进行时追加：TextDelta 早有这个守卫，
                            // 工具事件漏了——终局（Done/Error）后到的迟到工具事件会改
                            // 已终局的回复摘要。审批事件不加此守卫：审批卡是等用户
                            // 决策的打断，迟到审批若被吞、主机任务会一直等不到答复。
                            is ChatEvent.ToolCallEvent -> _state.update { s ->
                                if (!s.streaming) s else s.copy(messages = s.messages.map { m ->
                                    if (m.id == assistantId) m.copy(toolCalls = m.toolCalls + event.toolCall) else m
                                })
                            }
                            // 审批记在发送所属的会话名下：切会话后旧流万一还有迟到
                            // 审批事件落袋，也只会进旧会话的格子，不会弹到新会话的页里。
                            is ChatEvent.Approval -> _state.update { it.copy(pendingApprovals = it.pendingApprovals + (sessionId to event.request)) }
                            is ChatEvent.Done -> _state.update {
                                it.copy(
                                    streaming = false,
                                    // Done 自带的全文是主机权威版本：增量里被跳过的行
                                    // （如未知字段行）会让累积文本缺一块；主机给了
                                    // 非空全文就用它补齐，没给则保留累积文本。
                                    messages = it.messages.map { m ->
                                        if (m.id == assistantId) m.copy(text = event.fullText.ifBlank { m.text }) else m
                                    }.filterNot { m -> m.id == assistantId && m.text.isBlank() && m.toolCalls.isEmpty() },
                                )
                            }
                            is ChatEvent.Error -> _state.update {
                                it.copy(
                                    streaming = false,
                                    // 流式错误也是 String，不走 Throwable 分支；用同一翻译，网络英文错不再原样显示。
                                    message = event.message.ifBlank { "主机返回了错误，但没有说明原因。" }.toUserMessage() + authHint(event.message),
                                    messageIsError = true,
                                    messages = it.messages.filterNot { m -> m.id == assistantId && m.text.isBlank() && m.toolCalls.isEmpty() },
                                    // 主机报错同样还回原文本：以前只有传输失败与未配对还草稿，
                                    // 主机一报错（如 500、空回复、模型不存在）输入框就空了，
                                    // 长提示词得重打；与传输失败同口径——失败的发送都还回输入框
                                    // 供改完重发（部分回复已在列表里时，恢复的文本只是预填、
                                    // 不自动重发，传输失败同款取舍）。
                                    failedDraft = sessionId to cleanText,
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // User pressed stop: keep any partial reply, drop an empty placeholder.
                // 序号已变说明切会话/新发送已接管，旧收尾跳过（仍原样抛取消）。
                if (mySeq == streamSeq) {
                    _state.update {
                        it.copy(
                            streaming = false,
                            messages = it.messages.filterNot { m -> m.id == assistantId && m.text.isBlank() && m.toolCalls.isEmpty() },
                        )
                    }
                }
                throw e
            } catch (e: Exception) {
                // Transport failure mid-stream (not a host Error event): the
                // stream dies without Done, so clear streaming here or the
                // composer would stay stuck in "generating" forever.
                // 旧流的传输失败同样不许污染新流：报错与 failedDraft 只给当前流。
                if (mySeq == streamSeq) {
                    val m = e.userMessage()
                    _state.update {
                        it.copy(
                            streaming = false,
                            message = "发送失败：$m${authHint(m)}",
                            messageIsError = true,
                            failedDraft = sessionId to cleanText,
                            messages = it.messages.filterNot { m -> m.id == assistantId && m.text.isBlank() && m.toolCalls.isEmpty() },
                        )
                    }
                }
            } finally {
                if (streamJob === coroutineContext[Job]) streamJob = null
            }
        }
    }

    /** Stop generation: cancels the stream job; its catch block tidies state. */
    // 停止生成：有任务时取消任务（流结束分支会清 streaming）；无任务时直接清
    // streaming，防 streaming 卡住一直显示生成中（任务引用已丢、取消不到它时）。
    fun stopGenerating() { streamJob?.cancel() ?: _state.update { it.copy(streaming = false) } }

    fun clearFailedDraft() = _state.update { it.copy(failedDraft = null) }

    fun answerApproval(sessionId: String, approved: Boolean) {
        if (sessionId.isBlank()) return
        val req = _state.value.pendingApprovals[sessionId] ?: return
        if (_state.value.answeringApproval) return
        // 未配对路必须显式给 messageIsError = true：它是报错，却是全仓唯一一处
        // 写非空 message 时不给标志的 ChatViewModel 写入点（R269 全仓核对时漏了
        // 这两处）；标志沿用旧值——上条若是上传提示（false），这条未配对报错就
        // 会披正文色、不披红字。到达路径：审批卡挂着时去设置页清掉凭据、回聊天
        // 页再点允许/拒绝（卡按会话存着，不会因清凭据消失）。
        val base = store.serverUrl ?: run { _state.update { it.copy(message = UNPAIRED_MSG, messageIsError = true) }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = UNPAIRED_MSG, messageIsError = true) }; return }
        _state.update { it.copy(answeringApproval = true) }
        viewModelScope.launch {
            // 之前这里不管回复成没成功都清掉审批卡：网络一断，主机那边还
            // 在等、手机这边却没法再答一次。现在只在成功时清卡，失败时
            // 留卡并报错，用户可以再点一次。
            api.respondToApproval(base, credential, sessionId, req.id, approved)
                // 成功后旧失败消息也要清掉：上次回答失败留下的“审批回复失败”若还挂着，
                // 这次明明成功了、报错行却还在，像是又失败了一次；其余成功路
                // （刷新、加载）同样是成功清旧错，这里补齐。
                // 成功只清审批失败消息（R249 的本意）：无条件清会连同页的发送/
                // 历史报错一起清掉——审批成功与发送失败是两个操作，发送失败的
                // 草稿虽还在输入框里、但失败原因就此消失；发送/历史报错各有
                // 自己的生命周期（下次发送起手清、重试失败重写），不应被无关的
                // 审批成功顺带擦掉。message 为 null 时标志无意义，不用清。
                .onSuccess { _state.update { it.copy(pendingApprovals = it.pendingApprovals - sessionId, answeringApproval = false, message = if (it.message?.startsWith("审批回复失败") == true) null else it.message) } }
                .onFailure { e -> val m = e.userMessage(); _state.update { it.copy(message = "审批回复失败：$m${authHint(m)}", messageIsError = true, answeringApproval = false) } }
        }
    }

    // sessionId/fileName are kept for the future real upload call; the current
    // honest note doesn't need them, so they are intentionally unused for now.
    fun attachNote(sessionId: String, fileName: String) {
        // v1: the attach button is wired to the upload adapter, but the
        // endpoint is not yet verified against a real host (see ApiEndpoints).
        // Show that honestly instead of pretending the upload succeeded.
        _state.update {
            // 文案不指 README：手机上的用户够不着仓库 README，“按 README 步骤
            // 操作”是没法执行的指引；直接说明暂不可用，验证步骤在仓库侧说明。
            // 信息性提示、不是报错：红字会让用户以为点 + 号点出了错；显式给
            // false，走正文色（配对页 messageIsError 同款规则）。
            it.copy(message = "上传功能尚未在真实主机上验证，暂不可用。", messageIsError = false)
        }
    }
}
