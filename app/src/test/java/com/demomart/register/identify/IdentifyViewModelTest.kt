package com.demomart.register.identify

import app.cash.turbine.test
import com.demomart.register.net.ApiResult
import com.demomart.register.net.Customer
import com.demomart.register.sale.SaleFlowState
import com.demomart.register.testing.MainDispatcherRule
import com.demomart.register.testing.StubPosRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdentifyViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val flow = SaleFlowState()
    private val customer = Customer("ref", "Anna", "DM 4821 0093", 1355)
    private val codes = mutableListOf<String>()
    private var answer = CompletableDeferred<ApiResult<Customer>>()
    private val repo = object : StubPosRepository() {
        override suspend fun identify(token: String): ApiResult<Customer> {
            codes += token
            return answer.await()
        }
    }
    private var time = Instant.EPOCH
    private val clock = object : Clock() {
        override fun instant() = time
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(time, zone)
    }
    private val model = IdentifyViewModel(repo, flow, clock)
    private val token = "DM1:48210093:123456"

    @Test fun qrAndNfcTogetherCallIdentifyOnce() = runTest {
        model.onScanned(token)
        model.onNfc(NfcReadResult.Token("DM1:48210093:000000"))
        runCurrent()
        assertEquals(listOf(token), codes)
        answer.complete(ApiResult.Ok(customer))
    }

    @Test fun sameQrRepeatedCallsOnce() = runTest {
        repeat(30) { model.onScanned(token) }
        runCurrent()
        assertEquals(listOf(token), codes)
        answer.complete(ApiResult.Ok(customer))
    }

    @Test fun successStoresCustomerAndNavigates() = runTest {
        model.navigateToSale.test {
            model.onScanned(token)
            answer.complete(ApiResult.Ok(customer))
            assertEquals(Unit, awaitItem())
            assertEquals(customer, flow.customer.value)
            model.onScanned("DM1:48210093:000000")
            runCurrent()
            assertEquals(1, codes.size)
        }
    }

    @Test fun expiredTokenShowsMessageAndAcceptsAgain() = runTest {
        model.onScanned(token)
        answer.complete(ApiResult.Err("token_expired", ""))
        runCurrent()
        assertEquals(IdentifyStatus.Error("Code expired, ask the customer to refresh"), model.state.value.status)
        model.onScanned("DM1:48210093:000000")
        runCurrent()
        assertEquals(2, codes.size)
    }

    @Test fun failedTextIgnoredFor3s() = runTest {
        model.onScanned(token)
        answer.complete(ApiResult.Err("token_expired", ""))
        runCurrent()
        time = time.plusMillis(1000)
        model.onScanned(token)
        runCurrent()
        assertEquals(1, codes.size)
        time = time.plusMillis(2100)
        model.onScanned(token)
        runCurrent()
        assertEquals(2, codes.size)
    }

    @Test fun nfcDisabledShowsBanner() {
        model.setNfcAvailability(NfcAvailability.Disabled)
        assertEquals(NfcAvailability.Disabled, model.state.value.nfc)
    }

    @Test fun nfcNotSignedInShowsMessage() {
        model.onNfc(NfcReadResult.NotSignedIn)
        assertEquals(IdentifyStatus.Error("Customer not signed in"), model.state.value.status)
        assertEquals(emptyList<String>(), codes)
    }

    @Test fun nfcAppNotFoundShowsMessage() {
        model.onNfc(NfcReadResult.AppNotFound)
        assertEquals(IdentifyStatus.Error("Customer app not found"), model.state.value.status)
    }

    @Test fun skipCardGoesToSaleWithoutCustomer() = runTest {
        flow.setCustomer(customer)
        model.navigateToSale.test {
            model.skipCard()
            assertEquals(Unit, awaitItem())
            assertNull(flow.customer.value)
        }
    }
}
