package com.demomart.register.identify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.register.net.ApiResult
import com.demomart.register.net.Customer
import com.demomart.register.net.PosRepository
import com.demomart.register.net.errorText
import com.demomart.register.sale.SaleFlowState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface IdentifyStatus {
    data object Waiting : IdentifyStatus
    data object Checking : IdentifyStatus
    data class Error(val text: String) : IdentifyStatus
}

data class IdentifyState(
    val status: IdentifyStatus = IdentifyStatus.Waiting,
    val nfc: NfcAvailability = NfcAvailability.Unsupported,
)

@HiltViewModel
class IdentifyViewModel @Inject constructor(
    private val repo: PosRepository,
    private val flow: SaleFlowState,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(IdentifyState())
    val state = mutableState.asStateFlow()
    private val goToSale = MutableSharedFlow<Unit>()
    val navigateToSale = goToSale.asSharedFlow()
    private val failedUntil = mutableMapOf<String, Long>()
    private var finished = false

    fun onScanned(text: String) {
        if (!accepting() || !text.startsWith("DM")) return
        val now = clock.millis()
        failedUntil.entries.removeAll { it.value <= now }
        if (text in failedUntil) return
        mutableState.value = state.value.copy(status = IdentifyStatus.Checking)
        viewModelScope.launch {
            when (val result = repo.identify(text)) {
                is ApiResult.Ok -> finish(result.value)
                is ApiResult.Err -> {
                    failedUntil[text] = clock.millis() + 3000
                    showError(errorText(result.code))
                }
            }
        }
    }

    fun onNfc(result: NfcReadResult) {
        if (!accepting()) return
        when (result) {
            is NfcReadResult.Token -> onScanned(result.text)
            NfcReadResult.NotSignedIn -> showError("Customer not signed in")
            NfcReadResult.AppNotFound -> showError("Customer app not found")
            NfcReadResult.WalletUnreadable -> showError("Card in Google Wallet couldn't be read")
            is NfcReadResult.Failure -> showError("Hold the phone still and try again")
        }
    }

    fun setNfcAvailability(availability: NfcAvailability) {
        mutableState.value = state.value.copy(nfc = availability)
    }

    fun skipCard() {
        if (accepting()) finish(null)
    }

    private fun accepting() = !finished && state.value.status != IdentifyStatus.Checking

    private fun showError(message: String) {
        mutableState.value = state.value.copy(status = IdentifyStatus.Error(message))
    }

    private fun finish(customer: Customer?) {
        finished = true
        flow.setCustomer(customer)
        viewModelScope.launch { goToSale.emit(Unit) }
    }
}
