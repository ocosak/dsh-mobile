package com.dshmobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dshmobile.data.ApprovalRequest
import com.dshmobile.data.ChatEvent
import com.dshmobile.data.ChatMessage
import com.dshmobile.data.DshRemoteApi
import com.dshmobile.data.DshSession
import com.dshmobile.data.PairingStore
import com.dshmobile.data.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    )

    fun onServerUrlChange(v: String) = _state.update { it.copy(serverUrl = v) }

    /**
     * 设置页的服务器地址原来只改 ViewModel 状态、不写 store：
     * 切到会话页用的是 store 里的旧地址，重启后改动直接丢了。
     * 这个保存按钮把当前地址（去首尾空格、去尾斜杠）写进 store，
     * 配对页的 pair() 成功时同样只存整理后的地址。
     */
    // 服务器地址先整理再校验：去掉首尾空格和尾斜杠（尾斜杠会让拼接
    // 的接口路径多一个斜杠），再要求必须以 http:// 或 https:// 开头，
    // 否则 OkHttp 只能报看不懂的错，用户不知道是地址本身写错了。
    private fun cleanedBase(): String = _state.value.serverUrl.trim().trimEnd('/')
    private fun isHttpUrl(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    fun saveServerUrl() {
        val cleaned = cleanedBase()
        if (cleaned.isBlank()) {
            _state.update { it.copy(message = "请先填写服务器地址。") }
            return
        }
        if (!isHttpUrl(cleaned)) {
            _state.update { it.copy(message = "服务器地址要以 http:// 或 https:// 开头，例如 http://192.168.1.10:8080。") }
            return
        }
        store.serverUrl = cleaned
        _state.update { it.copy(serverUrl = cleaned, message = "服务器地址已保存。") }
    }

    /** Check the host is reachable and report its pairing phase, without pairing. */
    fun checkStatus() {
        val base = cleanedBase()
        if (base.isBlank()) {
            _state.update { it.copy(message = "请先填写服务器地址。") }
            return
        }
        if (!isHttpUrl(base)) {
            _state.update { it.copy(message = "服务器地址要以 http:// 或 https:// 开头，例如 http://192.168.1.10:8080。") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            api.pairStatus(base)
                .onSuccess { status ->
                    val pairedText = if (status.paired) "已有设备配对" else "尚未配对"
                    val lanText = if (status.requirePairingForLan) "局域网需要配对" else "局域网无需配对"
                    _state.update { it.copy(busy = false, message = "服务器可达：$pairedText，$lanText。") }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "服务器不可达：${e.message}") } }
        }
    }

    fun pair(token: String, deviceName: String) {
        val base = cleanedBase()
        if (base.isBlank() || token.isBlank()) {
            _state.update { it.copy(message = "请先填写服务器地址和配对码。") }
            return
        }
        if (!isHttpUrl(base)) {
            _state.update { it.copy(message = "服务器地址要以 http:// 或 https:// 开头，例如 http://192.168.1.10:8080。") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = api.acceptPairing(base, token.trim(), deviceName.ifBlank { "安卓手机" })
            result.onSuccess { credential ->
                store.serverUrl = base
                store.deviceCredential = credential
                store.deviceName = deviceName.ifBlank { "安卓手机" }
                _state.update { it.copy(busy = false, paired = true, message = "已配对。本设备现在可以操控主机，请保持手机锁定。") }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, message = "配对失败：${e.message}") }
            }
        }
    }

    fun disconnect() {
        val base = store.serverUrl
        val credential = store.deviceCredential
        viewModelScope.launch {
            var revokeError: String? = null
            if (!base.isNullOrBlank() && !credential.isNullOrBlank()) {
                api.revokeDevice(base, credential).onFailure { revokeError = it.message }
            }
            store.clear()
            val message = if (revokeError == null) {
                "已断开连接，本地凭据已清除。"
            } else {
                "本地凭据已清除，但主机撤销未确认（$revokeError），请在主机上手动撤销该设备。"
            }
            _state.update { it.copy(paired = false, message = message) }
        }
    }
}

class SessionsViewModel(
    private val api: DshRemoteApi,
    private val store: PairingStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    data class SessionsUiState(
        val sessions: List<DshSession> = emptyList(),
        val busy: Boolean = false,
        val message: String? = null,
        /** Just-created session, so the list screen can open it at once. */
        val lastCreated: DshSession? = null,
    )

    /** Polling calls use the default (guarded) path so a slow host can't pile up requests. */
    fun refresh(force: Boolean = false) {
        if (_state.value.busy && !force) return
        val base = store.serverUrl ?: return
        val credential = store.deviceCredential ?: return
        viewModelScope.launch {
            // Polling must not wipe an error the user is reading; a manual
            // refresh may. A later success always clears a stale error.
            _state.update { it.copy(busy = true, message = if (force) null else it.message) }
            api.listSessions(base, credential)
                .onSuccess { list -> _state.update { it.copy(sessions = list, busy = false, message = null) } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "加载会话失败：${e.message}") } }
        }
    }

    fun create(title: String) {
        val base = store.serverUrl ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        viewModelScope.launch {
            api.createSession(base, credential, title.ifBlank { "新会话" })
                .onSuccess { created ->
                    _state.update { it.copy(lastCreated = created) }
                    refresh(force = true)
                }
                .onFailure { e -> _state.update { it.copy(message = "创建失败：${e.message}") } }
        }
    }

    fun clearCreated() = _state.update { it.copy(lastCreated = null) }

    fun delete(id: String) {
        val base = store.serverUrl ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        viewModelScope.launch {
            api.deleteSession(base, credential, id)
                .onSuccess { refresh(force = true) }
                .onFailure { e -> _state.update { it.copy(message = "删除失败：${e.message}") } }
        }
    }

    fun rename(id: String, title: String) {
        val base = store.serverUrl ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        if (title.isBlank()) return
        viewModelScope.launch {
            api.renameSession(base, credential, id, title)
                .onSuccess { refresh(force = true) }
                .onFailure { e -> _state.update { it.copy(message = "重命名失败：${e.message}") } }
        }
    }
}

class ChatViewModel(
    private val api: DshRemoteApi,
    private val store: PairingStore,
) : ViewModel() {
    /** Which session the in-flight history load belongs to; stale loads are ignored. */
    private var historySessionId: String? = null
    /** The in-flight stream job, so generation can be stopped by the user. */
    private var streamJob: Job? = null
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val pendingApproval: ApprovalRequest? = null,
        val streaming: Boolean = false,
        val message: String? = null,
        val model: String = "",
        /** (sessionId, text) lost when a send dies mid-stream, so the screen can restore it. */
        val failedDraft: Pair<String, String>? = null,
    )

    fun setModel(v: String) = _state.update { it.copy(model = v) }

    /** Load history when a session is opened; a failed load shows an error, never fake messages. */
    fun loadHistory(sessionId: String) {
        val base = store.serverUrl ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        historySessionId = sessionId
        viewModelScope.launch {
            _state.update { it.copy(messages = emptyList(), pendingApproval = null, streaming = false, message = null) }
            api.listMessages(base, credential, sessionId)
                .onSuccess { history ->
                    // Ignore a stale load (user already switched sessions).
                    // If the user already sent a message while loading, keep it after the history.
                    if (historySessionId != sessionId) return@onSuccess
                    _state.update { s ->
                        s.copy(messages = if (s.streaming) history + s.messages else history)
                    }
                }
                .onFailure { e ->
                    if (historySessionId != sessionId) return@onFailure
                    _state.update { it.copy(message = "加载历史失败：${e.message}") }
                }
        }
    }

    fun send(sessionId: String, text: String) {
        if (_state.value.streaming) return
        // 未配对时聊天页本来会先清输入框再调 send，等于把用户刚打的字丢了；
        // 这里把原文记成 failedDraft，聊天页的恢复逻辑会把它还回输入框。
        val base = store.serverUrl ?: run {
            _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。", failedDraft = sessionId to text) }
            return
        }
        val credential = store.deviceCredential ?: run {
            _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。", failedDraft = sessionId to text) }
            return
        }
        if (text.isBlank()) return
        val userMsg = ChatMessage(id = "u-${System.currentTimeMillis()}", role = ChatMessage.Role.USER, text = text)
        val assistantId = "a-${System.currentTimeMillis()}"
        val job = viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        messages = it.messages + userMsg +
                            ChatMessage(id = assistantId, role = ChatMessage.Role.ASSISTANT, text = ""),
                        streaming = true, message = null, failedDraft = null,
                    )
                }
                api.sendMessage(base, credential, sessionId, text, _state.value.model.ifBlank { null })
                    .collect { event ->
                        when (event) {
                            is ChatEvent.TextDelta -> _state.update { s ->
                                s.copy(messages = s.messages.map { m ->
                                    if (m.id == assistantId) m.copy(text = m.text + event.text) else m
                                })
                            }
                            is ChatEvent.ToolCallEvent -> _state.update { s ->
                                s.copy(messages = s.messages.map { m ->
                                    if (m.id == assistantId) m.copy(toolCalls = m.toolCalls + event.toolCall) else m
                                })
                            }
                            is ChatEvent.Approval -> _state.update { it.copy(pendingApproval = event.request) }
                            is ChatEvent.Done -> _state.update {
                                it.copy(
                                    streaming = false,
                                    messages = it.messages.filterNot { m -> m.id == assistantId && m.text.isEmpty() },
                                )
                            }
                            is ChatEvent.Error -> _state.update {
                                it.copy(
                                    streaming = false,
                                    message = event.message,
                                    messages = it.messages.filterNot { m -> m.id == assistantId && m.text.isEmpty() },
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // User pressed stop: keep any partial reply, drop an empty placeholder.
                _state.update {
                    it.copy(
                        streaming = false,
                        messages = it.messages.filterNot { m -> m.id == assistantId && m.text.isEmpty() },
                    )
                }
                throw e
            } catch (e: Exception) {
                // Transport failure mid-stream (not a host Error event): the
                // stream dies without Done, so clear streaming here or the
                // composer would stay stuck in "generating" forever.
                _state.update {
                    it.copy(
                        streaming = false,
                        message = "发送失败：${e.message}",
                        failedDraft = sessionId to text,
                        messages = it.messages.filterNot { m -> m.id == assistantId && m.text.isEmpty() },
                    )
                }
            } finally {
                if (streamJob === coroutineContext[Job]) streamJob = null
            }
        }
        streamJob = job
    }

    /** Stop generation: cancels the stream job; its catch block tidies state. */
    fun stopGenerating() { streamJob?.cancel() }

    fun clearFailedDraft() = _state.update { it.copy(failedDraft = null) }

    fun answerApproval(sessionId: String, approved: Boolean) {
        val req = _state.value.pendingApproval ?: return
        val base = store.serverUrl ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        val credential = store.deviceCredential ?: run { _state.update { it.copy(message = "尚未配对，请先在配对页连接主机。") }; return }
        viewModelScope.launch {
            api.respondToApproval(base, credential, sessionId, req.id, approved)
            _state.update { it.copy(pendingApproval = null) }
        }
    }

    fun attachNote(sessionId: String, fileName: String) {
        // v1: the attach button is wired to the upload adapter, but the
        // endpoint is not yet verified against a real host (see ApiEndpoints).
        // Show that honestly instead of pretending the upload succeeded.
        _state.update {
            it.copy(message = "上传功能尚未在真实主机上验证（会话 $sessionId，文件 $fileName），请按 README 的验证步骤操作。")
        }
    }
}
