package com.demomart.register

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.demomart.register.auth.AuthTokenProvider
import com.demomart.register.auth.LoginScreen
import com.demomart.register.identify.IdentifyScreen
import com.demomart.register.identify.IdentifyViewModel
import com.demomart.register.identify.NfcReader
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.demomart.register.sale.AmountScreen
import com.demomart.register.sale.SaleFlowState
import com.demomart.register.sale.SaleScreen
import com.demomart.register.sale.ResultScreen
import com.demomart.register.sales.RecentSalesScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.ui.theme.DemoMartRegisterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var auth: AuthTokenProvider
    @Inject lateinit var flow: SaleFlowState
    private val nfcReader by lazy { NfcReader(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoMartRegisterTheme {
                val nav = rememberNavController()
                val start = remember { if (auth.signedIn.value) "amount" else "login" }
                NavHost(nav, startDestination = start) {
                    composable("login") {
                        LoginScreen(onSignedIn = {
                            nav.navigate("amount") { popUpTo("login") { inclusive = true } }
                        })
                    }
                    composable("amount") {
                        val cashier by auth.displayName.collectAsStateWithLifecycle()
                        AmountScreen(
                            cashier = cashier,
                            onContinue = { nav.navigate("identify") },
                            onRecent = { nav.navigate("recent") },
                            onSignOut = auth::signOut,
                        )
                    }
                    composable("identify") { entry ->
                        if (flow.amountCents.value <= 0) {
                            LaunchedEffect(Unit) { nav.popBackStack("amount", inclusive = false) }
                            return@composable
                        }
                        val model: IdentifyViewModel = hiltViewModel()
                        val back = {
                            flow.setCustomer(null)
                            nav.popBackStack()
                            Unit
                        }
                        BackHandler(onBack = back)
                        DisposableEffect(entry, model) {
                            val observer = LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_RESUME -> {
                                        model.setNfcAvailability(nfcReader.availability())
                                        if (!model.state.value.qrMode) nfcReader.start(model::onNfc)
                                        model.setReaderOn(nfcReader.isActive)
                                    }
                                    Lifecycle.Event.ON_PAUSE -> {
                                        nfcReader.stop()
                                        model.setReaderOn(false)
                                    }
                                    else -> Unit
                                }
                            }
                            entry.lifecycle.addObserver(observer)
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            onDispose {
                                entry.lifecycle.removeObserver(observer)
                                nfcReader.stop()
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                        val qrMode = model.state.collectAsStateWithLifecycle().value.qrMode
                        LaunchedEffect(qrMode) {
                            when {
                                qrMode -> nfcReader.stop()
                                entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) -> nfcReader.start(model::onNfc)
                            }
                            model.setReaderOn(nfcReader.isActive)
                        }
                        val amount by flow.amountCents.collectAsStateWithLifecycle()
                        IdentifyScreen(amount, onBack = back, onIdentified = { nav.navigate("sale") }, model)
                    }
                    composable("sale") {
                        if (flow.amountCents.value <= 0) {
                            LaunchedEffect(Unit) { nav.popBackStack("amount", inclusive = false) }
                        } else {
                            val backToIdentify = {
                                flow.setCustomer(null)
                                nav.navigate("identify") { popUpTo("identify") { inclusive = true } }
                            }
                            SaleScreen(
                                onIdentify = backToIdentify,
                                onAmount = { nav.popBackStack("amount", inclusive = false) },
                                onResult = {
                                    nav.navigate("result") { popUpTo("amount") { inclusive = true } }
                                },
                            )
                        }
                    }
                    composable("result") {
                        BackHandler {}
                        val newSale = {
                            nav.navigate("amount") { popUpTo("result") { inclusive = true } }
                        }
                        if (flow.lastResult.value == null) {
                            LaunchedEffect(Unit) { newSale() }
                        } else {
                            ResultScreen(onNewSale = newSale)
                        }
                    }
                    composable("recent") { RecentSalesScreen(onBack = { nav.popBackStack() }) }
                }
                LaunchedEffect(nav) {
                    auth.signedIn.collect { signedIn ->
                        if (!signedIn && nav.currentDestination?.route != "login") {
                            flow.startNew()
                            nav.navigate("login") {
                                popUpTo(nav.graph.id)
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }
    }
}
