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

    fun pair(token: String, deviceName: String) {
        val base = _state.value.serverUrl
        if (base.isBlank() || token.isBlank()) {
            _state.update { it.copy(message = "请先填写服务器地址和配对码。") }
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
            if (!base.isNullOrBlank() && !credential.isNullOrBlank()) {
                api.revokeDevice(base, credential)
            }
            store.clear()
            _state.update { it.copy(paired = false, message = "已断开连接，本地凭据已清除。") }
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
    )

    fun refresh() {
        val base = store.serverUrl ?: return
        val credential = store.deviceCredential ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            api.listSessions(base, credential)
                .onSuccess { list -> _state.update { it.copy(sessions = list, busy = false) } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "加载会话失败：${e.message}") } }
        }
    }

    fun create(title: String) {
        val base = store.serverUrl ?: return
        val credential = store.deviceCredential ?: return
        viewModelScope.launch {
            api.createSession(base, credential, title.ifBlank { "新会话" })
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(message = "创建失败：${e.message}") } }
        }
    }

    fun delete(id: String) {
        val base = store.serverUrl ?: return
        val credential = store.deviceCredential ?: return
        viewModelScope.launch {
            api.deleteSession(base, credential, id)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(message = "删除失败：${e.message}") } }
        }
    }
}

class ChatViewModel(
    private val api: DshRemoteApi,
    private val store: PairingStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val pendingApproval: ApprovalRequest? = null,
        val streaming: Boolean = false,
        val message: String? = null,
        val model: String = "",
    )

    fun setModel(v: String) = _state.update { it.copy(model = v) }

    fun send(sessionId: String, text: String) {
        val base = store.serverUrl ?: return
        val credential = store.deviceCredential ?: return
        if (text.isBlank()) return
        val userMsg = ChatMessage(id = "u-${System.currentTimeMillis()}", role = ChatMessage.Role.USER, text = text)
        val assistantId = "a-${System.currentTimeMillis()}"
        viewModelScope.launch {
            _state.update {
                it.copy(
                    messages = it.messages + userMsg +
                        ChatMessage(id = assistantId, role = ChatMessage.Role.ASSISTANT, text = ""),
                    streaming = true, message = null,
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
                        is ChatEvent.Done -> _state.update { it.copy(streaming = false) }
                        is ChatEvent.Error -> _state.update { it.copy(streaming = false, message = event.message) }
                    }
                }
        }
    }

    fun answerApproval(sessionId: String, approved: Boolean) {
        val req = _state.value.pendingApproval ?: return
        val base = store.serverUrl ?: return
        val credential = store.deviceCredential ?: return
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
