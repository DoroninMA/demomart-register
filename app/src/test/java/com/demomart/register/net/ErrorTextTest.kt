package com.demomart.register.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorTextTest {
    @Test fun errorsMapToEnglishText() {
        mapOf(
            "token_expired" to "Code expired, ask the customer to refresh",
            "token_invalid" to "Code not recognized",
            "member_not_found" to "Card not recognized",
            "customer_ref_expired" to "Customer session expired, scan the card again",
            "customer_ref_invalid" to "Customer session expired, scan the card again",
            "insufficient_points" to "Not enough points",
            "redeem_exceeds_amount" to "Points can't cover this amount",
            "redeem_step" to "Points can't cover this amount",
            "amount_invalid" to "Enter a valid amount",
            "already_refunded" to "This sale was already refunded",
            "sale_conflict" to "Receipt number clash, please retry",
            "forbidden" to "This account is not a cashier",
            "unauthenticated" to "Signed out, please log in again",
            "session_required" to "Signed out, please log in again",
            "network" to "No connection to server",
            "unknown" to "Something went wrong",
        ).forEach { (code, message) -> assertEquals(message, errorText(code)) }
    }
}
