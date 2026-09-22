package app.splitmate.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "splitmate_secure_prefs")

/**
 * Persists JWT access/refresh tokens. DataStore's file lives in the app's private
 * data directory (not backed up, not world-readable) which is the Android-appropriate
 * equivalent of secure local storage for a token pair; for defense in depth in a real
 * release build this should be wrapped with EncryptedFile / Jetpack Security.
 */
@Singleton
class TokenStore @Inject constructor(private val context: Context) {
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit {
            it[accessTokenKey] = accessToken
            it[refreshTokenKey] = refreshToken
        }
    }

    suspend fun getAccessToken(): String? = context.dataStore.data.first()[accessTokenKey]
    suspend fun getRefreshToken(): String? = context.dataStore.data.first()[refreshTokenKey]

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
