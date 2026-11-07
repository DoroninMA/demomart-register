package com.demomart.register.testing

import com.demomart.register.net.Customer
import com.demomart.register.net.QuoteOut
import com.demomart.register.net.SaleResult

val anna = Customer("ref-anna", "Anna K.", "DM 4821 0093", 1355)

fun sampleSale(
    receipt: Long = 127,
    status: String = "committed",
    balance: Long? = 873,
    name: String? = "Anna K.",
    createdAt: String = "2026-11-09T14:02:00Z",
) = SaleResult(
    receipt, "R1", receipt, name, 2300, 500, 1800, 18, status, balance, createdAt,
)

fun sampleQuote(amount: Long, redeem: Long, max: Long, hasCustomer: Boolean) = QuoteOut(
    amount, redeem, redeem, amount - redeem, if (hasCustomer) (amount - redeem) / 100 else 0, max,
)
