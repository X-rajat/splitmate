package app.splitmate.data.local.dao

import androidx.room.*
import app.splitmate.data.local.entities.ExpenseEntity
import app.splitmate.data.local.entities.GroupEntity
import app.splitmate.data.local.entities.PendingExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM cached_groups WHERE isArchived = 0")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<GroupEntity>)

    @Query("DELETE FROM cached_groups")
    suspend fun clear()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM cached_expenses WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun observeExpenses(groupId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM cached_expenses WHERE groupId = :groupId")
    suspend fun clearForGroup(groupId: String)
}

@Dao
interface PendingExpenseDao {
    @Insert
    suspend fun enqueue(expense: PendingExpenseEntity): Long

    @Query("SELECT * FROM pending_expenses ORDER BY createdAtEpochMs ASC")
    suspend fun getAllPending(): List<PendingExpenseEntity>

    @Query("SELECT COUNT(*) FROM pending_expenses")
    fun observePendingCount(): Flow<Int>

    @Delete
    suspend fun delete(expense: PendingExpenseEntity)

    @Update
    suspend fun update(expense: PendingExpenseEntity)
}
