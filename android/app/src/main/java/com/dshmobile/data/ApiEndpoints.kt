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
 *   is NEVER logged and NEVER put in a URL by this app, except for
 *   the one-time pairing grant flow, which the reference design
 *   itself uses (`/pair-app?grant=<one-time>`).
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
    // Pairing (reference-design surface; plugin serves these).
    const val PAIR_STATUS = "/api/pair/status"
    const val PAIR_ACCEPT = "/api/pair/accept"
    const val PAIR_REVOKE = "/api/pair/revoke"

    // Gated channel. All session/chat traffic goes through here,
    // never through a bare /api path from a remote (non-loopback) host.
    const val REMOTE_PREFIX = "/remote"

    // TODO (adapter-needed): map these to the real host routes.
    fun sessions(base: String): String = base.trimEnd('/') + REMOTE_PREFIX + "/api/sessions"
    fun session(base: String, id: String): String = sessions(base) + "/" + id
    fun sessionMessages(base: String, id: String): String = session(base, id) + "/messages"
    fun sessionChat(base: String, id: String): String = session(base, id) + "/chat"

    // TODO (adapter-needed): upload endpoint is NOT confirmed.
    // The reference design notes official uploads use
    // /api/session/uploadFileBinary, rewritten through /remote.
    // This app posts to the gated equivalent below until verified.
    fun sessionUpload(base: String, id: String): String =
        base.trimEnd('/') + REMOTE_PREFIX + "/api/session/uploadFileBinary?session=" + id

    fun pairStatus(base: String): String = base.trimEnd('/') + PAIR_STATUS
    fun pairAccept(base: String): String = base.trimEnd('/') + PAIR_ACCEPT
    fun pairRevoke(base: String): String = base.trimEnd('/') + PAIR_REVOKE
}
