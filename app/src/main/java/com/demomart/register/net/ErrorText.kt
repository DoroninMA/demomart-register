package com.demomart.register.net

fun errorText(code: String): String = when (code) {
    "token_expired" -> "Code expired, ask the customer to refresh"
    "token_invalid" -> "Code not recognized"
    "member_not_found" -> "Card not recognized"
    "customer_ref_expired", "customer_ref_invalid" -> "Customer session expired, scan the card again"
    "insufficient_points" -> "Not enough points"
    "redeem_exceeds_amount", "redeem_step" -> "Points can't cover this amount"
    "amount_invalid" -> "Enter a valid amount"
    "already_refunded" -> "This sale was already refunded"
    "sale_conflict" -> "Receipt number clash, please retry"
    "forbidden" -> "This account is not a cashier"
    "unauthenticated", "session_required" -> "Signed out, please log in again"
    "network" -> "No connection to server"
    else -> "Something went wrong"
}
