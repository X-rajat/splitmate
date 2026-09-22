package app.splitmate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.splitmate.data.local.dao.ExpenseDao
import app.splitmate.data.local.dao.GroupDao
import app.splitmate.data.local.dao.PendingExpenseDao
import app.splitmate.data.local.entities.ExpenseEntity
import app.splitmate.data.local.entities.GroupEntity
import app.splitmate.data.local.entities.PendingExpenseEntity

@Database(
    entities = [GroupEntity::class, ExpenseEntity::class, PendingExpenseEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun pendingExpenseDao(): PendingExpenseDao
}
