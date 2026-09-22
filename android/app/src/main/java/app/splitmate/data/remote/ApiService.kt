package app.splitmate.data.remote

import app.splitmate.data.remote.dto.*
import retrofit2.http.*

interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenPairDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenPairDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPairDto

    @GET("users/me")
    suspend fun getMe(): UserDto

    @GET("groups")
    suspend fun listGroups(): List<GroupDto>

    @POST("groups")
    suspend fun createGroup(@Body body: GroupCreateRequest): GroupDto

    @GET("groups/{id}")
    suspend fun getGroup(@Path("id") id: String): GroupDto

    @POST("groups/{id}/invite")
    suspend fun createInvitation(@Path("id") id: String): InvitationDto

    @POST("groups/join/{token}")
    suspend fun joinGroup(@Path("token") token: String): JoinGroupResponse

    @GET("groups/{id}/expenses")
    suspend fun listExpenses(@Path("id") id: String): List<ExpenseDto>

    @POST("groups/{id}/expenses")
    suspend fun createExpense(@Path("id") id: String, @Body body: ExpenseCreateRequest): ExpenseDto

    @GET("groups/{id}/balances")
    suspend fun getBalances(@Path("id") id: String): List<BalanceEntryDto>

    @GET("groups/{id}/settlements/suggested")
    suspend fun getSuggestedSettlements(@Path("id") id: String): List<SettlementSuggestionDto>

    @POST("settlements")
    suspend fun createSettlement(@Body body: SettlementCreateRequest): SettlementDto

    @GET("friends")
    suspend fun listFriends(): List<UserDto>

    @GET("friends/search")
    suspend fun searchUsers(@Query("q") query: String): List<UserDto>

    @POST("friends/requests")
    suspend fun sendFriendRequest(@Body body: FriendRequestCreateBody): FriendRequestDto

    @GET("notifications")
    suspend fun listNotifications(): List<NotificationDto>
}
