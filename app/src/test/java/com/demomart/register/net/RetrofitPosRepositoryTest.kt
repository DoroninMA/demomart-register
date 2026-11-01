package com.demomart.register.net

import com.demomart.register.auth.FakeTokenProvider
import com.demomart.register.di.NetworkModule
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RetrofitPosRepositoryTest {
    private val server = MockWebServer()
    private val auth = FakeTokenProvider()
    private lateinit var repo: PosRepository

    @Before fun setUp() {
        server.start()
        val json = NetworkModule.json()
        val client = NetworkModule.httpClient(auth).newBuilder()
            .retryOnConnectionFailure(false)
            .build()
        repo = RetrofitPosRepository(NetworkModule.posApi(NetworkModule.retrofit(client, json, server.url("/").toString())), json)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun identifyParsesCustomer() = runTest {
        auth.signIn("bob@demo.test", "")
        server.enqueue(MockResponse().setBody(
            """{"customer_ref":"r1","display_name":"Anna K.","card_display":"DM 4821 0093","balance":1355,"extra":true}"""
        ))
        val result = repo.identify("DM1:48210093:123456") as ApiResult.Ok
        assertEquals("Anna K.", result.value.displayName)
        assertEquals(1355L, result.value.balance)
        val request = server.takeRequest()
        assertEquals("/v1/pos/identify", request.path)
        assertEquals("Bearer fake:seed-bob:bob@demo.test:Bob", request.getHeader("Authorization"))
    }

    @Test fun errorBodyBecomesErr() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody(
            """{"error":"token_expired","message":"Refresh"}"""
        ))
        assertEquals(ApiResult.Err("token_expired", "Refresh"), repo.identify("DM1:48210093:123456"))
    }

    @Test fun ioFailureIsNetwork() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals("network", (repo.nextReceipt() as ApiResult.Err).code)
    }

    @Test fun commitSendsRegisterAndReceipt() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"sale_id":1,"register_id":"R1","receipt_no":127,"display_name":null,"amount_cents":2300,"redeem_points":0,"paid_cents":2300,"earned_points":0,"status":"committed","balance":null,"created_at":"2026-11-09T14:02:00Z"}"""
        ))
        val result = repo.commit(127, null, 2300, 0) as ApiResult.Ok
        assertEquals(127L, result.value.receiptNo)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"register_id\":\"R1\""))
        assertTrue(body.contains("\"receipt_no\":127"))
        assertTrue(body.contains("\"customer_ref\":null"))
    }

    @Test fun unauthorizedSignsOut() = runTest {
        auth.signIn("bob@demo.test", "")
        server.enqueue(MockResponse().setResponseCode(401).setBody(
            """{"error":"session_required","message":"Open a session first"}"""
        ))
        assertEquals("session_required", (repo.nextReceipt() as ApiResult.Err).code)
        assertFalse(auth.signedIn.value)
    }

    @Test fun invalidServerResponseIsServerError() = runTest {
        server.enqueue(MockResponse().setBody("""{"unexpected":42}"""))
        assertEquals("server", (repo.nextReceipt() as ApiResult.Err).code)
    }
}
