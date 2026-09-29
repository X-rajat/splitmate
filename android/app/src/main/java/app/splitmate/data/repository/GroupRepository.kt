package app.splitmate.data.repository

import app.splitmate.data.local.dao.GroupDao
import app.splitmate.data.local.entities.GroupEntity
import app.splitmate.data.remote.ApiService
import app.splitmate.data.remote.dto.GroupCreateRequest
import app.splitmate.data.remote.dto.GroupDto
import app.splitmate.data.remote.dto.GroupMemberDto
import app.splitmate.data.remote.dto.InvitationDto
import app.splitmate.data.remote.dto.JoinGroupResponse
import app.splitmate.data.remote.dto.PlaceholderMemberCreateRequest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val api: ApiService,
    private val groupDao: GroupDao,
) {
    /** Room is the single source of truth the UI observes; refresh() re-syncs it from the API. */
    fun observeCachedGroups(): Flow<List<GroupEntity>> = groupDao.observeGroups()

    suspend fun refresh(): List<GroupDto> {
        val groups = api.listGroups()
        groupDao.upsertAll(
            groups.map {
                GroupEntity(it.id, it.name, it.description, it.icon_url, it.currency, it.is_archived, it.member_count)
            }
        )
        return groups
    }

    suspend fun createGroup(
        name: String,
        description: String?,
        currency: String,
        memberIds: List<String>,
    ): GroupDto = api.createGroup(GroupCreateRequest(name, description, null, currency, memberIds))

    suspend fun createInvitation(groupId: String): InvitationDto = api.createInvitation(groupId)

    suspend fun joinGroup(token: String): JoinGroupResponse = api.joinGroup(token)

    /** Real member names for pickers (payer / split-between), including placeholder guests. */
    suspend fun listMembers(groupId: String): List<GroupMemberDto> = api.listMembers(groupId)

    /** Adds a guest with just a name - no account, no signup - so bills can be split with them. */
    suspend fun addPlaceholderMember(groupId: String, name: String): GroupMemberDto =
        api.addPlaceholderMember(groupId, PlaceholderMemberCreateRequest(name))
}
