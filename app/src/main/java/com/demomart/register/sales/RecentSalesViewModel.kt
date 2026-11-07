package com.demomart.register.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.register.net.ApiResult
import com.demomart.register.net.PosRepository
import com.demomart.register.net.SaleResult
import com.demomart.register.net.errorText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecentSalesState(
    val items: List<SaleResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val refunding: Long? = null,
)

@HiltViewModel
class RecentSalesViewModel @Inject constructor(private val repo: PosRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(RecentSalesState())
    val state = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        val current = state.value
        if (current.loading || current.refunding != null) return
        mutableState.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            mutableState.value = when (val response = repo.recentSales()) {
                is ApiResult.Ok -> RecentSalesState(
                    items = response.value.sortedWith(compareByDescending<SaleResult> { it.createdAt }
                        .thenByDescending { it.saleId }),
                )
                is ApiResult.Err -> current.copy(error = errorText(response.code))
            }
        }
    }

    fun refund(receiptNo: Long) {
        val current = state.value
        if (current.loading || current.refunding != null ||
            current.items.none { it.receiptNo == receiptNo && it.status != "refunded" }
        ) return
        mutableState.value = current.copy(refunding = receiptNo, error = null)
        viewModelScope.launch {
            mutableState.value = when (val response = repo.refund(receiptNo)) {
                is ApiResult.Ok -> current.copy(
                    items = current.items.map { if (it.receiptNo == receiptNo) response.value else it },
                    error = null,
                )
                is ApiResult.Err -> current.copy(error = errorText(response.code))
            }
        }
    }
}
