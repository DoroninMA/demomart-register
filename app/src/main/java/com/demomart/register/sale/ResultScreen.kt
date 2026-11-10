package com.demomart.register.sale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.money.formatEur
import com.demomart.register.sound.playSuccessSound
import com.demomart.register.ui.theme.EarnGreen
import com.demomart.register.ui.theme.RedeemOrange

@Composable
fun ResultScreen(onNewSale: () -> Unit, model: ResultViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val sale = state.result
    val refunded = sale.status == "refunded"
    var confirmRefund by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model) { model.navigateToAmount.collect { onNewSale() } }
    val context = LocalContext.current
    var soundFor by rememberSaveable { mutableStateOf("") }
    val soundKey = "${sale.receiptNo}:${sale.status}"
    LaunchedEffect(soundKey) {
        if (soundFor != soundKey) {
            soundFor = soundKey
            playSuccessSound(context)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.notice?.let { notice -> Card { Text(notice, Modifier.padding(16.dp)) } }
            when {
                refunded -> Text("Refunded", style = MaterialTheme.typography.headlineLarge)
                sale.balance == null -> Text("Sale complete", style = MaterialTheme.typography.headlineLarge)
                sale.redeemPoints > 0 -> {
                    Text("−${sale.redeemPoints} pts spent", color = RedeemOrange, style = MaterialTheme.typography.headlineLarge)
                    Text("+${sale.earnedPoints} pts earned", color = EarnGreen, style = MaterialTheme.typography.titleLarge)
                }
                else -> Text("+${sale.earnedPoints} pts", color = EarnGreen, style = MaterialTheme.typography.headlineLarge)
            }
            sale.balance?.let { Text("New balance $it pts", style = MaterialTheme.typography.titleLarge) }
            Text("#${sale.registerId}-${sale.receiptNo.toString().padStart(6, '0')}")
            Text("Paid ${formatEur(sale.paidCents)}")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!refunded) {
                OutlinedButton(
                    onClick = { confirmRefund = true },
                    enabled = !state.refunding,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.refunding) "Refunding…" else "Refund this sale") }
            }
            Button(
                onClick = model::newSale,
                enabled = !state.refunding,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("New sale") }
        }
    }
    if (confirmRefund) {
        RefundDialog(
            onDismiss = { confirmRefund = false },
            onConfirm = { confirmRefund = false; model.refund() },
        )
    }
}

@Composable
internal fun RefundDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Refund this sale?") },
        text = { Text("This refunds the whole sale and reverses its points.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Refund") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
