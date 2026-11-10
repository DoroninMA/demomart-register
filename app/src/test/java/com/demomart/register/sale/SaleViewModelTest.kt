package com.demomart.register.sale

import app.cash.turbine.test
import com.demomart.register.net.ApiResult
import com.demomart.register.net.QuoteOut
import com.demomart.register.net.SaleResult
import com.demomart.register.testing.FakeReceiptCounter
import com.demomart.register.testing.MainDispatcherRule
import com.demomart.register.testing.StubPosRepository
import com.demomart.register.testing.anna
import com.demomart.register.testing.sampleQuote
import com.demomart.register.testing.sampleSale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaleViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val flow = SaleFlowState().apply { setAmount(2300); setCustomer(anna) }
    private val counter = FakeReceiptCounter(number = 127)
    private val quoted = mutableListOf<Long>()
    private data class Commit(val receipt: Long, val ref: String?, val amount: Long, val redeem: Long)
    private val committed = mutableListOf<Commit>()
    private var max = 1300L
    private var quoteError: ApiResult.Err? = null
    private var commitAnswer: ApiResult<SaleResult> = ApiResult.Ok(sampleSale())
    private var recentAnswer: ApiResult<List<SaleResult>> = ApiResult.Ok(listOf(sampleSale()))
    private val repo = object : StubPosRepository() {
        override suspend fun quote(customerRef: String?, amountCents: Long, redeemPoints: Long): ApiResult<QuoteOut> {
            quoted += redeemPoints
            quoteError?.let { return it }
            val limit = if (customerRef == null) 0L else max
            return if (redeemPoints > limit) ApiResult.Err("insufficient_points", "")
            else ApiResult.Ok(sampleQuote(amountCents, redeemPoints, limit, customerRef != null))
        }

        override suspend fun commit(receiptNo: Long, customerRef: String?, amountCents: Long, redeemPoints: Long): ApiResult<SaleResult> {
            committed += Commit(receiptNo, customerRef, amountCents, redeemPoints)
            return commitAnswer
        }

        override suspend fun recentSales(before: SaleResult?) = recentAnswer
    }

    @Test fun initialQuoteSetsMax() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        assertEquals(1300L, model.state.value.quote?.maxRedeemPoints)
        assertEquals(listOf(0L), quoted)
    }

    @Test fun redeemRoundsAndClamps() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.setRedeem(550)
        advanceUntilIdle()
        assertEquals(500L, model.state.value.redeemPoints)
        model.setRedeem(9999)
        advanceUntilIdle()
        assertEquals(1300L, model.state.value.redeemPoints)
    }

    @Test fun confirmCommitsWithCurrentReceipt() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()
        assertEquals(Commit(127, anna.customerRef, 2300, 0), committed.single())
        assertEquals(128L, counter.current())
    }

    @Test fun networkErrorRetrySameReceipt() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        commitAnswer = ApiResult.Err("network", "")
        model.confirm()
        advanceUntilIdle()
        assertEquals(127L, counter.current())
        assertTrue(model.state.value.canRetry)
        commitAnswer = ApiResult.Ok(sampleSale())
        model.retry()
        advanceUntilIdle()
        assertEquals(2, committed.size)
        assertEquals(committed.first(), committed.last())
        assertEquals(128L, counter.current())
    }

    @Test fun saleConflictResyncs() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        commitAnswer = ApiResult.Err("sale_conflict", "")
        counter.next = ApiResult.Ok(128L)
        model.confirm()
        advanceUntilIdle()
        assertEquals(1, counter.syncs)
        assertEquals("Receipt number clash, please retry", model.state.value.error)
    }

    @Test fun redeemIsCalculatedLocally() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.setRedeem(500)
        val quote = model.state.value.quote!!
        assertEquals(listOf(0L), quoted)
        assertEquals(500L, quote.redeemPoints)
        assertEquals(500L, quote.discountCents)
        assertEquals(1800L, quote.paidCents)
        assertEquals(18L, quote.earnPoints)
        assertEquals(1300L, quote.maxRedeemPoints)
    }

    @Test fun commitRejectedForPointsRefreshesMax() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.setRedeem(1300)
        max = 300
        commitAnswer = ApiResult.Err("insufficient_points", "")
        model.confirm()
        advanceUntilIdle()
        assertEquals(0L, model.state.value.redeemPoints)
        assertEquals(300L, model.state.value.quote?.maxRedeemPoints)
        assertEquals("Not enough points", model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun noCustomerHidesRedeemAndCommitsNullRef() = runTest {
        flow.setCustomer(null)
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.setRedeem(500)
        assertEquals(0L, model.state.value.redeemPoints)
        model.confirm()
        advanceUntilIdle()
        assertNull(committed.single().ref)
    }

    @Test fun confirmDisabledWhileQuoting() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        model.confirm()
        advanceUntilIdle()
        assertTrue(committed.isEmpty())
    }

    @Test fun confirmRightAfterRedeemCommitsIt() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.setRedeem(500)
        model.confirm()
        advanceUntilIdle()
        assertEquals(500L, committed.single().redeem)
    }

    @Test fun redeemLockedWhileRetryPending() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.setRedeem(500)
        advanceUntilIdle()
        commitAnswer = ApiResult.Err("network", "")
        model.confirm()
        advanceUntilIdle()
        model.setRedeem(0)
        assertEquals(500L, model.state.value.redeemPoints)
    }

    @Test fun cancelWhenSaleNotRecorded() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        commitAnswer = ApiResult.Err("network", "")
        model.confirm()
        advanceUntilIdle()
        model.navigateToAmount.test {
            model.cancelPending()
            assertEquals(Unit, awaitItem())
        }
    }

    @Test fun cancelWhenSaleWentThrough() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        model.setRedeem(500)
        advanceUntilIdle()
        commitAnswer = ApiResult.Err("network", "")
        model.confirm()
        advanceUntilIdle()
        counter.next = ApiResult.Ok(128L)
        model.navigateToResult.test {
            model.cancelPending()
            assertEquals(Unit, awaitItem())
            assertEquals(sampleSale(), flow.lastResult.value)
            assertEquals("This sale went through", flow.resultNotice.value)
        }
    }

    @Test fun cancelWhileStillOffline() = runTest {
        val model = SaleViewModel(repo, counter, flow)
        advanceUntilIdle()
        commitAnswer = ApiResult.Err("network", "")
        model.confirm()
        advanceUntilIdle()
        counter.next = ApiResult.Err("network", "")
        model.cancelPending()
        advanceUntilIdle()
        assertTrue(model.state.value.canRetry)
        assertEquals("No connection to server", model.state.value.error)
    }

    @Test fun expiredRefOnQuoteGoesToIdentify() = runTest {
        quoteError = ApiResult.Err("customer_ref_expired", "")
        val model = SaleViewModel(repo, counter, flow)
        model.navigateToIdentify.test { assertEquals(Unit, awaitItem()) }
    }
}
