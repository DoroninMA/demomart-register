package com.demomart.register.testing

import com.demomart.register.net.ApiResult
import com.demomart.register.receipt.ReceiptCounter

class FakeReceiptCounter(
    var number: Long = 1,
    var next: ApiResult<Long> = ApiResult.Ok(number),
) : ReceiptCounter {
    var syncs = 0
        private set
    var startupSyncs = 0
        private set

    override suspend fun sync(): ApiResult<Long> {
        syncs++
        (next as? ApiResult.Ok)?.let { number = maxOf(number, it.value) }
        return next
    }

    override suspend fun syncOnce() {
        startupSyncs++
    }

    override suspend fun current(): Long = number

    override suspend fun advance() {
        number++
    }
}
