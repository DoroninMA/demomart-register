package com.demomart.register.auth

import com.demomart.register.net.ApiResult
import com.demomart.register.testing.FakeReceiptCounter
import com.demomart.register.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val auth = FakeTokenProvider()
    private val counter = FakeReceiptCounter()
    private val model = LoginViewModel(auth, counter)

    @Test fun cashierLoginSucceeds() = runTest {
        model.setEmail("bob@demo.test")
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.done)
        assertTrue(auth.signedIn.value)
        assertEquals(1, counter.syncs)
    }

    @Test fun nonCashierRejected() = runTest {
        counter.next = ApiResult.Err("forbidden", "")
        model.setEmail("anna@demo.test")
        model.submit()
        advanceUntilIdle()
        assertEquals("This account is not a cashier", model.state.value.error)
        assertFalse(auth.signedIn.value)
    }

    @Test fun unknownFakeUserRejected() = runTest {
        counter.next = ApiResult.Err("session_required", "")
        model.setEmail("nobody@demo.test")
        model.submit()
        advanceUntilIdle()
        assertEquals("This account is not a cashier", model.state.value.error)
        assertFalse(auth.signedIn.value)
    }

    @Test fun serverDownOnLogin() = runTest {
        counter.next = ApiResult.Err("network", "")
        model.setEmail("bob@demo.test")
        model.submit()
        advanceUntilIdle()
        assertEquals("No connection to server", model.state.value.error)
        assertFalse(auth.signedIn.value)
    }
}
