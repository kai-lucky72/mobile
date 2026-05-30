package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.ServiceLocator
import com.example.ui.screens.AddEditExpenseScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpenseDetailScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize local SQLite Room persistence context
        ServiceLocator.init(this)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val repository = ServiceLocator.repository!!
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return FinanceViewModel(repository) as T
                    }
                }
                val viewModel: FinanceViewModel = viewModel(factory = factory)
                FinanceAppNavHost(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FinanceAppNavHost(viewModel: FinanceViewModel) {
    val navController = rememberNavController()
    val currentUserState = viewModel.currentUser.collectAsState()
    val startDestination = if (currentUserState.value != null) "dashboard" else "login"

    LaunchedEffect(currentUserState.value) {
        if (currentUserState.value == null) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToAddExpense = {
                    navController.navigate("add_expense")
                },
                onNavigateToExpenseDetails = { id ->
                    navController.navigate("detail/$id")
                }
            )
        }
        composable("add_expense") {
            AddEditExpenseScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            ExpenseDetailScreen(
                expenseId = id,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

