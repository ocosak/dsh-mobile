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
 * - clear() wipes both, and should be followed by a server-side revoke.
 */
class PairingStore(context: Context) {
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
        set(value) {
            if (value == null) prefs.edit().remove(KEY_URL).apply()
            else prefs.edit().putString(KEY_URL, value).apply()
        }

    var deviceCredential: String?
        get() = prefs.getString(KEY_CREDENTIAL, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_CREDENTIAL).apply()
            else prefs.edit().putString(KEY_CREDENTIAL, value).apply()
        }

    var deviceName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_NAME).apply()
            else prefs.edit().putString(KEY_NAME, value).apply()
        }

    var model: String?
        get() = prefs.getString(KEY_MODEL, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_MODEL).apply()
            else prefs.edit().putString(KEY_MODEL, value).apply()
        }

    val isPaired: Boolean
        get() = !serverUrl.isNullOrBlank() && !deviceCredential.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_CREDENTIAL = "device_credential"
        private const val KEY_NAME = "device_name"
        private const val KEY_MODEL = "model"
    }
}
