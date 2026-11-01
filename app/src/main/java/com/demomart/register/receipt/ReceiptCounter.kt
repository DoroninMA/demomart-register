package com.demomart.register.receipt

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.demomart.register.net.ApiResult
import com.demomart.register.net.PosRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ReceiptCounter {
    suspend fun sync(): ApiResult<Long>
    suspend fun syncOnce()
    suspend fun current(): Long
    suspend fun advance()
}

@Singleton
class DataStoreReceiptCounter @Inject constructor(
    private val store: DataStore<Preferences>,
    private val repo: PosRepository,
) : ReceiptCounter {
    private val startupLock = Mutex()
    private var didStartupSync = false

    override suspend fun sync(): ApiResult<Long> {
        val result = repo.nextReceipt()
        if (result is ApiResult.Ok) {
            store.edit { prefs -> prefs[NEXT] = result.value }
        }
        return result
    }

    override suspend fun syncOnce() = startupLock.withLock {
        if (!didStartupSync) {
            sync()
            didStartupSync = true
        }
    }

    override suspend fun current(): Long = next(store.data.first())

    override suspend fun advance() {
        store.edit { prefs -> prefs[NEXT] = next(prefs) + 1 }
    }

    private fun next(prefs: Preferences): Long = (prefs[NEXT] ?: 1L).coerceAtLeast(1)

    private companion object {
        val NEXT = longPreferencesKey("next_receipt_no")
    }
}
