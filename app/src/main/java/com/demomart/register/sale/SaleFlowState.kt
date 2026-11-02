package com.demomart.register.sale

import com.demomart.register.net.Customer
import com.demomart.register.net.SaleResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SaleFlowState @Inject constructor() {
    private val amount = MutableStateFlow(0L)
    private val identified = MutableStateFlow<Customer?>(null)
    private val result = MutableStateFlow<SaleResult?>(null)
    private val notice = MutableStateFlow<String?>(null)

    val amountCents = amount.asStateFlow()
    val customer = identified.asStateFlow()
    val lastResult = result.asStateFlow()
    val resultNotice = notice.asStateFlow()

    fun setAmount(cents: Long) { amount.value = cents }

    fun setCustomer(customer: Customer?) { identified.value = customer }

    fun setResult(result: SaleResult, notice: String? = null) {
        this.result.value = result
        this.notice.value = notice
    }

    fun startNew() {
        amount.value = 0L
        identified.value = null
        result.value = null
        notice.value = null
    }
}
