package com.example.data.api

import com.example.data.model.User
import com.example.data.model.Expense
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

interface FinanceApiService {
    @GET("users")
    suspend fun getUsersByUsername(
        @Query("username") username: String
    ): List<User>

    @GET("expenses")
    suspend fun getAllExpenses(): List<Expense>

    @GET("expenses/{id}")
    suspend fun getExpenseDetails(
        @Path("id") id: String
    ): Expense

    @POST("expenses")
    suspend fun createExpense(
        @Body expense: Expense
    ): Expense

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(
        @Path("id") id: String
    ): Expense
}

object RetrofitClient {
    private const val BASE_URL = "https://67ac71475853dfff53dab929.mockapi.io/api/v1/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val apiService: FinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FinanceApiService::class.java)
    }
}
