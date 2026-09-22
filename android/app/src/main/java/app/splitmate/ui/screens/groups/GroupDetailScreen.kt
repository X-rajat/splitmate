package app.splitmate.ui.screens.groups

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.data.local.entities.ExpenseEntity
import app.splitmate.data.remote.dto.BalanceEntryDto
import app.splitmate.data.remote.dto.SettlementSuggestionDto
import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.ui.theme.BalanceColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onAddExpense: () -> Unit,
    onBack: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.invitation) {
        state.invitation?.let { invitation ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, invitation.share_message)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share group invite"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group") },
                actions = {
                    IconButton(onClick = { viewModel.generateInvite() }) { Icon(Icons.Filled.Share, "Share group") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddExpense, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Expense") })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.error != null) {
                item { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
            }
            if (state.suggestedSettlements.isNotEmpty()) {
                item { Text("Suggested settlements", style = MaterialTheme.typography.titleMedium) }
                items(state.suggestedSettlements) { SettlementRow(it) }
            }
            item { Text("Balances", style = MaterialTheme.typography.titleMedium) }
            items(state.balances) { BalanceRow(it) }
            item { Text("Recent expenses", style = MaterialTheme.typography.titleMedium) }
            if (state.expenses.isEmpty()) {
                item { Text("No expenses yet. Tap + Expense to add one.") }
            } else {
                items(state.expenses, key = { it.id }) { ExpenseRow(it) }
            }
        }
    }
}

@Composable
private fun BalanceRow(entry: BalanceEntryDto) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.user_name)
            Text(
                BalanceCalculator.formatMajorUnits(entry.net_minor),
                color = if (entry.net_minor >= 0) BalanceColors.Positive else BalanceColors.Negative,
            )
        }
    }
}

@Composable
private fun SettlementRow(s: SettlementSuggestionDto) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${s.from_user_name} → ${s.to_user_name}")
            Spacer(Modifier.weight(1f))
            Text(BalanceCalculator.formatMajorUnits(s.amount_minor))
        }
    }
}

@Composable
private fun ExpenseRow(e: ExpenseEntity) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(e.description, style = MaterialTheme.typography.titleSmall)
            Text("${BalanceCalculator.formatMajorUnits(e.amountMinor)} ${e.currency} - ${e.category}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
