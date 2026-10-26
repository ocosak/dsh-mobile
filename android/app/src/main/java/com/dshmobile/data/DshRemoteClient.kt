package com.dshmobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * OkHttp implementation of DshRemoteApi.
 *
 * Security rules enforced here:
 * - Device credential goes in the `x-dsh-remote-device` header only.
 *   It is never logged, never put in a query string, and never
 *   forwarded anywhere else by this client.
 * - Pairing tokens are used once, in a POST body, and never logged.
 * - Base URLs are normalised by normalisedBase(): a user-typed host without a scheme is
 *   normalised to http for local-looking hosts (localhost, *.local,
 *   10.x, 172.16-31.x, 192.168.x) and to https otherwise.
 * - No API key, password, tunnel token or real server URL is baked in.
 */
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 120L
private const val WRITE_TIMEOUT_SECONDS = 60L
private const val PAIR_STATUS_TIMEOUT_SECONDS = 30L

class DshRemoteClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        // Connect fails fast (15s); reads allow long host processing (120s);
        // writes allow uploads (60s). Streaming uses its own unlimited client.
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 不自动重试：本应用请求多有副作用（新建会话、配对、审批），连接失败时静默重发
        // 可能在主机上落下重复会话/设备；失败直接报给用户，由用户明确点重试更安全。
        .retryOnConnectionFailure(false)
        .build(),
) : DshRemoteApi {

    // 流式客户端只建一次（lazy，复用连接池）：每次发送都 newBuilder 会新建连接池和线程池，
    // 浪费且复用不到长连接；普通请求仍用默认 client（120s 读取超时）。
    private val streamHttp: OkHttpClient by lazy { http.newBuilder().readTimeout(0, TimeUnit.SECONDS).build() }

    private fun normalisedBase(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        // scheme 不区分大小写：用户从别处粘来的 "HTTP://..." 以前因大小写对不上、
        // 被当成裸地址又拼一层 https，拼出 "https://HTTP://..." 这种坏地址。
        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        // 地址栏只填主机（scheme + host + port）：带路径/查询/片段（如 /dsh、
        // ?x=1）会原样拼进 API 路径里、让所有接口 404；这里只留 authority 部分。
        // 无路径输入时 authority 与 trimmed 相同，旧行为不变。
        val afterScheme = if (hasScheme) trimmed.substringAfter("://") else trimmed
        val rawAuthority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        // 裸 ::1（无括号、无端口）只能是回环地址，不存在歧义：这里补括号，
        // 后续 host 判定与拼地址都用补括号后的形——只改判定不改拼地址的话，
        // 仍会拼出 http://::1 这种非法 URL。带端口的裸形（如 ::1:8080）无法
        // 区分端口与地址段，按既定规则不猜、仍走原逻辑（请求层报无效地址，
        // 由用户改写成 [::1]:8080）。
        val authority = if (rawAuthority == "::1") "[::1]" else rawAuthority
        if (hasScheme) return trimmed.substringBefore("://") + "://" + authority
        // A bare address with no scheme used to become https, which silently
        // broke LAN hosts (they are almost always plain http). Local-looking
        // hosts default to http; everything else still defaults to https.
        // IPv6 回环要先按方括号取 host：直接 substringBefore(':') 会把 "[::1]:8080"
        // 截成 "["，回环判定失败、误走 https；方括号内才是完整 host。
        val host = if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
        val secondOctet = host.split(".").getOrNull(1)?.toIntOrNull()
        // 域名不区分大小写："EXAMPLE.LOCAL" 以前因大小写对不上误走 https。
        val isLocal = host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "[::1]" || host.endsWith(".local", ignoreCase = true) ||
            host.startsWith("192.168.") || host.startsWith("10.") ||
            (host.startsWith("172.") && secondOctet != null && secondOctet in 16..31)
        return (if (isLocal) "http://" else "https://") + authority
    }

    // 凭据只走 x-dsh-remote-device 请求头，绝不进 URL、不记日志（见类注释安全规则）。
    private fun Request.Builder.deviceHeader(credential: String) =
        header("x-dsh-remote-device", credential)

    private fun jsonBody(json: JSONObject) =
        json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    /** 阻塞调用包一层 Result：JSON 解析失败换成中文格式提示，其他异常原样以失败返回。 */
    private fun <T> runCatchingResult(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: JSONException) {
        // 地址填错指向了非 dsh 服务时，返回的往往是 HTML，JSON 解析
        // 失败的原始异常文案用户看不懂；统一换成可操作的中文提示。
        Result.failure(IllegalStateException("服务器返回的内容不是预期的格式，可能是地址填错了（指向了非 dsh 服务）", e))
    } catch (e: CancellationException) {
        // 取消不是失败：以前会把 CancellationException 包进 Result.failure，
        // 调用方按普通失败处理、协程的取消语义就丢了；原样抛出，与流式
        // sendMessage 里已有的取消原样传播保持一致。
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 非 2xx 时尽量带上主机给的 {"error"} 详情：以前只给 HTTP 码（流式 R223
    // 已补，其余阻塞接口还丢着）；body 已读的站点直接传 body 串，未读的站点
    // 在判失败时读一次（use 块内、失败路才读，成功路不浪费）。解析失败（HTML
    // 报错页、数组体）时返回空后缀，不把解析错盖在 HTTP 码上。
    private fun errorSuffix(body: String): String {
        val detail = try { JSONObject(body).optString("error").trim() } catch (_: Exception) { "" }
        return if (detail.isNotBlank()) "：$detail" else ""
    }

    // 阻塞式 API 一律走 IO 线程：调用方是 viewModelScope（主线程），OkHttp
    // execute() 是阻塞调用；慢速/不可达主机时 15s 连接超时远超 5s ANR 阈值，
    // 主线程卡 15s 会直接 ANR。取消原样抛出（runCatchingResult 已保证），不吞取消。
    private suspend fun <T> ioResult(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatchingResult(block) }

    override suspend fun pairStatus(baseUrl: String): Result<PairStatus> = ioResult {
        val req = Request.Builder().url(ApiEndpoints.pairStatus(normalisedBase(baseUrl))).get().build()
        // 健康检查是快速探针：共享客户端读超时 120s 是为数据操作留的，探针若
        // 挂起（主机已接受连接但迟迟不回）会让“检查中…”卡 120 秒，且检查钮
        // busy 时禁用、无取消路（加取消钮需存 Job 改结构，R277 已否决）；单次
        // 调用设 30 秒总期限（连接+读全程），超时按连接超时翻译，不动共享
        // 客户端——其余 9 个数据接口仍用 120s。30 秒对局域网探针已极宽裕。
        val call = http.newCall(req)
        call.timeout().timeout(PAIR_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        call.execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(body))
            val json = JSONObject(body)
            PairStatus(
                // 缺 paired 字段默认 false（安全默认值：不假设已配对）。
                paired = json.optBoolean("paired", false),
                requirePairingForLan = json.optBoolean("requirePairingForLan", true),
                phase = json.optString("phase").trim().takeIf { it.isNotBlank() },
                // 缺 lanAvailable 字段留 null（未知），不是 false（不可用），两者语义不同。
                lanAvailable = if (json.has("lanAvailable")) json.optBoolean("lanAvailable") else null,
            )
        }
    }

    override suspend fun acceptPairing(
        baseUrl: String,
        pairingToken: String,
        deviceName: String,
    ): Result<String> = ioResult {
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
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(body))
            // 空体不是“地址填错”（那是 JSON 解析失败的提示），而是主机没给凭据；
            // 直接报无凭据，否则用户会去查地址、查错方向。
            if (body.isBlank()) throw IllegalStateException("配对返回中没有设备凭据")
            val json = JSONObject(body)
            // 主机给的拒绝原因别丢：2xx + {"error": ...} 形回包时，凭据缺失的
            // 抛错若不带原因，用户只看到“没有凭据”、不知道主机为什么拒绝；
            // 带上后配对提示的 word 识别（Unauthorized/Forbidden）也能接住它。
            val hostError = json.optString("error").trim()
            val detail = if (hostError.isNotBlank()) "：$hostError" else ""
            json.optString("deviceCredential").trim().ifEmpty {
                json.optString("deviceId").trim()
            }.ifEmpty { throw IllegalStateException("配对返回中没有设备凭据$detail") }
        }
    }

    override suspend fun revokeDevice(baseUrl: String, deviceCredential: String): Result<Unit> =
        ioResult {
            val payload = JSONObject().apply { put("revoke", true) }
            val req = Request.Builder()
                .url(ApiEndpoints.pairRevoke(normalisedBase(baseUrl)))
                .deviceHeader(deviceCredential)
                .post(jsonBody(payload))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(resp.body?.string().orEmpty()))
            }
        }

    // ISO 8601 字符串时间戳转 epoch 毫秒；解析失败返回 null（未知），不抛错。
    // 只在数字取不到时用（optLong 对非数字串返回 0，0 会被误当成 1970）。
    private fun parseIsoToMs(raw: String): Long? {
        // XX 补无冒号时区（+0800，XXX 只认 +08:00 与 Z）；空格分隔符补非标准
        // ISO 写法（部分主机会把 T 写成空格）；每种失败都换下一个试，不抛错。
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "yyyy-MM-dd'T'HH:mm:ssXX",
            // 无时区 T 形：与空格分隔形同类（部分主机不带时区），SimpleDateFormat
            // 无时区时按系统默认时区解，与空格形口径一致；带毫秒与不带毫秒各一式。
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (pattern in patterns) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(raw.trim())
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // 换下一个格式试。
            }
        }
        return null
    }

    // 取 epoch 毫秒：数字（含数字串）直接认；非数字串（如 ISO 时间）走 ISO 解析；
    // 都取不到返回 null（未知），不返回 0（0 会被误当成 1970 年的会话）。
    private fun optEpochMs(o: JSONObject, key: String): Long? {
        if (!o.has(key)) return null
        return o.optLong(key).takeIf { it != 0L } ?: parseIsoToMs(o.optString(key))
    }

    private fun parseSessions(body: String): List<DshSession> {
        // 先按数组解（列表接口最常见的形），再按对象解：包裹键、最后裸对象
        // 当成单条会话。以前是对象优先、catch 里把同一个 JSONArray(body) 再跑
        // 一遍——第一次抛、第二次必抛，等于没容忍；裸对象回包会直接走失败路，
        // 现在当成 1 条（无 id 仍会被下面的循环跳过，不会凭空造出会话）。
        // 仍不是合法 JSON 时 JSONObject 照常抛、走失败路，不静默吞掉。
        val arr: JSONArray = try {
            JSONArray(body)
        } catch (_: Exception) {
            val obj = JSONObject(body)
            obj.optJSONArray("sessions")
                ?: obj.optJSONArray("data")
                ?: obj.optJSONArray("items")
                ?: JSONArray().put(obj)
        }
        val out = mutableListOf<DshSession>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            // 没有 id 的会话打不开（打开要靠 id 拼路由），以前会原样进列表，
            // 点它只会去一个空 id 的聊天页；直接跳过。id 只取一次：以前检查
            // 与字段各取一遍 trim，改一处漏另一处就会检查与实际不一致。
            val id = o.optString("id").trim()
            if (id.isBlank()) continue
            out += DshSession(
                id = id,
                title = o.optString("title").trim().ifEmpty { "未命名会话" },
                model = o.optString("model").trim().takeIf { it.isNotBlank() },
                // 主机可能用 updatedAt / updated_at / updatedAtMs 三种写法，
                // 只认第一种会把另外两种的会话都归到“更早”里；
                // 每种写法都可能是数字或 ISO 字符串，optEpochMs 两路都认。
                updatedAtMs = optEpochMs(o, "updatedAt")
                    ?: optEpochMs(o, "updated_at")
                    ?: optEpochMs(o, "updatedAtMs"),
            )
        }
        return out
    }

    override suspend fun listSessions(baseUrl: String, deviceCredential: String): Result<List<DshSession>> =
        ioResult {
            val req = Request.Builder()
                .url(ApiEndpoints.sessions(normalisedBase(baseUrl)))
                .deviceHeader(deviceCredential)
                .get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(body))
                // 空体表示空列表，不是格式错误；以前会走到 JSONArray("") 抛英文错。
                if (body.isBlank()) return@ioResult emptyList()
                parseSessions(body)
            }
        }

    override suspend fun createSession(baseUrl: String, deviceCredential: String, title: String): Result<DshSession> =
        ioResult {
            val req = Request.Builder()
                .url(ApiEndpoints.sessions(normalisedBase(baseUrl)))
                .deviceHeader(deviceCredential)
                .post(jsonBody(JSONObject().apply { put("title", title) }))
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(body))
                // 回包容忍链：{session}/{data} 包裹优先，其次单对象数组，最后直接 id。
                // body 只解析一次成 JSONObject：以前三条链各解析一遍，同一串解析三次。
                val bodyJson = try { JSONObject(body) } catch (_: Exception) { null }
                val nested = bodyJson?.let { j ->
                    (j.optJSONObject("session") ?: j.optJSONObject("data"))?.let { o ->
                        DshSession(
                            id = o.optString("id").trim(),
                            title = o.optString("title").trim().ifEmpty { title },
                            model = o.optString("model").trim().takeIf { it.isNotBlank() },
                        )
                    }
                }
                // 创建回包取首项：R257 之后 parseSessions 本身已容忍数组、包裹、
                // 裸对象三种形，不再需要先判 "[" 再包一层方括号——以前的分支
                // 是为了绕开旧 parseSessions 不认裸对象，现在直接调一次即可；
                // 三种形的结果与旧分支逐一等价（包裹体仍由 nested 链接管）。
                val wrapped = try {
                    parseSessions(body).firstOrNull()
                } catch (_: Exception) { null }
                val directId = bodyJson?.optString("id").trim().orEmpty()
                // 包裹对象 id 为空时不能短路整条链：以前 nested 非空就直接取它，
                // 哪怕它的 id 是空串，顶层直接 id 有效也会被跳过、白白报“没有会话 id”。
                (nested?.takeIf { it.id.isNotBlank() } ?: wrapped?.takeIf { it.id.isNotEmpty() } ?: DshSession(id = directId, title = title))
                    .takeIf { it.id.isNotBlank() }
                    ?: throw IllegalStateException("创建返回中没有会话 id")
            }
        }

    override suspend fun renameSession(
        baseUrl: String, deviceCredential: String, id: String, title: String,
    ): Result<Unit> = ioResult {
        val req = Request.Builder()
            .url(ApiEndpoints.session(normalisedBase(baseUrl), id))
            .deviceHeader(deviceCredential)
            .post(jsonBody(JSONObject().apply { put("title", title) }))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(resp.body?.string().orEmpty()))
        }
    }

    override suspend fun listMessages(
        baseUrl: String,
        deviceCredential: String,
        sessionId: String,
    ): Result<List<ChatMessage>> = ioResult {
        val req = Request.Builder()
            .url(ApiEndpoints.sessionMessages(normalisedBase(baseUrl), sessionId))
            .deviceHeader(deviceCredential)
            .get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(body))
            // 空体表示空历史，不是格式错误；以前会走到 JSONArray("") 抛英文错。
            if (body.isBlank()) return@ioResult emptyList()
            // 与 R257 的 parseSessions 同构：数组优先、再对象（包裹键、最后裸对象
            // 当成单条消息）；以前对象优先、catch 里把同一个 JSONArray(body) 再跑
            // 一遍，裸对象回包必定二次抛、走失败路。包裹键与 parseSessions 对齐
            // （messages/data/items）：以前这里漏了 items，{"items": [...]} 形
            // 回包会把整个对象当成一条消息、因无正文被跳过，最终显示空历史。
            val arr: JSONArray = try {
                JSONArray(body)
            } catch (_: Exception) {
                val obj = JSONObject(body)
                obj.optJSONArray("messages")
                    ?: obj.optJSONArray("data")
                    ?: obj.optJSONArray("items")
                    ?: JSONArray().put(obj)
            }
            val out = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                // 角色比较不用 lowercase()：与工具状态同理，语系无关的
                // equals(ignoreCase) 更稳；当前被比较的词虽不含 I，但以后加带
                // I 的角色也不会踩土耳其语系的坑。
                val roleRaw = o.optString("role").trim()
                // 系统消息是主机给智能体的指令、工具消息是工具输出，都不是对话内容；
                // 以前只跳过系统消息，工具消息会当成助手回复显示出来，同样张冠李戴。
                // function 是旧式工具输出角色，与 tool 同义，一并跳过。
                if (roleRaw.equals("system", true) || roleRaw.equals("tool", true) || roleRaw.equals("function", true)) continue
                val text = o.optString("text").trim()
                    .ifEmpty { o.optString("content").trim() }
                    .ifEmpty { o.optString("message").trim() }
                // 历史里的工具调用：流式里工具摘要是可见的，历史若不解析、重进后
                // 摘要全丢（纯工具消息整条丢、带正文的丢摘要），live 与历史不一致。
                // 字段形 tolerant 复用流式词汇：数组 toolCalls/tool_calls、单个对象
                // toolCall；主机若不用这些形，opt* 返回空、行为与以前完全一致，
                // 不猜死一种形（与 text/content/message、sessions/data/items 同款
                // tolerant 口径）。无状态的历史工具给 UNKNOWN（见 parseToolStatus）。
                val toolCalls = mutableListOf<ToolCall>()
                val toolArr = o.optJSONArray("toolCalls") ?: o.optJSONArray("tool_calls")
                if (toolArr != null) {
                    for (j in 0 until toolArr.length()) {
                        toolArr.optJSONObject(j)?.let { toolCalls += parseToolCall(it, ToolCall.Status.UNKNOWN) }
                    }
                } else {
                    o.optJSONObject("toolCall")?.let { toolCalls += parseToolCall(it, ToolCall.Status.UNKNOWN) }
                }
                // 空文本只在同时无工具时才跳过：纯工具消息（无正文、有调用）是
                // 有效对话内容，聊天页空白正文有工具时本就画摘要行（R215）。
                if (text.isBlank() && toolCalls.isEmpty()) continue
                out += ChatMessage(
                    id = "h-$i",
                    role = if (roleRaw.equals("user", true) || roleRaw.equals("human", true)) ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT,
                    text = text,
                    toolCalls = toolCalls,
                )
            }
            out
        }
    }

    override suspend fun deleteSession(
        baseUrl: String, deviceCredential: String, id: String,
    ): Result<Unit> = ioResult {
        val req = Request.Builder()
            .url(ApiEndpoints.session(normalisedBase(baseUrl), id))
            .deviceHeader(deviceCredential)
            .delete().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(resp.body?.string().orEmpty()))
        }
    }

    // 工具状态词映射与工具对象解析（流式与历史共用）：比较不用 lowercase()，
    // 土耳其语系下 "IN_PROGRESS"/"DENIED" 的 I 会转无点 ı、lowercase 后对不上；
    // equals 的 ignoreCase 按字符比对，不受语系影响。缺省值分两路：流式工具
    // 无状态即进行中（PENDING，流式以前不读 status、摘要永远待处理，现读）；
    // 历史工具无状态是未知（UNKNOWN）——历史已是过去，说“待处理”会把跑完
    // 的说成没跑，说“已完成”又可能是失败，UNKNOWN 最诚实。
    private fun parseToolStatus(raw: String, default: ToolCall.Status): ToolCall.Status {
        val st = raw.trim()
        return when {
            st.equals("done", true) || st.equals("completed", true) || st.equals("success", true) -> ToolCall.Status.DONE
            st.equals("running", true) || st.equals("in_progress", true) -> ToolCall.Status.RUNNING
            st.equals("failed", true) || st.equals("error", true) -> ToolCall.Status.FAILED
            st.equals("approved", true) -> ToolCall.Status.APPROVED
            st.equals("denied", true) || st.equals("rejected", true) -> ToolCall.Status.DENIED
            else -> default
        }
    }

    private fun parseToolCall(t: JSONObject, defaultStatus: ToolCall.Status): ToolCall = ToolCall(
        id = t.optString("id").trim(),
        name = t.optString("name").trim(),
        argsJson = t.optString("args").trim(),
        status = parseToolStatus(t.optString("status"), defaultStatus),
    )

    override fun sendMessage(
        baseUrl: String,
        deviceCredential: String,
        sessionId: String,
        text: String,
        model: String?,
    ): Flow<ChatEvent> = flow {
        // 流式连接两次 token 之间可能停很久（长任务思考），普通 client 的
        // 120s 读取超时会把它当成断线杀掉；这里单独给流式用不限读取超时。
        try {
            val payload = JSONObject().apply {
                put("message", text)
                if (!model.isNullOrBlank()) put("model", model.trim())
                put("stream", true)
            }
            val req = Request.Builder()
                .url(ApiEndpoints.sessionChat(normalisedBase(baseUrl), sessionId))
                .deviceHeader(deviceCredential)
                .post(jsonBody(payload))
                .build()
            // 取消要能打断阻塞读：流式读超时是 0（无限），以前取消只停收集端、
            // 读线程还在阻塞读里，job 完不成、streaming 卡住。把 call 挂到 job 的
            // 取消上，取消时 call.cancel() 让阻塞读抛 IOException（见下方 catch）。
            val call = streamHttp.newCall(req)
            currentCoroutineContext()[Job]?.invokeOnCancellation { call.cancel() }
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    // 非 2xx 时 body 里常有 {"error": "..."}：以前直接丢掉，
                    // 用户只看到 HTTP 码、不知道主机到底报了什么；能解析就带上。
                    val errDetail = try {
                        JSONObject(resp.body?.string().orEmpty()).optString("error").trim()
                    } catch (_: Exception) { "" }
                    val suffix = if (errDetail.isNotBlank()) "：$errDetail" else ""
                    emit(ChatEvent.Error("聊天失败：HTTP ${resp.code}$suffix"))
                    return@use
                }
                val source = resp.body?.source() ?: run {
                    emit(ChatEvent.Error("主机返回了空回复。"))
                    return@use
                }
                // Best-effort streaming parse: SSE `data: {...}` lines,
                // or plain JSON lines, or one final JSON object.
                // 多行 JSON 事件：主机把一个 JSON 对象拆成多行 data: 发时，
                // 逐行解析会每行都失败、当成原文进回复；以 { 或 [ 开头但
                // 解析失败的 data: 行先攒起来，后续 data: 行续上再试，空行/
                // 非 data: 行/流结束时仍未成形就按原文落袋（与旧行为一致，
                // 只是不再把 "data:" 前缀本身也写进回复）。
                var errorEmitted = false
                val full = StringBuilder()
                // 单个事件的解析：返回 false 表示终局错误、调用方应停读。
                suspend fun handleData(data: String): Boolean {
                    if (data.isEmpty() || data == "[DONE]") return true
                    try {
                        val json = JSONObject(data)
                        // 文本字段优先级是故意的：delta > text > content > message。
                        // 主机事件若同时带多个文本字段（如 delta 与 text 并存），
                        // 只取第一个命中的、其余丢弃；调换分支顺序会改变多字段事件
                        // 取哪个字段，不要无故重排。
                        when {
                            // 主机以 {"error": "..."} 发错时，以前没有任何分支接它，
                            // 整行会被静默丢掉、生成就这么没下文；先接 error 分支，
                            // 否则带 message 字段的错误会被当成正文增量发出。
                            // 错误是终局事件：发完就停，不再读后续行，也不再补 Done；
                            // 否则错误后的增量行会追加到已报错的回复里，Done 还会把状态再翻一次。
                            json.has("error") -> {
                                errorEmitted = true
                                emit(ChatEvent.Error(json.optString("error").trim().ifEmpty { "主机返回了错误，但没有说明原因。" }))
                                return false
                            }
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
                            json.has("content") -> {
                                val t = json.optString("content")
                                full.append(t)
                                emit(ChatEvent.TextDelta(t))
                            }
                            json.has("message") -> {
                                val t = json.optString("message")
                                full.append(t)
                                emit(ChatEvent.TextDelta(t))
                            }
                            json.has("toolCall") -> {
                                emit(ChatEvent.ToolCallEvent(parseToolCall(json.getJSONObject("toolCall"), ToolCall.Status.PENDING)))
                            }
                            json.has("approval") -> {
                                val a = json.getJSONObject("approval")
                                emit(
                                    ChatEvent.Approval(
                                        ApprovalRequest(
                                            id = a.optString("id").trim(),
                                            toolName = a.optString("tool").trim().ifEmpty { a.optString("toolName").trim() },
                                            description = a.optString("description").trim(),
                                            argsJson = a.optString("args").trim(),
                                        )
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Non-JSON line: treat as raw text (data: 前缀已去掉，
                        // 不像以前那样把 "data:" 本身也写进回复）。
                        full.append(data).append("\n")
                        emit(ChatEvent.TextDelta(data + "\n"))
                    }
                    return true
                }
                // 攒着的多行事件试解析：成形就处理并清空；不成形原样留着继续攒。
                var pending: StringBuilder? = null
                suspend fun tryPending(): Boolean {
                    val text = pending?.toString() ?: return true
                    return try {
                        JSONObject(text)
                        pending = null
                        handleData(text)
                    } catch (_: Exception) {
                        true
                    }
                }
                // 最终结账：不成形也按原文落袋（空行/非 data: 行/流结束时）。
                suspend fun flushPending(): Boolean {
                    val text = pending?.toString() ?: return true
                    pending = null
                    return try {
                        JSONObject(text)
                        handleData(text)
                    } catch (_: Exception) {
                        full.append(text).append("\n")
                        emit(ChatEvent.TextDelta(text + "\n"))
                        true
                    }
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    // SSE 注释行以冒号开头，不是内容；event/id/retry 同样是 SSE
                    // 字段名、不是内容；元数据行不切断攒着的多行事件。
                    if (line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:")) continue
                    if (line.isBlank()) {
                        // 空行是 SSE 事件边界：先把攒着的结掉。
                        if (!flushPending()) break
                        continue
                    }
                    if (line.startsWith("data:")) {
                        val content = line.removePrefix("data:").trim()
                        val p = pending
                        if (p != null) {
                            // 续行：多行事件的后续行。
                            if (p.isNotEmpty()) p.append("\n")
                            p.append(content)
                            if (!tryPending()) break
                            continue
                        }
                        if (content.isEmpty() || content == "[DONE]") continue
                        try {
                            JSONObject(content)
                            if (!handleData(content)) break
                        } catch (_: Exception) {
                            // 解析失败且像 JSON 开头：可能是多行事件的第一行，先攒着；
                            // 否则按原文（去掉 data: 前缀）落袋。
                            if (content.startsWith("{") || content.startsWith("[")) pending = StringBuilder(content)
                            else {
                                full.append(content).append("\n")
                                emit(ChatEvent.TextDelta(content + "\n"))
                            }
                        }
                        continue
                    }
                    // 非 SSE 行（裸 JSON 或纯文本）：先把攒着的结掉，再按原来处理。
                    if (!flushPending()) break
                    if (!handleData(line)) break
                }
                if (!flushPending()) {
                    // 流结束时攒着的已是终局错误：不再补 Done（与循环内一致）。
                    errorEmitted = true
                }
                if (!errorEmitted) emit(ChatEvent.Done(full.toString()))
            }
        } catch (e: CancellationException) {
            // 用户点停止属于主动取消，必须原样传播给 ViewModel 的取消分支
            // （保留已生成的部分回复、丢掉空占位），不能变成一条“聊天出错”。
            throw e
        } catch (e: IOException) {
            // call.cancel() 让阻塞读抛的 IOException：此时 job 已取消，应按取消
            // 处理（走 ViewModel 停止收尾），而不是报一条“发送失败”；真正的断网
            // 此时 job 未取消，仍走 Error。
            if (currentCoroutineContext()[Job]?.isCancelled == true) throw CancellationException()
            emit(ChatEvent.Error(e.message?.takeIf { it.isNotBlank() } ?: "聊天出错"))
        } catch (e: Exception) {
            emit(ChatEvent.Error(e.message?.takeIf { it.isNotBlank() } ?: "聊天出错"))
        }
    // flowOn IO：网络读取在 IO 线程，事件仍按收集方上下文投递（ViewModel 在 Main 收）。
    }.flowOn(Dispatchers.IO)

    override suspend fun respondToApproval(
        baseUrl: String, deviceCredential: String, sessionId: String,
        approvalId: String, approved: Boolean,
    ): Result<Unit> = ioResult {
        val payload = JSONObject().apply {
            put("approvalId", approvalId)
            put("approved", approved)
        }
        val req = Request.Builder()
            .url(ApiEndpoints.sessionApproval(normalisedBase(baseUrl), sessionId))
            .deviceHeader(deviceCredential)
            .post(jsonBody(payload))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}" + errorSuffix(resp.body?.string().orEmpty()))
        }
    }

    override suspend fun uploadAttachment(
        baseUrl: String, deviceCredential: String, sessionId: String,
        fileName: String, bytes: ByteArray,
    ): Result<String> = ioResult {
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
            if (!resp.isSuccessful) throw IllegalStateException("上传失败：HTTP ${resp.code}（适配器尚未验证）" + errorSuffix(body))
            // 成功但空 body 时返回空串；调用方须把空串当成“未知 id”，不是有效 id。
            body
        }
    }
}
