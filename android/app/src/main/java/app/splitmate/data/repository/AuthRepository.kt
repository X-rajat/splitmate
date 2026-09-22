package app.splitmate.data.repository

import app.splitmate.data.local.TokenStore
import app.splitmate.data.remote.ApiService
import app.splitmate.data.remote.dto.LoginRequest
import app.splitmate.data.remote.dto.RegisterRequest
import app.splitmate.data.remote.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) {
    suspend fun register(name: String, email: String, mobile: String?, password: String) {
        val tokens = api.register(RegisterRequest(name, email, mobile, password))
        tokenStore.saveTokens(tokens.access_token, tokens.refresh_token)
    }

    suspend fun login(identifier: String, password: String) {
        val tokens = api.login(LoginRequest(identifier, password))
        tokenStore.saveTokens(tokens.access_token, tokens.refresh_token)
    }

    suspend fun isLoggedIn(): Boolean = tokenStore.getAccessToken() != null

    suspend fun me(): UserDto = api.getMe()

    suspend fun logout() = tokenStore.clear()
}
