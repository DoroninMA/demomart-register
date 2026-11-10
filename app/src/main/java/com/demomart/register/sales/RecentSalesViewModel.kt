package com.demomart.register.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.register.net.ApiResult
import com.demomart.register.net.PosRepository
import com.demomart.register.net.SALES_PAGE
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
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

private val newestFirst = compareByDescending<SaleResult> { it.createdAt }.thenByDescending { it.saleId }

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
                    items = response.value.sortedWith(newestFirst),
                    hasMore = response.value.size == SALES_PAGE,
                )
                is ApiResult.Err -> current.copy(error = errorText(response.code))
            }
        }
    }

    fun loadMore() {
        val current = state.value
        val last = current.items.lastOrNull()
        if (!current.hasMore || current.loading || current.loadingMore || last == null) return
        mutableState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val response = repo.recentSales(before = last)
            val latest = state.value
            mutableState.value = when (response) {
                is ApiResult.Ok -> {
                    val known = latest.items.mapTo(HashSet()) { it.saleId }
                    // a server that ignores the cursor sends the first page again
                    val fresh = response.value.filter { it.saleId !in known }.sortedWith(newestFirst)
                    latest.copy(items = latest.items + fresh, hasMore = fresh.size == SALES_PAGE, loadingMore = false)
                }
                is ApiResult.Err -> latest.copy(loadingMore = false, error = errorText(response.code))
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
