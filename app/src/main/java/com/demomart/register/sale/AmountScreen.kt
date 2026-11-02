package com.demomart.register.sale

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.money.formatEur

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountScreen(
    cashier: String?,
    onContinue: () -> Unit,
    onRecent: () -> Unit,
    onSignOut: () -> Unit,
    model: AmountViewModel = hiltViewModel(),
) {
    val amount by model.input.collectAsStateWithLifecycle()
    val canContinue by model.canContinue.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (cashier == null) "Register #1" else "Register #1 · $cashier") },
                actions = {
                    IconButton(onClick = onRecent) { Icon(Icons.Default.List, "Recent sales") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = { menuOpen = false; onSignOut() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sale amount", style = MaterialTheme.typography.titleMedium)
            Text(formatEur(amount.cents), style = MaterialTheme.typography.displayMedium)
            listOf(
                listOf("1", "2", "3"), listOf("4", "5", "6"),
                listOf("7", "8", "9"), listOf("00", "0", "⌫"),
            ).forEach { keys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Box(
                                Modifier.combinedClickable(
                                    role = Role.Button,
                                    onClickLabel = if (key == "⌫") "Backspace" else null,
                                    onLongClickLabel = if (key == "⌫") "Clear" else null,
                                    onLongClick = if (key == "⌫") model::onClear else null,
                                    onClick = {
                                        when (key) {
                                            "⌫" -> model.onBackspace()
                                            "00" -> model.onDoubleZero()
                                            else -> model.onDigit(key.toInt())
                                        }
                                    },
                                ),
                                contentAlignment = Alignment.Center,
                            ) { Text(key, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
            Button(
                onClick = { model.continueToIdentify(); onContinue() },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Scan loyalty card") }
        }
    }
}
