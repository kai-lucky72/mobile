package com.example.data

import android.content.Context
import androidx.room.Room
import com.example.data.api.RetrofitClient
import com.example.data.local.FinanceDatabase
import com.example.data.repository.FinanceRepository

object ServiceLocator {
    private var database: FinanceDatabase? = null
    var repository: FinanceRepository? = null
        private set

    fun init(context: Context) {
        if (database == null) {
            database = Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "finance_tracker_db"
            )
            .fallbackToDestructiveMigration()
            .build()
            
            repository = FinanceRepository(
                apiService = RetrofitClient.apiService,
                financeDao = database!!.financeDao()
            )
        }
    }
}
