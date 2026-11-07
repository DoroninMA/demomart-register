package com.demomart.register.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.register.net.ApiResult
import com.demomart.register.net.Customer
import com.demomart.register.net.PosRepository
import com.demomart.register.net.QuoteOut
import com.demomart.register.net.SaleResult
import com.demomart.register.net.errorText
import com.demomart.register.receipt.ReceiptCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SaleState(
    val customer: Customer?,
    val amountCents: Long,
    val redeemPoints: Long = 0,
    val quote: QuoteOut? = null,
    val quoting: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val canRetry: Boolean = false,
) {
    val canConfirm: Boolean
        get() = !quoting && !busy && !canRetry && quote?.redeemPoints == redeemPoints
}

@HiltViewModel
class SaleViewModel @Inject constructor(
    private val repo: PosRepository,
    private val counter: ReceiptCounter,
    private val flow: SaleFlowState,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SaleState(flow.customer.value, flow.amountCents.value))
    val state = mutableState.asStateFlow()
    private val goToResult = MutableSharedFlow<Unit>()
    private val goToIdentify = MutableSharedFlow<Unit>()
    private val goToAmount = MutableSharedFlow<Unit>()
    val navigateToResult = goToResult.asSharedFlow()
    val navigateToIdentify = goToIdentify.asSharedFlow()
    val navigateToAmount = goToAmount.asSharedFlow()

    private var quoteJob: Job? = null
    private var pending: PendingSale? = null

    init { requestQuote(0, debounce = false) }

    fun setRedeem(points: Long) {
        val sale = state.value
        if (sale.busy || sale.canRetry || sale.customer == null) return
        val max = sale.quote?.maxRedeemPoints ?: return
        val redeem = points.coerceIn(0, max) / 100 * 100
        if (redeem != sale.redeemPoints) requestQuote(redeem, debounce = true)
    }

    fun retryQuote() {
        val sale = state.value
        if (!sale.quoting && !sale.busy && !sale.canRetry) requestQuote(sale.redeemPoints, debounce = false)
    }

    private fun requestQuote(points: Long, debounce: Boolean) {
        quoteJob?.cancel()
        val sale = state.value
        mutableState.value = sale.copy(redeemPoints = points, quoting = true, error = null)
        quoteJob = viewModelScope.launch {
            if (debounce) delay(200)
            val ref = sale.customer?.customerRef
            val first = repo.quote(ref, sale.amountCents, points)
            val response = if (first is ApiResult.Err && first.code == "insufficient_points" && points > 0) {
                when (val zero = repo.quote(ref, sale.amountCents, 0)) {
                    is ApiResult.Err -> zero
                    is ApiResult.Ok -> {
                        val reduced = minOf(points, zero.value.maxRedeemPoints)
                        mutableState.value = state.value.copy(redeemPoints = reduced, quote = zero.value)
                        if (reduced == 0L) zero else repo.quote(ref, sale.amountCents, reduced)
                    }
                }
            } else first

            when (response) {
                is ApiResult.Ok -> {
                    val quote = response.value
                    val reduced = minOf(quote.redeemPoints, quote.maxRedeemPoints)
                    mutableState.value = state.value.copy(quote = quote, quoting = false)
                    if (reduced != quote.redeemPoints) requestQuote(reduced, debounce = false)
                }
                is ApiResult.Err -> {
                    mutableState.value = state.value.copy(quote = null, quoting = false)
                    if (response.code == "customer_ref_expired") rescan()
                    else showError(errorText(response.code))
                }
            }
        }
    }

    fun confirm() {
        val sale = state.value
        if (!sale.canConfirm) return
        mutableState.value = sale.copy(busy = true, error = null)
        viewModelScope.launch {
            val attempt = PendingSale(counter.current(), sale.customer, sale.amountCents, sale.redeemPoints)
            pending = attempt
            send(attempt)
        }
    }

    fun retry() {
        val attempt = pending ?: return
        val sale = state.value
        if (sale.busy || !sale.canRetry) return
        mutableState.value = sale.copy(busy = true, error = null)
        viewModelScope.launch { send(attempt) }
    }

    private suspend fun send(attempt: PendingSale) {
        when (val response = repo.commit(attempt.receipt, attempt.customer?.customerRef, attempt.amount, attempt.redeem)) {
            is ApiResult.Ok -> {
                counter.advance()
                finish(response.value)
            }
            is ApiResult.Err -> when (response.code) {
                "network", "server" -> keepPending(errorText(response.code))
                "sale_conflict" -> {
                    pending = null
                    counter.sync()
                    showError(errorText(response.code))
                }
                "customer_ref_expired" -> rescan()
                else -> {
                    pending = null
                    showError(errorText(response.code))
                }
            }
        }
    }

    fun cancelPending() {
        val attempt = pending ?: return
        val sale = state.value
        if (!sale.canRetry || sale.busy) return
        mutableState.value = sale.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val next = counter.sync()) {
                is ApiResult.Err -> keepPending("No connection to server")
                is ApiResult.Ok -> if (next.value <= attempt.receipt) {
                    pending = null
                    flow.setCustomer(null)
                    goToAmount.deliver()
                } else {
                    when (val recent = repo.recentSales()) {
                        is ApiResult.Err -> keepPending(errorText(recent.code))
                        is ApiResult.Ok -> {
                            val result = recent.value.firstOrNull { attempt.matches(it) }
                            if (result == null) keepPending("Could not confirm this sale. Retry to check it.")
                            else finish(result, "This sale went through")
                        }
                    }
                }
            }
        }
    }

    private suspend fun finish(result: SaleResult, notice: String? = null) {
        pending = null
        flow.setResult(result, notice)
        goToResult.deliver()
    }

    private suspend fun rescan() {
        pending = null
        flow.setCustomer(null)
        goToIdentify.deliver()
    }

    private fun showError(message: String) {
        mutableState.value = state.value.copy(busy = false, error = message, canRetry = false)
    }

    private fun keepPending(message: String) {
        mutableState.value = state.value.copy(busy = false, error = message, canRetry = true)
    }

    private suspend fun MutableSharedFlow<Unit>.deliver() {
        subscriptionCount.first { it > 0 }
        emit(Unit)
    }

    private data class PendingSale(
        val receipt: Long,
        val customer: Customer?,
        val amount: Long,
        val redeem: Long,
    ) {
        fun matches(result: SaleResult): Boolean =
            result.receiptNo == receipt && result.amountCents == amount && result.redeemPoints == redeem &&
                result.displayName == customer?.displayName
    }
}
