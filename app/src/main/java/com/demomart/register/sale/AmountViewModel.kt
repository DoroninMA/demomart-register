package com.demomart.register.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.register.money.AmountInput
import com.demomart.register.receipt.ReceiptCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AmountViewModel @Inject constructor(
    private val flow: SaleFlowState,
    counter: ReceiptCounter,
) : ViewModel() {
    private val amount = MutableStateFlow(AmountInput(flow.amountCents.value))
    val input = amount.asStateFlow()
    val canContinue = input.map { it.cents > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, input.value.cents > 0)

    init {
        viewModelScope.launch { counter.syncOnce() }
    }

    fun onDigit(digit: Int) { amount.value = input.value.digit(digit) }

    fun onDoubleZero() { amount.value = input.value.doubleZero() }

    fun onBackspace() { amount.value = input.value.backspace() }

    fun onClear() { amount.value = input.value.clear() }

    fun continueToIdentify() {
        if (input.value.cents == 0L) return
        flow.setCustomer(null)
        flow.setAmount(input.value.cents)
    }
}
