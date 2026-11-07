package com.demomart.register.sale

import app.cash.turbine.test
import com.demomart.register.net.ApiResult
import com.demomart.register.net.SaleResult
import com.demomart.register.testing.MainDispatcherRule
import com.demomart.register.testing.StubPosRepository
import com.demomart.register.testing.anna
import com.demomart.register.testing.sampleSale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val flow = SaleFlowState().apply { setAmount(2300); setCustomer(anna); setResult(sampleSale()) }
    private var answer: ApiResult<SaleResult> = ApiResult.Ok(sampleSale(status = "refunded", balance = 1355))
    private val repo = object : StubPosRepository() {
        override suspend fun refund(receiptNo: Long): ApiResult<SaleResult> {
            assertEquals(127L, receiptNo)
            return answer
        }
    }

    @Test fun refundReplacesResult() = runTest {
        val model = ResultViewModel(repo, flow)
        model.refund()
        advanceUntilIdle()
        assertEquals("refunded", model.state.value.result.status)
        assertEquals(model.state.value.result, flow.lastResult.value)
    }

    @Test fun refundAlreadyRefunded() = runTest {
        answer = ApiResult.Err("already_refunded", "")
        val model = ResultViewModel(repo, flow)
        model.refund()
        advanceUntilIdle()
        assertEquals("This sale was already refunded", model.state.value.error)
    }

    @Test fun newSaleResetsFlow() = runTest {
        val model = ResultViewModel(repo, flow)
        model.navigateToAmount.test {
            model.newSale()
            assertEquals(Unit, awaitItem())
            assertEquals(0L, flow.amountCents.value)
            assertNull(flow.customer.value)
        }
    }
}
