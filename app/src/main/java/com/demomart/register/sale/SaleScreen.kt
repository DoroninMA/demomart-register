package com.demomart.register.sale

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.money.formatEur
import com.demomart.register.net.QuoteOut
import com.demomart.register.ui.theme.EarnGreen
import com.demomart.register.ui.theme.RedeemOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                state.customer?.let { "${it.displayName} · ${it.balance} pts" } ?: "No loyalty card",
                style = MaterialTheme.typography.titleMedium,
            )
            val quote = state.quote
            if (quote == null) {
                Text(formatEur(state.amountCents), style = MaterialTheme.typography.displayMedium)
                if (state.quoting) Text("Calculating…")
            } else {
                Totals(quote, showEarn = state.customer != null)
                if (state.customer != null && quote.maxRedeemPoints > 0) {
                    RedeemControl(state, quote.maxRedeemPoints, model::setRedeem)
                }
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
private fun Totals(quote: QuoteOut, showEarn: Boolean) {
    val paid by animateIntAsState(quote.paidCents.toInt(), tween(300), label = "paid")
    val discount by animateIntAsState(quote.discountCents.toInt(), tween(300), label = "discount")
    val earn by animateIntAsState(quote.earnPoints.toInt(), tween(300), label = "earn")
    Text("Subtotal ${formatEur(quote.amountCents)}", style = MaterialTheme.typography.bodyLarge)
    Text(
        if (discount > 0) "Points −${formatEur(discount.toLong())}" else "No points used",
        color = if (discount > 0) RedeemOrange else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("To pay", style = MaterialTheme.typography.headlineSmall)
    Text(
        formatEur(paid.toLong()),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    if (showEarn) Text("Will earn +$earn pts", color = EarnGreen)
}

internal fun redeemPresets(max: Long): List<Pair<String, Long>> = buildList {
    add("None" to 0L)
    for (points in listOf(100L, 500L)) if (points < max) add(formatEur(points) to points)
    add("Max" to max)
}

@Composable
private fun RedeemControl(state: SaleState, max: Long, onChange: (Long) -> Unit) {
    val enabled = !state.busy && !state.canRetry
    val points = state.redeemPoints
    Text("Pay with points", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        redeemPresets(max).forEach { (label, value) ->
            Tile(label, Modifier.weight(1f), enabled, selected = points == value) { onChange(value) }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepeatTile("−", enabled && points > 0) { onChange(points - 100) }
        Text(
            "$points pts",
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
        RepeatTile("+", enabled && points < max) { onChange(points + 100) }
    }
}

// Same flat tiles as the amount keypad
@Composable
private fun Tile(
    text: String,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Color.Unspecified else colors.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

// Steps on press and while held; a disabled Surface never reports the release, so touches are handled here
@Composable
private fun RepeatTile(text: String, enabled: Boolean, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .heightIn(min = 56.dp)
            .semantics {
                role = Role.Button
                if (enabled) onClick { step(); true } else disabled()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    step()
                    val repeat = scope.launch {
                        delay(400)
                        while (true) {
                            step()
                            delay(120)
                        }
                    }
                    // finally also runs when the tile gets disabled mid-press
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        repeat.cancel()
                    }
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
