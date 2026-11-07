package com.demomart.register.sale

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.money.formatEur
import com.demomart.register.ui.theme.RedeemOrange
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleScreen(
    onIdentify: () -> Unit,
    onAmount: () -> Unit,
    onResult: () -> Unit,
    model: SaleViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.navigateToIdentify.collect { onIdentify() } }
    LaunchedEffect(model) { model.navigateToAmount.collect { onAmount() } }
    LaunchedEffect(model) { model.navigateToResult.collect { onResult() } }
    BackHandler { if (!state.busy && !state.canRetry) onIdentify() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Review sale") },
                navigationIcon = {
                    TextButton(onClick = onIdentify, enabled = !state.busy && !state.canRetry) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                state.customer?.let { "${it.displayName} · ${it.balance} pts" } ?: "No loyalty card",
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Subtotal ${formatEur(state.amountCents)}")
            val quote = state.quote
            if (state.customer != null && quote != null && quote.maxRedeemPoints > 0) {
                RedeemControl(state, quote.maxRedeemPoints, model::setRedeem)
            }
            if (state.quoting) Text("Calculating…")
            if (quote != null && !state.quoting && quote.redeemPoints == state.redeemPoints) {
                if (quote.discountCents > 0) Text("−${formatEur(quote.discountCents)}", color = RedeemOrange)
                Text("To pay")
                Text(formatEur(quote.paidCents), style = MaterialTheme.typography.displayMedium)
                if (state.customer != null) Text("Will earn +${quote.earnPoints} pts")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.canRetry) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::retry, enabled = !state.busy) { Text("Retry") }
                    TextButton(onClick = model::cancelPending, enabled = !state.busy) { Text("Cancel") }
                }
            } else {
                Button(
                    onClick = model::confirm,
                    enabled = state.canConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(if (state.busy) "Confirming…" else "Confirm sale") }
                if (quote == null && state.error != null && !state.quoting) {
                    TextButton(onClick = model::retryQuote) { Text("Retry calculation") }
                }
            }
        }
    }
}

@Composable
private fun RedeemControl(state: SaleState, max: Long, onChange: (Long) -> Unit) {
    val units = (max / 100).toInt()
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Pay with points", style = MaterialTheme.typography.titleMedium)
            Text("${state.redeemPoints} pts")
            Slider(
                value = (state.redeemPoints / 100).toFloat().coerceAtMost(units.toFloat()),
                onValueChange = { onChange(it.roundToLong() * 100) },
                valueRange = 0f..units.toFloat(),
                steps = (units - 1).coerceAtLeast(0),
                enabled = !state.busy && !state.canRetry,
            )
        }
    }
}
