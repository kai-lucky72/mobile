package com.example.data.repository

import com.example.data.api.FinanceApiService
import com.example.data.local.CachedUser
import com.example.data.local.FinanceDao
import com.example.data.local.LocalExpense
import com.example.data.model.Budget
import com.example.data.model.Expense
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FinanceRepository(
    private val apiService: FinanceApiService,
    private val financeDao: FinanceDao
) {
    // Current user state
    suspend fun getCurrentUser(): User? {
        val cached = financeDao.getCurrentUser()
        return cached?.toUser()
    }

    suspend fun saveCurrentUser(user: User) {
        financeDao.clearCurrentUserFlag()
        val cached = CachedUser.fromUser(user, isCurrent = true)
        financeDao.insertUser(cached)
    }

    suspend fun logoutUser() {
        financeDao.deleteCurrentUser()
    }

    // Server-side login lookup with fallback to mock data
    suspend fun authenticate(username: String): List<User> {
        return try {
            apiService.getUsersByUsername(username)
        } catch (e: Exception) {
            // Fallback: If network/api is offline or restricted (e.g., HTTP 402), return local mock users
            getMockUsers().filter { it.username.equals(username.trim(), ignoreCase = true) }
        }
    }

    // Expense replication flow
    fun observeExpenses(userId: String): Flow<List<Expense>> {
        return financeDao.getExpensesForUser(userId).map { list ->
            list.map { it.toExpense() }
        }
    }

    suspend fun refreshExpenses(userId: String) {
        try {
            val remoteExpenses = apiService.getAllExpenses()
            val userExpenses = remoteExpenses.filter { it.userId == userId }
            val localList = userExpenses.map { LocalExpense.fromExpense(it) }
            financeDao.clearExpensesForUser(userId)
            financeDao.insertExpenses(localList)
        } catch (e: Exception) {
            // Fallback: API call failed (HTTP 402 / offline)
            // If the local database is empty for this user, seed it with interactive bento mock data
            val currentLocalList = financeDao.getExpensesForUser(userId).first()
            if (currentLocalList.isEmpty()) {
                val mockExpenses = getMockExpenses(userId)
                val localMockList = mockExpenses.map { LocalExpense.fromExpense(it) }
                financeDao.insertExpenses(localMockList)
            }
        }
    }

    suspend fun getExpenseById(id: String): Expense? {
        // Try local database first
        val local = financeDao.getExpenseById(id)
        if (local != null) {
            return local.toExpense()
        }
        // Fallback to API direct
        return try {
            val remote = apiService.getExpenseDetails(id)
            financeDao.insertExpense(LocalExpense.fromExpense(remote))
            remote
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createExpense(expense: Expense): Expense {
        return try {
            val remote = apiService.createExpense(expense)
            financeDao.insertExpense(LocalExpense.fromExpense(remote))
            remote
        } catch (e: Exception) {
            // Fallback: Create locally if offline or payment limit reached
            val localId = expense.id ?: java.util.UUID.randomUUID().toString()
            val localExpense = expense.copy(
                id = localId,
                createdAt = System.currentTimeMillis().toString()
            )
            financeDao.insertExpense(LocalExpense.fromExpense(localExpense))
            localExpense
        }
    }

    suspend fun deleteExpense(id: String) {
        try {
            apiService.deleteExpense(id)
        } catch (e: Exception) {
            // Ignore server-side deletion error if offline/402
        }
        financeDao.deleteExpenseById(id)
    }

    // Predefined beautiful mock users with structured M3 bento-grid budgets
    private fun getMockUsers(): List<User> {
        return listOf(
            User(
                id = "1",
                username = "Rosalyn59@hotmail.com",
                password = "GjFlrSgyliG8tW5",
                budgets = listOf(
                    Budget(category = "Food", amount = 60000.0),
                    Budget(category = "Transport", amount = 100000.0),
                    Budget(category = "Utilities", amount = 150000.0),
                    Budget(category = "Clothing", amount = 190000.0)
                )
            ),
            User(
                id = "2",
                username = "Winona88@gmail.com",
                password = "K1WhMnOMjFPMk7J",
                budgets = listOf(
                    Budget(category = "Food", amount = 80000.0),
                    Budget(category = "Transport", amount = 120000.0),
                    Budget(category = "Utilities", amount = 200000.0),
                    Budget(category = "Clothing", amount = 100000.0)
                )
            )
        )
    }

    // Interactive bento starter values
    private fun getMockExpenses(userId: String): List<Expense> {
        return listOf(
            Expense(
                id = "${userId}_exp1",
                createdAt = "2026-05-27T08:00:00Z",
                name = "New Outfit",
                title = "New Outfit",
                amount = 400.0,
                description = "Bento-inspired stylish new outfit",
                userId = userId,
                category = "Clothing",
                date = "May 27"
            ),
            Expense(
                id = "${userId}_exp2",
                createdAt = "2026-05-26T12:00:00Z",
                name = "Bus Fare",
                title = "Bus Fare",
                amount = 1234.0,
                description = "Bus ride across Kigali",
                userId = userId,
                category = "Transport",
                date = "May 26"
            ),
            Expense(
                id = "${userId}_exp3",
                createdAt = "2026-05-25T19:30:00Z",
                name = "Dinner",
                title = "Dinner",
                amount = 500.0,
                description = "Shared dinner with friends",
                userId = userId,
                category = "Food",
                date = "May 25"
            )
        )
    }
}
