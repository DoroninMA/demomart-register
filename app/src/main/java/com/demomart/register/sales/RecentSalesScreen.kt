package com.demomart.register.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.money.formatEur
import com.demomart.register.net.SaleResult
import com.demomart.register.sale.RefundDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

fun saleRowText(
    sale: SaleResult,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): String {
    val time = Instant.parse(sale.createdAt).atZone(zone)
    val stamp = if (time.toLocalDate() == today) timeFormat.format(time)
    else "${dateFormat.format(time)}, ${timeFormat.format(time)}"
    val name = sale.displayName ?: "No card"
    val points = if (sale.displayName == null) "" else " · +${sale.earnedPoints} / −${sale.redeemPoints} pts"
    return "$stamp · $name · ${formatEur(sale.paidCents)}$points"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentSalesScreen(onBack: () -> Unit, model: RecentSalesViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var confirmRefund by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Recent sales") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(state.loading, model::refresh, Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.error?.let { error ->
                    item { Text(error, color = MaterialTheme.colorScheme.error) }
                }
                if (state.items.isEmpty() && !state.loading && state.error == null) {
                    item { Text("No sales yet") }
                }
                items(state.items, key = { it.saleId }) { sale ->
                    SaleRow(sale, enabled = !state.loading && state.refunding == null) {
                        confirmRefund = sale.receiptNo
                    }
                }
            }
        }
    }

    confirmRefund?.let { receipt ->
        RefundDialog(
            onDismiss = { confirmRefund = null },
            onConfirm = { confirmRefund = null; model.refund(receipt) },
        )
    }
}

@Composable
private fun SaleRow(sale: SaleResult, enabled: Boolean, onRefund: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val refunded = sale.status == "refunded"
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val color = if (refunded) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
                Text(saleRowText(sale), color = color)
                if (refunded) Text("Refunded", style = MaterialTheme.typography.labelMedium, color = color)
            }
            if (!refunded) {
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                        Icon(Icons.Default.MoreVert, "Sale options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Refund") },
                            onClick = { menuOpen = false; onRefund() },
                        )
                    }
                }
            }
        }
    }
}
