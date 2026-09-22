package app.splitmate.data.repository

import app.splitmate.data.local.dao.ExpenseDao
import app.splitmate.data.local.dao.PendingExpenseDao
import app.splitmate.data.local.entities.ExpenseEntity
import app.splitmate.data.local.entities.PendingExpenseEntity
import app.splitmate.data.remote.ApiService
import app.splitmate.data.remote.dto.BalanceEntryDto
import app.splitmate.data.remote.dto.ExpenseCreateRequest
import app.splitmate.data.remote.dto.SettlementSuggestionDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val api: ApiService,
    private val expenseDao: ExpenseDao,
    private val pendingExpenseDao: PendingExpenseDao,
    private val gson: Gson,
) {
    fun observeCachedExpenses(groupId: String): Flow<List<ExpenseEntity>> = expenseDao.observeExpenses(groupId)

    fun observePendingCount(): Flow<Int> = pendingExpenseDao.observePendingCount()

    suspend fun refresh(groupId: String) {
        val expenses = api.listExpenses(groupId)
        expenseDao.upsertAll(
            expenses.map {
                ExpenseEntity(
                    it.id, it.group_id, it.description, it.amount_minor, it.currency,
                    it.category, it.split_type, it.created_by, it.created_at, it.receipt_url,
                )
            }
        )
    }

    /**
     * Creates an expense. If the network call fails (offline), the request is queued
     * locally in Room and retried by [syncPendingExpenses] the next time connectivity
     * is confirmed - satisfying the "create pending expenses while offline" requirement.
     */
    suspend fun createExpense(groupId: String, request: ExpenseCreateRequest): Boolean {
        return try {
            api.createExpense(groupId, request)
            refresh(groupId)
            true
        } catch (e: IOException) {
            pendingExpenseDao.enqueue(
                PendingExpenseEntity(
                    groupId = groupId,
                    payloadJson = gson.toJson(request),
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            )
            false
        }
    }

    suspend fun syncPendingExpenses() {
        for (pending in pendingExpenseDao.getAllPending()) {
            val request = gson.fromJson(pending.payloadJson, ExpenseCreateRequest::class.java)
            try {
                api.createExpense(pending.groupId, request)
                pendingExpenseDao.delete(pending)
            } catch (e: IOException) {
                pendingExpenseDao.update(pending.copy(syncFailed = true))
            }
        }
    }

    suspend fun getBalances(groupId: String): List<BalanceEntryDto> = api.getBalances(groupId)

    suspend fun getSuggestedSettlements(groupId: String): List<SettlementSuggestionDto> =
        api.getSuggestedSettlements(groupId)
}
