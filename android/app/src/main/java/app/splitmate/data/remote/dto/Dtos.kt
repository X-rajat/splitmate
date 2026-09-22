package app.splitmate.data.remote.dto

data class RegisterRequest(val name: String, val email: String, val mobile: String?, val password: String)
data class LoginRequest(val identifier: String, val password: String)
data class RefreshRequest(val refresh_token: String)
data class TokenPairDto(val access_token: String, val refresh_token: String, val token_type: String)

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val mobile: String?,
    val profile_photo_url: String?,
    val default_currency: String,
)

data class GroupCreateRequest(
    val name: String,
    val description: String?,
    val icon_url: String?,
    val currency: String,
    val member_ids: List<String> = emptyList(),
)

data class GroupDto(
    val id: String,
    val name: String,
    val description: String?,
    val icon_url: String?,
    val currency: String,
    val is_archived: Boolean,
    val member_count: Int,
)

data class InvitationDto(
    val token: String,
    val invite_url: String,
    val share_message: String,
    val expires_at: String,
)

data class JoinGroupResponse(val group: GroupDto, val already_member: Boolean)

data class ExpenseParticipantIn(
    val user_id: String,
    val amount_minor: Long? = null,
    val percentage_bp: Int? = null,
    val shares: Int? = null,
)

data class ExpensePaymentIn(val user_id: String, val amount_minor: Long)

data class ExpenseCreateRequest(
    val description: String,
    val amount_minor: Long,
    val currency: String,
    val category: String,
    val split_type: String,
    val participant_ids: List<String> = emptyList(),
    val participants: List<ExpenseParticipantIn> = emptyList(),
    val payments: List<ExpensePaymentIn>,
    val receipt_url: String? = null,
)

data class ExpenseDto(
    val id: String,
    val group_id: String,
    val description: String,
    val amount_minor: Long,
    val currency: String,
    val category: String,
    val split_type: String,
    val created_by: String,
    val created_at: String,
    val receipt_url: String?,
)

data class BalanceEntryDto(val user_id: String, val user_name: String, val net_minor: Long)

data class SettlementSuggestionDto(
    val from_user_id: String,
    val from_user_name: String,
    val to_user_id: String,
    val to_user_name: String,
    val amount_minor: Long,
)

data class SettlementCreateRequest(
    val group_id: String,
    val to_user_id: String,
    val amount_minor: Long,
    val currency: String,
    val method: String,
    val upi_id: String? = null,
    val note: String? = null,
)

data class SettlementDto(
    val id: String,
    val group_id: String,
    val from_user_id: String,
    val to_user_id: String,
    val amount_minor: Long,
    val currency: String,
    val method: String,
    val created_at: String,
)

data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val is_read: Boolean,
    val created_at: String,
)

data class FriendRequestCreateBody(val to_user_id: String)
data class FriendRequestDto(val id: String, val from_user_id: String, val to_user_id: String, val status: String)
