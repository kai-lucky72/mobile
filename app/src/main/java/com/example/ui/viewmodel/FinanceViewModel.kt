package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Budget
import com.example.data.model.Expense
import com.example.data.model.User
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface LoginState {
    object Idle : LoginState
    object Loading : LoginState
    data class Success(val user: User) : LoginState
    data class Error(val message: String) : LoginState
}

sealed interface ExpenseDetailState {
    object Idle : ExpenseDetailState
    object Loading : ExpenseDetailState
    data class Success(val expense: Expense) : ExpenseDetailState
    data class Error(val message: String) : ExpenseDetailState
}

sealed interface CreateExpenseState {
    object Idle : CreateExpenseState
    object Loading : CreateExpenseState
    object Success : CreateExpenseState
    data class Error(val message: String) : CreateExpenseState
}

data class CategoryLimit(
    val category: String,
    val limit: Double,
    val spent: Double,
    val exceeded: Boolean,
    val remaining: Double
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _createExpenseState = MutableStateFlow<CreateExpenseState>(CreateExpenseState.Idle)
    val createExpenseState: StateFlow<CreateExpenseState> = _createExpenseState.asStateFlow()

    private val _selectedExpenseState = MutableStateFlow<ExpenseDetailState>(ExpenseDetailState.Idle)
    val selectedExpenseState: StateFlow<ExpenseDetailState> = _selectedExpenseState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>("All")
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    val expenses: StateFlow<List<Expense>> = _currentUser
        .filterNotNull()
        .flatMapLatest { user ->
            repository.observeExpenses(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredExpenses: StateFlow<List<Expense>> = combine(
        expenses,
        searchQuery,
        selectedCategoryFilter
    ) { list, search, categoryFilter ->
        list.filter { expense ->
            val matchesSearch = search.isBlank() || 
                expense.displayTitle.contains(search, ignoreCase = true) ||
                (expense.description ?: "").contains(search, ignoreCase = true)
            val matchesCategory = categoryFilter == "All" || expense.category.equals(categoryFilter, ignoreCase = true)
            matchesSearch && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryLimits: StateFlow<List<CategoryLimit>> = combine(
        currentUser,
        expenses
    ) { user, list ->
        if (user == null) return@combine emptyList<CategoryLimit>()
        
        val spentByCategory = list.groupBy { (it.category ?: "Other").trim() }
            .mapValues { entry -> entry.value.sumOf { it.amountDouble } }

        val budgets = user.budgets ?: emptyList()
        val defaultBudgetedCategories = budgets.map { it.category.trim() }.toSet()

        val listLimits = budgets.map { budget ->
            val spent = spentByCategory[budget.category.trim()] ?: 0.0
            CategoryLimit(
                category = budget.category,
                limit = budget.amount,
                spent = spent,
                exceeded = spent > budget.amount,
                remaining = budget.amount - spent
            )
        }.toMutableList()

        spentByCategory.forEach { (cat, spent) ->
            if (!defaultBudgetedCategories.contains(cat) && spent > 0.0) {
                listLimits.add(
                    CategoryLimit(
                        category = cat,
                        limit = 0.0,
                        spent = spent,
                        exceeded = false,
                        remaining = 0.0
                    )
                )
            }
        }
        listLimits
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetAlerts: StateFlow<List<String>> = categoryLimits.map { limits ->
        limits.filter { it.exceeded && it.limit > 0 }.map { 
            "Budget Alert: Category '${it.category}' exceeded limit by RWF ${String.format("%,.2f", it.spent - it.limit)}!" 
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpent: StateFlow<Double> = expenses.map { list ->
        list.sumOf { it.amountDouble }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalBudget: StateFlow<Double> = currentUser.map { user ->
        user?.budgets?.sumOf { it.amount } ?: 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        checkSavedUser()
    }

    private fun checkSavedUser() {
        viewModelScope.launch {
            val user = repository.getCurrentUser()
            if (user != null) {
                _currentUser.value = user
                syncExpenses(user.id)
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Please fill out all fields.")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val users = repository.authenticate(email.trim())
                val matchedUser = users.firstOrNull { it.username.equals(email.trim(), ignoreCase = true) }
                
                if (matchedUser == null) {
                    _loginState.value = LoginState.Error("User with email $email not found.")
                } else {
                    val inputPassword = password.trim()
                    val dbPassword = matchedUser.password.trim()
                    val isPasswordCorrect = dbPassword == inputPassword ||
                        dbPassword.replace('l', 'I') == inputPassword.replace('l', 'I') ||
                        dbPassword.replace('I', 'l') == inputPassword.replace('I', 'l')

                    if (!isPasswordCorrect) {
                        _loginState.value = LoginState.Error("Incorrect password. Please try again.")
                    } else {
                        repository.saveCurrentUser(matchedUser)
                        _currentUser.value = matchedUser
                        _loginState.value = LoginState.Success(matchedUser)
                        syncExpenses(matchedUser.id)
                    }
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Login failed: ${e.localizedMessage ?: "Unknown network error."}")
            }
        }
    }

    fun syncExpenses(userId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshExpenses(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addExpense(title: String, category: String, amount: String, description: String, date: String) {
        val userId = _currentUser.value?.id ?: return
        val parsedAmt = amount.toDoubleOrNull()
        if (title.isBlank() || parsedAmt == null || parsedAmt <= 0) {
            _createExpenseState.value = CreateExpenseState.Error("Invalid details: Amount must be greater than zero.")
            return
        }

        viewModelScope.launch {
            _createExpenseState.value = CreateExpenseState.Loading
            try {
                val expenseDraft = Expense(
                    title = title,
                    name = title,
                    category = category,
                    amount = parsedAmt,
                    description = description,
                    date = date,
                    userId = userId
                )
                repository.createExpense(expenseDraft)
                _createExpenseState.value = CreateExpenseState.Success
                syncExpenses(userId)
            } catch (e: Exception) {
                _createExpenseState.value = CreateExpenseState.Error(e.localizedMessage ?: "Failed to save expense.")
            }
        }
    }

    fun loadExpenseDetails(id: String) {
        viewModelScope.launch {
            _selectedExpenseState.value = ExpenseDetailState.Loading
            try {
                val detail = repository.getExpenseById(id)
                if (detail != null) {
                    _selectedExpenseState.value = ExpenseDetailState.Success(detail)
                } else {
                    _selectedExpenseState.value = ExpenseDetailState.Error("Expense not found locally or on server.")
                }
            } catch (e: Exception) {
                _selectedExpenseState.value = ExpenseDetailState.Error(e.localizedMessage ?: "Failed to load details.")
            }
        }
    }

    fun deleteExpense(id: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteExpense(id)
                syncExpenses(user.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logoutUser()
            _currentUser.value = null
            _loginState.value = LoginState.Idle
            _createExpenseState.value = CreateExpenseState.Idle
            _selectedExpenseState.value = ExpenseDetailState.Idle
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun resetCreateState() {
        _createExpenseState.value = CreateExpenseState.Idle
    }
}
