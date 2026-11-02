package com.demomart.register.sale

import com.demomart.register.net.Customer
import com.demomart.register.net.SaleResult
import com.demomart.register.testing.FakeReceiptCounter
import com.demomart.register.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmountViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val flow = SaleFlowState()
    private val counter = FakeReceiptCounter()

    @Test fun continueDisabledAtZero() {
        assertFalse(AmountViewModel(flow, counter).canContinue.value)
    }

    @Test fun continueWritesAmount() = runTest {
        val model = AmountViewModel(flow, counter)
        listOf(2, 3, 0, 0).forEach(model::onDigit)
        advanceUntilIdle()
        assertTrue(model.canContinue.value)
        model.continueToIdentify()
        assertEquals(2300L, flow.amountCents.value)
    }

    @Test fun startNewResetsFlow() {
        flow.setAmount(2300)
        flow.setCustomer(Customer("ref", "Anna", "DM 4821 0093", 1355))
        flow.setResult(
            SaleResult(1, "R1", 127, "Anna", 2300, 500, 1800, 18, "committed", 873, "2026-11-09T14:02:00Z"),
            "This sale went through",
        )
        flow.startNew()
        assertEquals(0L, flow.amountCents.value)
        assertNull(flow.customer.value)
        assertNull(flow.lastResult.value)
        assertNull(flow.resultNotice.value)
    }

    @Test fun amountKeptWhenReturning() {
        flow.setAmount(2300)
        assertEquals(2300L, AmountViewModel(flow, counter).input.value.cents)
    }

    @Test fun initCallsSyncOnce() = runTest {
        AmountViewModel(flow, counter)
        advanceUntilIdle()
        assertEquals(1, counter.startupSyncs)
    }
}
