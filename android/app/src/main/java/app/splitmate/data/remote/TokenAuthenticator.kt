package app.splitmate.data.remote

import app.splitmate.BuildConfig
import app.splitmate.data.local.TokenStore
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * Access tokens expire after 30 minutes (see backend ACCESS_TOKEN_EXPIRE_MINUTES) and
 * every screen in this app makes authenticated calls, so without this the app would
 * crash or silently fail on a 401 the instant a session got old - which is exactly
 * what made Add Expense / balances crash after sitting on a screen for a while.
 *
 * On a 401, this exchanges the stored refresh token for a new pair and retries the
 * original request once. If the refresh token itself is also invalid/expired, it
 * clears the stored tokens so the app falls back to showing the login screen next
 * time it checks auth state, rather than looping on an unrecoverable 401.
 */
/** Constructed explicitly by [app.splitmate.di.NetworkModule] (not @Inject) so the
 * plain, un-authenticated OkHttpClient it needs for the refresh call itself can be
 * passed in by qualifier without creating a circular Hilt binding. */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val gson: Gson,
    private val plainOkHttpClient: OkHttpClient,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never loop forever: if we already retried once for this request chain, give up.
        if (responseCount(response) >= 2) return null

        return runBlocking {
            mutex.withLock {
                val refreshToken = tokenStore.getRefreshToken() ?: return@withLock null

                // Another request may have already refreshed while we waited for the lock;
                // if the failing request's token differs from what's stored now, just retry with it.
                val currentAccessToken = tokenStore.getAccessToken()
                val failedAuthHeader = response.request.header("Authorization")
                if (currentAccessToken != null && failedAuthHeader != "Bearer $currentAccessToken") {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                val newTokens = refreshTokens(refreshToken)
                if (newTokens == null) {
                    tokenStore.clear()
                    return@withLock null
                }
                tokenStore.saveTokens(newTokens.first, newTokens.second)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.first}")
                    .build()
            }
        }
    }

    /** Returns (accessToken, refreshToken) on success, null on any failure. */
    private fun refreshTokens(refreshToken: String): Pair<String, String>? {
        val body = gson.toJson(mapOf("refresh_token" to refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL + "auth/refresh")
            .post(body)
            .build()
        return try {
            plainOkHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = resp.body?.string() ?: return null
                val parsed = gson.fromJson(json, Map::class.java)
                val access = parsed["access_token"] as? String ?: return null
                val refresh = parsed["refresh_token"] as? String ?: return null
                access to refresh
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
