package com.dshmobile.data

/** A dsh session (conversation / agent run). SYSTEM-, tool- and
 * function-role messages are host/tool output, not conversation, and are
 * skipped when parsing history (tool/function added after the SYSTEM-only
 * wording below went stale).
 * updatedAtMs 是主机给的 epoch 毫秒（updatedAt/updated_at/updatedAtMs 三种写法都认）；
 * 为空表示主机没给时间戳，列表里沉底。 */
data class DshSession(
    val id: String,
    val title: String,
    val model: String? = null,
    val updatedAtMs: Long? = null,
)

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class ToolCall(
    val id: String,
    val name: String,
    val argsJson: String = "",
    val status: Status = Status.PENDING,
) {
    enum class Status { PENDING, APPROVED, DENIED, RUNNING, DONE, FAILED, UNKNOWN }
}

/** A host-side approval prompt for a tool action. Approving is a security decision. */
data class ApprovalRequest(
    val id: String,
    val toolName: String,
    val description: String,
    val argsJson: String = "",
)

/** Stream events. Done carries the full accumulated text, but the UI already
 * received it via TextDeltas; Done mainly signals end-of-stream. */
sealed class ChatEvent {
    data class TextDelta(val text: String) : ChatEvent()
    data class ToolCallEvent(val toolCall: ToolCall) : ChatEvent()
    data class Approval(val request: ApprovalRequest) : ChatEvent()
    data class Done(val fullText: String) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}

/** Pairing status from /api/pair/status. phase and lanAvailable are parsed
 * but not currently displayed; requirePairingForLan defaults to true (secure). */
data class PairStatus(
    val paired: Boolean,
    val requirePairingForLan: Boolean = true,
    val phase: String? = null,
    val lanAvailable: Boolean? = null,
)
