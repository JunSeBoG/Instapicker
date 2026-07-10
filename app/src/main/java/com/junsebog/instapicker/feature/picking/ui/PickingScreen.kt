package com.junsebog.instapicker.feature.picking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState
import com.junsebog.instapicker.feature.picking.domain.PickTab
import com.junsebog.instapicker.feature.picking.domain.PickingIntent
import com.junsebog.instapicker.feature.picking.domain.PickingUiState
import com.junsebog.instapicker.feature.picking.domain.ScannerState

/** Stateful entry point: pulls the ViewModel and forwards its state to the pure UI. */
@Composable
fun PickingRoute(viewModel: PickingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    PickingScreen(state = state, onIntent = viewModel::dispatch)
}

/** The whole screen as a pure function of [state]; every action is an intent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickingScreen(state: PickingUiState, onIntent: (PickingIntent) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message = message.text)
            onIntent(PickingIntent.DismissMessage)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "Instapicker") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PickTabRow(active = state.activeTab, onSelect = { onIntent(PickingIntent.ChangeTab(it)) })
            ItemList(
                tab = state.activeTab,
                items = state.itemsIn(state.activeTab.toPickState()),
                onIntent = onIntent,
            )
        }
    }

    val scanner = state.scanner
    if (scanner is ScannerState.Active) {
        ScannerOverlay(itemId = scanner.itemId, onCancel = { onIntent(PickingIntent.CancelScan) })
    }
}

@Composable
private fun PickTabRow(active: PickTab, onSelect: (PickTab) -> Unit) {
    TabRow(selectedTabIndex = active.ordinal) {
        PickTab.entries.forEach { tab ->
            Tab(
                selected = tab == active,
                onClick = { onSelect(tab) },
                text = { Text(text = tab.label()) },
            )
        }
    }
}

@Composable
private fun ItemList(tab: PickTab, items: List<PickItem>, onIntent: (PickingIntent) -> Unit) {
    if (items.isEmpty()) {
        EmptyState(tab = tab)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.id }) { item ->
            PickItemRow(item = item, onIntent = onIntent)
        }
    }
}

/** Friendly per-tab placeholder so an empty list never reads as a broken screen. */
@Composable
private fun EmptyState(tab: PickTab) {
    val message = when (tab) {
        PickTab.PENDING -> "Nothing left to pick — every item has been handled."
        PickTab.REMOVED -> "No items removed."
        PickTab.ADDED -> "No items added yet. Scan a product to add it here."
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(all = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PickItemRow(item: PickItem, onIntent: (PickingIntent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(all = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = item.ean13,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                QuantityBadge(item = item)
            }
            Spacer(modifier = Modifier.height(12.dp))
            RowActions(item = item, onIntent = onIntent)
        }
    }
}

/**
 * Big, glanceable count on the right — readable in a dim warehouse. While a stacked
 * row is still being picked it shows scan progress (e.g. "1/3"); once it is Added or
 * Removed it shows just the quantity (e.g. "×3").
 */
@Composable
private fun QuantityBadge(item: PickItem) {
    val text = if (item.state == PickState.PENDING && item.requestedQty > 1) {
        val scanned = item.requestedQty - item.remainingToScan
        "$scanned/${item.requestedQty}"
    } else {
        "×${item.requestedQty}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RowActions(item: PickItem, onIntent: (PickingIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (item.state) {
            PickState.PENDING -> {
                Button(onClick = { onIntent(PickingIntent.StartScan(item.id)) }) { Text(text = "Scan") }
                OutlinedButton(onClick = { onIntent(PickingIntent.MoveToRemoved(item.id)) }) {
                    Text(text = "Remove")
                }
            }
            PickState.REMOVED ->
                OutlinedButton(onClick = { onIntent(PickingIntent.MoveToPending(item.id)) }) {
                    Text(text = "Move to pending")
                }
            PickState.ADDED ->
                OutlinedButton(
                    onClick = { onIntent(PickingIntent.Rollback(itemId = item.id, to = PickState.PENDING)) },
                ) {
                    Text(text = "Undo")
                }
        }
    }
}

@Composable
private fun ScannerOverlay(itemId: String, onCancel: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim) {
        Column(
            modifier = Modifier.fillMaxSize().padding(all = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Scanning item $itemId", color = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            // TODO(feature/scanner): the live CameraX + ML Kit preview replaces this shell.
            Button(onClick = onCancel) { Text(text = "Cancel") }
        }
    }
}

private fun PickTab.toPickState(): PickState = when (this) {
    PickTab.PENDING -> PickState.PENDING
    PickTab.REMOVED -> PickState.REMOVED
    PickTab.ADDED -> PickState.ADDED
}

private fun PickTab.label(): String = when (this) {
    PickTab.PENDING -> "Pending"
    PickTab.REMOVED -> "Removed"
    PickTab.ADDED -> "Added"
}
