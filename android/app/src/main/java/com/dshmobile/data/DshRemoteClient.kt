package com.dshmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OkHttp implementation of DshRemoteApi.
 *
 * Security rules enforced here:
 * - Device credential goes in the `x-dsh-remote-device` header only.
 *   It is never logged, never put in a query string, and never
 *   forwarded anywhere else by this client.
 * - Pairing tokens are used once, in a POST body, and never logged.
 * - Base URLs must be http(s). A user-typed host without a scheme is
 *   normalised to https first (LAN http is allowed only if the user
 *   types http:// explicitly — see PairingScreen's LAN note).
 * - No API key, password, tunnel token or real server URL is baked in.
 */
class DshRemoteClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : DshRemoteApi {

    private fun normalisedBase(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    private fun Request.Builder.deviceHeader(credential: String) =
        header("x-dsh-remote-device", credential)

    private fun jsonBody(json: JSONObject) =
        json.toString().toRequestBody("application/json".toMediaType())

    private fun <T> runCatchingResult(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pairStatus(baseUrl: String): Result<PairStatus> = runCatchingResult {
        val req = Request.Builder().url(ApiEndpoints.pairStatus(normalisedBase(baseUrl))).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Pair status failed: HTTP ${resp.code}")
            val json = JSONObject(body)
            PairStatus(
                paired = json.optBoolean("paired", false),
                requirePairingForLan = json.optBoolean("requirePairingForLan", true),
                phase = json.optString("phase").takeIf { it.isNotEmpty() },
                lanAvailable = if (json.has("lanAvailable")) json.optBoolean("lanAvailable") else null,
            )
        }
    }

    override suspend fun acceptPairing(
        baseUrl: String,
        pairingToken: String,
        deviceName: String,
    ): Result<String> = runCatchingResult {
        // Token is consumed by the host; the response must carry a
        // per-device credential (device id). Never log either value.
        val payload = JSONObject().apply {
            put("token", pairingToken)
            put("deviceName", deviceName)
        }
        val req = Request.Builder()
            .url(ApiEndpoints.pairAccept(normalisedBase(baseUrl)))
            .post(jsonBody(payload))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Pairing failed: HTTP ${resp.code}")
            val json = JSONObject(body)
            json.optString("deviceCredential").ifEmpty {
                json.optString("deviceId")
            }.ifEmpty { throw IllegalStateException("Pairing response carried no device credential") }
        }
    }

    override suspend fun revokeDevice(baseUrl: String, deviceCredential: String): Result<Unit> =
        runCatchingResult {
            val payload = JSONObject().apply { put("revoke", true) }
            val req = Request.Builder()
                .url(ApiEndpoints.pairRevoke(normalisedBase(baseUrl)))
                .deviceHeader(deviceCredential)
                .post(jsonBody(payload))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Revoke failed: HTTP ${resp.code}")
            }
        }

    private fun parseSessions(body: String): List<DshSession> {
        val arr: JSONArray = try {
            JSONObject(body).optJSONArray("sessions") ?: JSONArray(body)
        } catch (_: Exception) {
            JSONArray(body)
        }
        val out = mutableListOf<DshSession>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += DshSession(
                id = o.optString("id"),
                title = o.optString("title").ifEmpty { "Untitled session" },
                model = o.optString("model").takeIf { it.isNotEmpty() },
                updatedAtMs = if (o.has("updatedAt")) o.optLong("updatedAt") else null,
            )
        }
        return out
    }

    override suspend fun listSessions(baseUrl: String, deviceCredential: String): Result<List<DshSession>> =
        runCatchingResult {
            val req = Request.Builder()
                .url(ApiEndpoints.sessions(normalisedBase(baseUrl)))
                .deviceHeader(deviceCredential)
                .get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Sessions failed: HTTP ${resp.code}")
                parseSessions(body)
            }
        }

    override suspend fun createSession(baseUrl: String, deviceCredential: String, title: String): Result<DshSession> =
        runCatchingResult {
            val req = Request.Builder()
                .url(ApiEndpoints.sessions(normalisedBase(baseUrl)))
                .deviceHeader(deviceCredential)
                .post(jsonBody(JSONObject().apply { put("title", title) }))
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Create session failed: HTTP ${resp.code}")
                parseSessions("[$body]").firstOrNull()
                    ?: DshSession(id = JSONObject(body).optString("id"), title = title)
            }
        }

    override suspend fun renameSession(
        baseUrl: String, deviceCredential: String, id: String, title: String,
    ): Result<Unit> = runCatchingResult {
        val req = Request.Builder()
            .url(ApiEndpoints.session(normalisedBase(baseUrl), id))
            .deviceHeader(deviceCredential)
            .post(jsonBody(JSONObject().apply { put("title", title) }))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Rename failed: HTTP ${resp.code}")
        }
    }

    override suspend fun deleteSession(
        baseUrl: String, deviceCredential: String, id: String,
    ): Result<Unit> = runCatchingResult {
        val req = Request.Builder()
            .url(ApiEndpoints.session(normalisedBase(baseUrl), id))
            .deviceHeader(deviceCredential)
            .delete().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Delete failed: HTTP ${resp.code}")
        }
    }

    override fun sendMessage(
        baseUrl: String,
        deviceCredential: String,
        sessionId: String,
        text: String,
        model: String?,
    ): Flow<ChatEvent> = flow {
        try {
            val payload = JSONObject().apply {
                put("message", text)
                if (!model.isNullOrEmpty()) put("model", model)
                put("stream", true)
            }
            val req = Request.Builder()
                .url(ApiEndpoints.sessionChat(normalisedBase(baseUrl), sessionId))
                .deviceHeader(deviceCredential)
                .post(jsonBody(payload))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    emit(ChatEvent.Error("Chat failed: HTTP ${resp.code}"))
                    return@use
                }
                val source = resp.body?.source() ?: run {
                    emit(ChatEvent.Error("Empty chat response"))
                    return@use
                }
                // Best-effort streaming parse: SSE `data: {...}` lines,
                // or plain JSON lines, or one final JSON object.
                val full = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    try {
                        val json = JSONObject(data)
                        when {
                            json.has("delta") -> {
                                val d = json.optString("delta")
                                full.append(d)
                                emit(ChatEvent.TextDelta(d))
                            }
                            json.has("text") -> {
                                val t = json.optString("text")
                                full.append(t)
                                emit(ChatEvent.TextDelta(t))
                            }
                            json.has("toolCall") -> {
                                val tc = json.getJSONObject("toolCall")
                                emit(
                                    ChatEvent.ToolCallEvent(
                                        ToolCall(
                                            id = tc.optString("id"),
                                            name = tc.optString("name"),
                                            argsJson = tc.optString("args"),
                                        )
                                    )
                                )
                            }
                            json.has("approval") -> {
                                val a = json.getJSONObject("approval")
                                emit(
                                    ChatEvent.Approval(
                                        ApprovalRequest(
                                            id = a.optString("id"),
                                            toolName = a.optString("tool"),
                                            description = a.optString("description"),
                                            argsJson = a.optString("args"),
                                        )
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Non-JSON line: treat as raw text.
                        full.append(line).append("\n")
                        emit(ChatEvent.TextDelta(line + "\n"))
                    }
                }
                emit(ChatEvent.Done(full.toString()))
            }
        } catch (e: Exception) {
            emit(ChatEvent.Error(e.message ?: "Chat error"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun respondToApproval(
        baseUrl: String, deviceCredential: String, sessionId: String,
        approvalId: String, approved: Boolean,
    ): Result<Unit> = runCatchingResult {
        val payload = JSONObject().apply {
            put("approvalId", approvalId)
            put("approved", approved)
        }
        val req = Request.Builder()
            .url(ApiEndpoints.sessionChat(normalisedBase(baseUrl), sessionId) + "/approval")
            .deviceHeader(deviceCredential)
            .post(jsonBody(payload))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Approval response failed: HTTP ${resp.code}")
        }
    }

    override suspend fun uploadAttachment(
        baseUrl: String, deviceCredential: String, sessionId: String,
        fileName: String, bytes: ByteArray,
    ): Result<String> = runCatchingResult {
        // TODO (adapter-needed): multipart shape + endpoint not confirmed.
        // This posts raw bytes with a filename header so the failure
        // mode is visible; the adapter must be verified against a
        // real host before claiming uploads work.
        val req = Request.Builder()
            .url(ApiEndpoints.sessionUpload(normalisedBase(baseUrl), sessionId))
            .deviceHeader(deviceCredential)
            .header("x-dsh-upload-filename", fileName)
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Upload failed: HTTP ${resp.code} (adapter not yet verified)")
            body
        }
    }
}
