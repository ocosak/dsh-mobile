package com.dshmobile.data

/**
 * Single source of truth for every HTTP path this app talks to.
 *
 * SECURITY + HONESTY NOTE — read before wiring to a real host:
 * - The pairing surface below follows the design in
 *   https://github.com/zhu1090093659/dsh-web (dsh-remote-web-ui):
 *   a time-limited pairing token, per-device sessions, and a gated
 *   `/remote` channel. The device credential is sent as the
 *   `x-dsh-remote-device` header (the reference design's header) and
 *   is NEVER logged and NEVER put in a URL by this app. The reference
 *   design also has a one-time grant URL (`/pair-app?grant=<one-time>`),
 *   but this app never uses that flow: the pairing token goes in a POST body only.
 * - TODO (adapter-needed): the official dsh session/chat endpoints
 *   were NOT confirmed from the reference docs at v1 time. The
 *   `/remote/api/...` paths below are therefore ADAPTER PLACEHOLDERS
 *   for this app's own `dsh-mobile-remote` plugin (see plugin/).
 *   Do NOT present them as verified official dsh endpoints. When
 *   pointing at a real dsh instance, map each function in
 *   DshRemoteApi to the host's actual route here, in this file only,
 *   without touching any UI code. The plugin in this repo documents
 *   the same contract it expects the host to serve.
 */
object ApiEndpoints {
    // 再去掉用户手写进来的 /remote 前缀：前缀由本应用统一加，用户地址里带了它
    // 不处理会拼成 /remote/remote；只去精确后缀，不动其它路径（如 /myremote）。
    // 前后空白一并去掉（调用方一般已 trim，但 choke point 自己兜底更稳）；
    // 后缀比较不区分大小写（"…/REMOTE" 同样要去，否则拼成 /remote/REMOTE）。
    private fun cleanBase(base: String): String {
        val trimmed = base.trim().trimEnd('/')
        return if (trimmed.endsWith(REMOTE_PREFIX, ignoreCase = true)) trimmed.dropLast(REMOTE_PREFIX.length) else trimmed
    }
    // Pairing (reference-design surface; plugin serves these).
    const val PAIR_STATUS = "/api/pair/status"
    const val PAIR_ACCEPT = "/api/pair/accept"
    const val PAIR_REVOKE = "/api/pair/revoke"

    // Gated channel. All session/chat traffic goes through here,
    // never through a bare /api path from a remote (non-loopback) host.
    const val REMOTE_PREFIX = "/remote"

    // TODO (adapter-needed): map these to the real host routes.
    fun sessions(base: String): String = cleanBase(base) + REMOTE_PREFIX + "/api/sessions"
    fun session(base: String, id: String): String = "${sessions(base)}/${android.net.Uri.encode(id)}"
    fun sessionMessages(base: String, id: String): String = "${session(base, id)}/messages"
    fun sessionChat(base: String, id: String): String = "${session(base, id)}/chat"

    // TODO (adapter-needed): approval endpoint shape not confirmed, same
    // standing as sessionUpload; kept here (not built inline in the client)
    // so every path lives in this file, per this file's own rule.
    fun sessionApproval(base: String, id: String): String = "${sessionChat(base, id)}/approval"

    // TODO (adapter-needed): upload endpoint is NOT confirmed.
    // The reference design notes official uploads use
    // /api/session/uploadFileBinary, rewritten through /remote.
    // This app posts to the gated equivalent below until verified.
    fun sessionUpload(base: String, id: String): String =
        "${cleanBase(base)}${REMOTE_PREFIX}/api/session/uploadFileBinary?session=${android.net.Uri.encode(id)}"

    fun pairStatus(base: String): String = cleanBase(base) + PAIR_STATUS
    fun pairAccept(base: String): String = cleanBase(base) + PAIR_ACCEPT
    fun pairRevoke(base: String): String = cleanBase(base) + PAIR_REVOKE
}
