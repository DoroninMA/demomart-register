package com.demomart.register.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.demomart.register.net.PosRepository
import com.demomart.register.net.RetrofitPosRepository
import com.demomart.register.receipt.DataStoreReceiptCounter
import com.demomart.register.receipt.ReceiptCounter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun posRepository(impl: RetrofitPosRepository): PosRepository

    @Binds
    abstract fun receiptCounter(impl: DataStoreReceiptCounter): ReceiptCounter

    companion object {
        @Provides @Singleton
        fun preferences(@ApplicationContext context: Context) =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("register") }

        @Provides
        fun clock(): Clock = Clock.systemUTC()
    }
}
