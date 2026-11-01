package com.demomart.register.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PosApi {
    @POST("v1/pos/identify")
    suspend fun identify(@Body request: IdentifyIn): Response<Customer>

    @POST("v1/pos/quote")
    suspend fun quote(@Body request: QuoteIn): Response<QuoteOut>

    @POST("v1/pos/commit")
    suspend fun commit(@Body request: CommitIn): Response<SaleResult>

    @POST("v1/pos/refund")
    suspend fun refund(@Body request: RefundIn): Response<SaleResult>

    @GET("v1/pos/sales")
    suspend fun recentSales(@Query("register_id") registerId: String): Response<List<SaleResult>>

    @GET("v1/pos/next-receipt")
    suspend fun nextReceipt(@Query("register_id") registerId: String): Response<NextReceipt>
}
