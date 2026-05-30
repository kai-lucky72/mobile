package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.CreateExpenseState
import com.example.ui.viewmodel.FinanceViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExpenseScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val createState by viewModel.createExpenseState.collectAsState()

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var date by remember { mutableStateOf("2026-05-30") } // Matches local metadata time

    var expandedCategory by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Transportation", "Food", "Housing", "Utilities", "Other")

    // Automatic redirect on successful creation
    LaunchedEffect(createState) {
        if (createState is CreateExpenseState.Success) {
            viewModel.resetCreateState()
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Record a New Expense",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                "Provide details of the transaction. This will instantly be sent to Kigali ABCD main database servers and matched against category budgets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    validationError = null
                },
                label = { Text("Expense Title / Merchant") },
                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_title_input"),
                singleLine = true,
                placeholder = { Text("e.g. Restaurant bill, Bus Ticket") }
            )

            // Category select Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { expandedCategory = !expandedCategory }) {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Expand categories")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_category_input")
                        .clickable { expandedCategory = !expandedCategory }
                )
                DropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                expandedCategory = false
                            },
                            modifier = Modifier.testTag("dropdown_item_$cat")
                        )
                    }
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    // Only support positive digit or floating representations
                    if (it.isEmpty() || it.toDoubleOrNull() != null || it.last() == '.') {
                        amount = it
                        validationError = null
                    }
                },
                label = { Text("Amount (RWF)") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_amount_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("e.g. 5000") }
            )

            // Description Input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("expense_description_input"),
                maxLines = 4,
                placeholder = { Text("Useful details about this transaction...") }
            )

            // Date input
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Transaction Date (YYYY-MM-DD)") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_date_input"),
                singleLine = true,
                placeholder = { Text("e.g. 2026-05-30") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error display
            val errorMsg = validationError ?: (createState as? CreateExpenseState.Error)?.message
            if (errorMsg != null) {
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_expense_error_message")
                )
            }

            // Submit Button
            Button(
                onClick = {
                    val parsedAmt = amount.toDoubleOrNull()
                    if (title.isBlank()) {
                        validationError = "Please specify an expense title."
                    } else if (parsedAmt == null || parsedAmt <= 0) {
                        validationError = "Please specify a positive amount greater than zero."
                    } else {
                        viewModel.addExpense(
                            title = title.trim(),
                            category = category,
                            amount = amount.trim(),
                            description = description.trim(),
                            date = date.trim()
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_expense_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = createState !is CreateExpenseState.Loading
            ) {
                if (createState is CreateExpenseState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save Expense",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
