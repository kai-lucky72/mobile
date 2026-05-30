package com.example.data.local

import androidx.room.*
import com.example.data.model.Budget
import com.example.data.model.Expense
import com.example.data.model.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_expenses")
data class LocalExpense(
    @PrimaryKey val id: String,
    val createdAt: String?,
    val name: String?,
    val title: String?,
    val amount: Double,
    val description: String?,
    val userId: String?,
    val category: String?,
    val date: String?
) {
    fun toExpense(): Expense {
        return Expense(
            id = id,
            createdAt = createdAt,
            name = name,
            title = title,
            amount = amount,
            description = description,
            userId = userId,
            category = category,
            date = date
        )
    }

    companion object {
        fun fromExpense(expense: Expense): LocalExpense {
            return LocalExpense(
                id = expense.id ?: "",
                createdAt = expense.createdAt,
                name = expense.name,
                title = expense.title,
                amount = expense.amountDouble,
                description = expense.description,
                userId = expense.userId,
                category = expense.category ?: "Other",
                date = expense.date
            )
        }
    }
}

@Entity(tableName = "cached_users")
data class CachedUser(
    @PrimaryKey val id: String,
    val username: String,
    val isCurrent: Boolean = false,
    val budgetsJson: String? = null
) {
    fun getBudgets(): List<Budget> {
        if (budgetsJson.isNullOrBlank()) return emptyList()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, Budget::class.java)
        val adapter = moshi.adapter<List<Budget>>(type)
        return try {
            adapter.fromJson(budgetsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toUser(): User {
        return User(
            id = id,
            username = username,
            password = "", // Not storing the password in cleartext locally
            budgets = getBudgets()
        )
    }

    companion object {
        fun fromUser(user: User, isCurrent: Boolean = false): CachedUser {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, Budget::class.java)
            val adapter = moshi.adapter<List<Budget>>(type)
            val budgetsJson = user.budgets?.let { adapter.toJson(it) }
            return CachedUser(
                id = user.id,
                username = user.username,
                isCurrent = isCurrent,
                budgetsJson = budgetsJson
            )
        }
    }
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM local_expenses WHERE userId = :userId ORDER BY date DESC, createdAt DESC")
    fun getExpensesForUser(userId: String): Flow<List<LocalExpense>>

    @Query("SELECT * FROM local_expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): LocalExpense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: LocalExpense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<LocalExpense>)

    @Query("DELETE FROM local_expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)

    @Query("DELETE FROM local_expenses WHERE userId = :userId")
    suspend fun clearExpensesForUser(userId: String)

    @Query("SELECT * FROM cached_users WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentUser(): CachedUser?

    @Query("SELECT * FROM cached_users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): CachedUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: CachedUser)

    @Query("UPDATE cached_users SET isCurrent = 0")
    suspend fun clearCurrentUserFlag()

    @Query("DELETE FROM cached_users WHERE isCurrent = 1")
    suspend fun deleteCurrentUser()
}

@Database(entities = [LocalExpense::class, CachedUser::class], version = 1, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
}
