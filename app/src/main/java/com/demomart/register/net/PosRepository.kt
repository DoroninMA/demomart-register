package com.demomart.register.net

import com.demomart.register.BuildConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val code: String, val message: String) : ApiResult<Nothing>
}

interface PosRepository {
    suspend fun identify(token: String): ApiResult<Customer>
    suspend fun quote(customerRef: String?, amountCents: Long, redeemPoints: Long): ApiResult<QuoteOut>
    suspend fun commit(receiptNo: Long, customerRef: String?, amountCents: Long, redeemPoints: Long): ApiResult<SaleResult>
    suspend fun refund(receiptNo: Long): ApiResult<SaleResult>
    // before: the last sale already shown, to fetch the page after it
    suspend fun recentSales(before: SaleResult? = null): ApiResult<List<SaleResult>>
    suspend fun nextReceipt(): ApiResult<Long>
}

@Singleton
class RetrofitPosRepository @Inject constructor(
    private val api: PosApi,
    private val json: Json,
) : PosRepository {
    private val registerId = BuildConfig.REGISTER_ID

    override suspend fun identify(token: String) = request { api.identify(IdentifyIn(token)) }

    override suspend fun quote(customerRef: String?, amountCents: Long, redeemPoints: Long) =
        request { api.quote(QuoteIn(customerRef, amountCents, redeemPoints)) }

    override suspend fun commit(receiptNo: Long, customerRef: String?, amountCents: Long, redeemPoints: Long) =
        request { api.commit(CommitIn(registerId, receiptNo, customerRef, amountCents, redeemPoints)) }

    override suspend fun refund(receiptNo: Long) = request { api.refund(RefundIn(registerId, receiptNo)) }

    override suspend fun recentSales(before: SaleResult?) =
        request { api.recentSales(registerId, beforeAt = before?.createdAt, beforeId = before?.saleId) }

    override suspend fun nextReceipt(): ApiResult<Long> = when (val result = request { api.nextReceipt(registerId) }) {
        is ApiResult.Ok -> ApiResult.Ok(result.value.nextReceiptNo)
        is ApiResult.Err -> result
    }

    private suspend fun <T : Any> request(call: suspend () -> Response<T>): ApiResult<T> {
        try {
            val response = call()
            if (response.isSuccessful) return response.body()?.let { ApiResult.Ok(it) } ?: serverError
            val body = response.errorBody() ?: return serverError
            return body.use {
                if (response.code() >= 500) serverError
                else json.decodeFromString<ErrorBody>(it.string()).let { error ->
                    ApiResult.Err(error.error, error.message)
                }
            }
        } catch (_: SerializationException) {
            return serverError
        } catch (_: IOException) {
            return ApiResult.Err("network", "No connection to server")
        }
    }

    private companion object {
        val serverError = ApiResult.Err("server", "Something went wrong")
    }
}
