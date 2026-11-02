package com.demomart.register

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.demomart.register.auth.AuthTokenProvider
import com.demomart.register.auth.LoginScreen
import com.demomart.register.sale.AmountScreen
import com.demomart.register.sale.SaleFlowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.ui.theme.DemoMartRegisterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var auth: AuthTokenProvider
    @Inject lateinit var flow: SaleFlowState

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
                    composable("identify") {
                        BackHandler {
                            flow.setCustomer(null)
                            nav.popBackStack()
                        }
                        Placeholder("Scan loyalty card")
                    }
                    composable("sale") {
                        BackHandler {
                            flow.setCustomer(null)
                            nav.popBackStack()
                        }
                        Placeholder("Confirm sale")
                    }
                    composable("result") {
                        BackHandler {}
                        Placeholder("Sale complete")
                    }
                    composable("recent") { Placeholder("Recent sales") }
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


@Composable
private fun Placeholder(text: String) {
    Surface {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text)
        }
    }
}
