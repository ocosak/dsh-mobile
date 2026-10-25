package com.dshmobile.data

import kotlinx.coroutines.flow.Flow

/**
 * UI-facing contract. The UI never knows URLs, headers or JSON shapes;
 * it only talks to this interface, so a real dsh host can be swapped
 * in by changing DshRemoteClient + ApiEndpoints only.
 */
interface DshRemoteApi {
    suspend fun pairStatus(baseUrl: String): Result<PairStatus>

    /** Exchange a one-time pairing token for a per-device credential. */
    suspend fun acceptPairing(baseUrl: String, pairingToken: String, deviceName: String): Result<String>

    suspend fun revokeDevice(baseUrl: String, deviceCredential: String): Result<Unit>

    suspend fun listSessions(baseUrl: String, deviceCredential: String): Result<List<DshSession>>
    suspend fun createSession(baseUrl: String, deviceCredential: String, title: String): Result<DshSession>
    suspend fun renameSession(baseUrl: String, deviceCredential: String, id: String, title: String): Result<Unit>
    suspend fun deleteSession(baseUrl: String, deviceCredential: String, id: String): Result<Unit>

    /**
     * Load a session's message history (desktop parity: opening a
     * session shows its history, not an empty screen).
     * TODO (adapter-needed): response shape not confirmed against a
     * real host; the client parses tolerantly and surfaces failures.
     */
    suspend fun listMessages(
        baseUrl: String,
        deviceCredential: String,
        sessionId: String,
    ): Result<List<ChatMessage>>

    /** Streaming chat. Emits text deltas, tool calls and approval prompts. */
    fun sendMessage(
        baseUrl: String,
        deviceCredential: String,
        sessionId: String,
        text: String,
        model: String?,
    ): Flow<ChatEvent>

    suspend fun respondToApproval(
        baseUrl: String,
        deviceCredential: String,
        sessionId: String,
        approvalId: String,
        approved: Boolean,
    ): Result<Unit>

    /**
     * Upload an attachment for a session.
     * TODO (adapter-needed): endpoint not confirmed against a real host.
     * Callers must surface the failure honestly; never silently drop a file.
     */
    suspend fun uploadAttachment(
        baseUrl: String,
        deviceCredential: String,
        sessionId: String,
        fileName: String,
        bytes: ByteArray,
    ): Result<String>
}
