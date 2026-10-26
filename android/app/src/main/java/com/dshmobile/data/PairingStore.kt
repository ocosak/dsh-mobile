package com.dshmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for pairing state.
 *
 * - Server URL: not a secret by itself, but stored alongside the credential.
 * - Device credential: full-control credential (a paired device can
 *   drive the host's agent). It is stored ONLY in EncryptedSharedPreferences,
 *   never in plain files, logs, or URLs.
 * - clear() wipes all stored state (URL, credential, name, model),
 *   and should be followed by a server-side revoke.
 * - All setters trim leading/trailing whitespace on write (putOrRemove);
 *   callers still validate the trimmed value before writing.
 */
class PairingStore(context: Context) {
    // 写入前统一去首尾空格：地址、凭据、设备名、模型名都不应带首尾空格；
    // 集中在这里，调用方不用各写一遍 trim，新 setter 也自动兜底。
    // 注意：校验仍要在调用方对 trim 后的值做（store 的 trim 不能代替校验）。
    private fun putOrRemove(key: String, value: String?, sync: Boolean = false) {
        val clean = value?.trim()
        // 空串与 null 同按清除处理：以前空串会存成 "" 键，读方全靠
        // orEmpty/isNullOrBlank 兜底；清掉后 store 里不留空键，读方语义
        // 不变（各处对 null 与 "" 等价：isNullOrBlank、orEmpty、takeIf）。
        val editor = if (clean.isNullOrEmpty()) prefs.edit().remove(key) else prefs.edit().putString(key, clean)
        // 凭据丢了就得整套重配（不像地址/模型名可随手重填），写盘用 commit；
        // 其余字段仍走 apply，免得每次打字都同步写盘卡主线程。
        if (sync) editor.commit() else editor.apply()
    }
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "dsh_pairing",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var serverUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) { putOrRemove(KEY_URL, value) }

    var deviceCredential: String?
        get() = prefs.getString(KEY_CREDENTIAL, null)
        set(value) { putOrRemove(KEY_CREDENTIAL, value, sync = true) }

    var deviceName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) { putOrRemove(KEY_NAME, value) }

    var model: String?
        get() = prefs.getString(KEY_MODEL, null)
        set(value) { putOrRemove(KEY_MODEL, value) }

    // isPaired only means credentials are stored locally, not that the host
    // is reachable or the URL is valid; checkStatus verifies reachability.
    val isPaired: Boolean
        get() = !serverUrl.isNullOrBlank() && !deviceCredential.isNullOrBlank()

    fun clear() {
        // 凭据清除必须同步落盘：apply() 是异步写盘，应用紧接着被杀掉
        // 就可能没清干净；commit() 同步写完才返回，断开时用它。
        prefs.edit().clear().commit()
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_CREDENTIAL = "device_credential"
        private const val KEY_NAME = "device_name"
        private const val KEY_MODEL = "model"
    }
}
