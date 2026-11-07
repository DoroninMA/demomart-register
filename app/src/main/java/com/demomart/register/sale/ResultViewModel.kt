package com.demomart.register.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.register.net.ApiResult
import com.demomart.register.net.PosRepository
import com.demomart.register.net.SaleResult
import com.demomart.register.net.errorText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResultState(
    val result: SaleResult,
    val notice: String? = null,
    val refunding: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repo: PosRepository,
    private val flow: SaleFlowState,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ResultState(checkNotNull(flow.lastResult.value), flow.resultNotice.value))
    val state = mutableState.asStateFlow()
    private val goToAmount = MutableSharedFlow<Unit>()
    val navigateToAmount = goToAmount.asSharedFlow()

    fun refund() {
        val sale = state.value
        if (sale.refunding || sale.result.status == "refunded") return
        mutableState.value = sale.copy(refunding = true, error = null)
        viewModelScope.launch {
            mutableState.value = when (val result = repo.refund(sale.result.receiptNo)) {
                is ApiResult.Ok -> {
                    flow.setResult(result.value, sale.notice)
                    sale.copy(result = result.value, refunding = false)
                }
                is ApiResult.Err -> sale.copy(refunding = false, error = errorText(result.code))
            }
        }
    }

    fun newSale() {
        if (state.value.refunding) return
        flow.startNew()
        viewModelScope.launch { goToAmount.emit(Unit) }
    }
}
