package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val username: String,
    val password: String,
    val createdAt: String? = null,
    val budget: Double? = 0.0,
    val budgets: List<Budget>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class Budget(
    val category: String,
    val amount: Double,
    val period: String? = "Monthly"
)

@JsonClass(generateAdapter = true)
data class Expense(
    val id: String? = null,
    val createdAt: String? = null,
    val name: String? = "",
    val title: String? = "",
    val amount: Any? = null, // Can be Double or String from mock API
    val description: String? = "",
    val userId: String? = null,
    val category: String? = "Other",
    val date: String? = null
) {
    val displayTitle: String
        get() = when {
            !title.isNullOrBlank() -> title
            !name.isNullOrBlank() -> name
            else -> "Untitled Expense"
        }

    val amountDouble: Double
        get() = when (amount) {
            is Number -> amount.toDouble()
            is String -> amount.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
}
