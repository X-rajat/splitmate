package app.splitmate.data.repository

import app.splitmate.data.remote.ApiService
import app.splitmate.data.remote.dto.FriendRequestCreateBody
import app.splitmate.data.remote.dto.FriendRequestDto
import app.splitmate.data.remote.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(private val api: ApiService) {
    suspend fun listFriends(): List<UserDto> = api.listFriends()
    suspend fun search(query: String): List<UserDto> = api.searchUsers(query)
    suspend fun sendRequest(toUserId: String): FriendRequestDto = api.sendFriendRequest(FriendRequestCreateBody(toUserId))
}
