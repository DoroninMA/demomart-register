package com.demomart.register.auth

import com.demomart.register.net.ApiResult
import com.demomart.register.net.errorText
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

interface AuthTokenProvider {
    val signedIn: StateFlow<Boolean>
    val displayName: StateFlow<String?>
    suspend fun signIn(email: String, password: String): ApiResult<Unit>
    suspend fun idToken(): String?
    fun signOut()
}

private fun nameFromEmail(email: String): String =
    email.substringBefore('@').replaceFirstChar { it.titlecase() }

class FakeTokenProvider : AuthTokenProvider {
    private val token = MutableStateFlow<String?>(null)
    private val signedInState = MutableStateFlow(false)
    private val name = MutableStateFlow<String?>(null)
    override val signedIn = signedInState.asStateFlow()
    override val displayName = name.asStateFlow()

    override suspend fun signIn(email: String, password: String): ApiResult<Unit> {
        val address = email.trim().lowercase()
        val local = address.substringBefore('@')
        val displayName = nameFromEmail(address)
        token.value = "fake:seed-$local:$address:$displayName"
        name.value = displayName
        signedInState.value = true
        return ApiResult.Ok(Unit)
    }

    override suspend fun idToken(): String? = token.value

    override fun signOut() {
        token.value = null
        name.value = null
        signedInState.value = false
    }
}

class FirebaseTokenProvider(private val auth: FirebaseAuth) : AuthTokenProvider {
    private val signedInState = MutableStateFlow(false)
    private val name = MutableStateFlow<String?>(null)
    override val signedIn = signedInState.asStateFlow()
    override val displayName = name.asStateFlow()

    init {
        updateSession()
        auth.addAuthStateListener { updateSession() }
    }

    override suspend fun signIn(email: String, password: String): ApiResult<Unit> = try {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        updateSession()
        ApiResult.Ok(Unit)
    } catch (_: FirebaseNetworkException) {
        ApiResult.Err("network", errorText("network"))
    } catch (_: FirebaseAuthException) {
        ApiResult.Err("unauthenticated", errorText("unauthenticated"))
    }

    override suspend fun idToken(): String? =
        auth.currentUser?.getIdToken(false)?.await()?.token

    override fun signOut() {
        auth.signOut()
        updateSession()
    }

    private fun updateSession() {
        val user = auth.currentUser
        name.value = user?.displayName ?: user?.email?.let(::nameFromEmail)
        signedInState.value = user != null
    }
}
