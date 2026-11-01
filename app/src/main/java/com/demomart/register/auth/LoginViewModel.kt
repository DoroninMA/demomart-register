package com.demomart.register.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.register.net.ApiResult
import com.demomart.register.net.errorText
import com.demomart.register.receipt.ReceiptCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthTokenProvider,
    private val counter: ReceiptCounter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state = mutableState.asStateFlow()

    fun setEmail(email: String) {
        mutableState.value = state.value.copy(email = email, error = null)
    }

    fun setPassword(password: String) {
        mutableState.value = state.value.copy(password = password, error = null)
    }

    fun submit() {
        val form = state.value
        if (form.busy || form.done || form.email.isBlank()) return
        mutableState.value = form.copy(busy = true, error = null)
        viewModelScope.launch {
            when (val login = auth.signIn(form.email, form.password)) {
                is ApiResult.Err -> fail(errorText(login.code))
                is ApiResult.Ok -> when (val sync = counter.sync()) {
                    is ApiResult.Ok -> mutableState.value = state.value.copy(
                        busy = false, password = "", done = true,
                    )
                    is ApiResult.Err -> fail(
                        if (sync.code == "forbidden" || sync.code == "session_required") {
                            errorText("forbidden")
                        } else errorText(sync.code)
                    )
                }
            }
        }
    }

    private fun fail(message: String) {
        auth.signOut()
        mutableState.value = state.value.copy(busy = false, error = message)
    }
}
