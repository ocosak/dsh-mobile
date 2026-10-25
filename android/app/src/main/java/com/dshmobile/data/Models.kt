package com.dshmobile.data

/** A dsh session (conversation / agent run). */
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
    enum class Status { PENDING, APPROVED, DENIED, RUNNING, DONE, FAILED }
}

/** A host-side approval prompt for a tool action. Approving is a security decision. */
data class ApprovalRequest(
    val id: String,
    val toolName: String,
    val description: String,
    val argsJson: String = "",
)

sealed class ChatEvent {
    data class TextDelta(val text: String) : ChatEvent()
    data class ToolCallEvent(val toolCall: ToolCall) : ChatEvent()
    data class Approval(val request: ApprovalRequest) : ChatEvent()
    data class Done(val fullText: String) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}

data class PairStatus(
    val paired: Boolean,
    val requirePairingForLan: Boolean = true,
    val phase: String? = null,
    val lanAvailable: Boolean? = null,
)
