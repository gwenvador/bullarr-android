package io.github.gwenvador.bullarr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** URL du serveur et clé API, chiffrées (clé maître dans le Keystore Android). */
class Settings(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bullarr_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value).apply()

    /** "key" (clé API), "password" (identifiant) ou "oidc" (SSO). */
    var authMode: String
        get() = prefs.getString(KEY_MODE, "key") ?: "key"
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASS, value).apply()

    var sessionCookie: String
        get() = prefs.getString(KEY_COOKIE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    /** Dernière URL utilisée, gardée après une déconnexion pour préremplir l'écran de connexion. */
    var lastUrl: String
        get() = prefs.getString(KEY_LAST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST, value).apply()

    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_API = "api_key"
        const val KEY_LAST = "last_url"
        const val KEY_MODE = "auth_mode"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        const val KEY_COOKIE = "session_cookie"
    }
}
