package com.demomart.register.receipt

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import com.demomart.register.net.ApiResult
import com.demomart.register.testing.StubPosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReceiptCounterTest {
    @get:Rule val directory = TemporaryFolder()
    private var next: ApiResult<Long> = ApiResult.Ok(128L)
    private var calls = 0
    private val repo = object : StubPosRepository() {
        override suspend fun nextReceipt(): ApiResult<Long> {
            calls++
            return next
        }
    }

    private fun counter(scope: CoroutineScope) = DataStoreReceiptCounter(
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                directory.root.resolve("register.preferences_pb").toOkioPath()
            },
            scope = scope,
        ),
        repo,
    )

    @Test fun freshInstallTakesServerValue() = runTest {
        val counter = counter(backgroundScope)
        assertEquals(ApiResult.Ok(128L), counter.sync())
        assertEquals(128L, counter.current())
    }

    @Test fun keepsLocalWhenAhead() = runTest {
        val counter = counter(backgroundScope)
        next = ApiResult.Ok(130L)
        counter.sync()
        next = ApiResult.Ok(128L)
        assertEquals(ApiResult.Ok(128L), counter.sync())
        assertEquals(130L, counter.current())
    }

    @Test fun advanceIncrements() = runTest {
        val job = Job()
        val first = counter(CoroutineScope(backgroundScope.coroutineContext + job))
        first.sync()
        first.advance()
        job.cancelAndJoin()
        assertEquals(129L, counter(backgroundScope).current())
    }

    @Test fun syncFailureKeepsLocal() = runTest {
        val counter = counter(backgroundScope)
        counter.sync()
        next = ApiResult.Err("network", "")
        assertEquals(next, counter.sync())
        assertEquals(128L, counter.current())
    }

    @Test fun syncOnceRunsOnce() = runTest {
        val counter = counter(backgroundScope)
        counter.syncOnce()
        counter.syncOnce()
        assertEquals(1, calls)
    }
}
