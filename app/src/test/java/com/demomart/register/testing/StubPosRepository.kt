package com.demomart.register.testing

import com.demomart.register.net.ApiResult
import com.demomart.register.net.Customer
import com.demomart.register.net.PosRepository
import com.demomart.register.net.QuoteOut
import com.demomart.register.net.SaleResult

open class StubPosRepository : PosRepository {
    override suspend fun identify(token: String): ApiResult<Customer> = error("Unexpected identify")
    override suspend fun quote(customerRef: String?, amountCents: Long, redeemPoints: Long): ApiResult<QuoteOut> =
        error("Unexpected quote")
    override suspend fun commit(receiptNo: Long, customerRef: String?, amountCents: Long, redeemPoints: Long): ApiResult<SaleResult> =
        error("Unexpected commit")
    override suspend fun refund(receiptNo: Long): ApiResult<SaleResult> = error("Unexpected refund")
    override suspend fun recentSales(): ApiResult<List<SaleResult>> = error("Unexpected recentSales")
    override suspend fun nextReceipt(): ApiResult<Long> = error("Unexpected nextReceipt")
}
