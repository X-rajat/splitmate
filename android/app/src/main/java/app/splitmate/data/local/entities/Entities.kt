package app.splitmate.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val currency: String,
    val isArchived: Boolean,
    val memberCount: Int,
    val netBalanceMinor: Long = 0,
)

@Entity(tableName = "cached_expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val splitType: String,
    val createdBy: String,
    val createdAt: String,
    val receiptUrl: String?,
)

/** Expenses created while offline, queued for sync once connectivity returns. */
@Entity(tableName = "pending_expenses")
data class PendingExpenseEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val groupId: String,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val syncFailed: Boolean = false,
)
