package com.demomart.register.sales

import com.demomart.register.net.ApiResult
import com.demomart.register.net.SaleResult
import com.demomart.register.testing.MainDispatcherRule
import com.demomart.register.testing.StubPosRepository
import com.demomart.register.testing.sampleSale
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentSalesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val first = sampleSale(createdAt = "2026-11-09T14:02:00Z")
    private val newer = sampleSale(receipt = 128, createdAt = "2026-11-09T15:02:00Z")
    private var refund: ApiResult<SaleResult> = ApiResult.Ok(first.copy(status = "refunded", balance = 1355))
    private val repo = object : StubPosRepository() {
        override suspend fun recentSales() = ApiResult.Ok(listOf(first, newer))
        override suspend fun refund(receiptNo: Long): ApiResult<SaleResult> {
            assertEquals(first.receiptNo, receiptNo)
            return refund
        }
    }

    @Test fun loadsNewestFirst() = runTest {
        val model = RecentSalesViewModel(repo)
        advanceUntilIdle()
        assertEquals(listOf(newer, first), model.state.value.items)
    }

    @Test fun refundReplacesRow() = runTest {
        val model = RecentSalesViewModel(repo)
        advanceUntilIdle()
        model.refund(first.receiptNo)
        advanceUntilIdle()
        assertEquals(listOf("committed", "refunded"), model.state.value.items.map { it.status })
        assertEquals(1355L, model.state.value.items.last().balance)
    }

    @Test fun alreadyRefundedShowsMessage() = runTest {
        refund = ApiResult.Err("already_refunded", "")
        val model = RecentSalesViewModel(repo)
        advanceUntilIdle()
        model.refund(first.receiptNo)
        advanceUntilIdle()
        assertEquals("This sale was already refunded", model.state.value.error)
    }

    @Test fun rowTextForNoCardSale() {
        val sale = first.copy(displayName = null, paidCents = 2300, earnedPoints = 0, redeemPoints = 0)
        assertEquals(
            "14:02 · No card · €23.00",
            saleRowText(sale, ZoneOffset.UTC, LocalDate.of(2026, 11, 9)),
        )
    }

    @Test fun rowTextUsesDeviceZoneAndDate() {
        assertEquals(
            "Nov 9, 17:02 · Anna K. · €18.00 · +18 / −500 pts",
            saleRowText(first, ZoneOffset.ofHours(3), LocalDate.of(2026, 11, 10)),
        )
    }
}
