package app.splitmate.ui.screens.groups

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.data.local.entities.ExpenseEntity
import app.splitmate.data.remote.dto.BalanceEntryDto
import app.splitmate.data.remote.dto.SettlementSuggestionDto
import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.ui.common.AddGuestDialog
import app.splitmate.ui.theme.BalanceColors
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onAddExpense: () -> Unit,
    onBack: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddGuest by remember { mutableStateOf(false) }

    if (showAddGuest) {
        AddGuestDialog(
            onConfirm = { name -> viewModel.addGuest(name); showAddGuest = false },
            onDismiss = { showAddGuest = false },
        )
    }

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
                title = { Text("Group", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showAddGuest = true }) { Icon(Icons.Filled.PersonAdd, "Add guest without signup") }
                    IconButton(onClick = viewModel::generateInvite) { Icon(Icons.Filled.Share, "Share group invite") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddExpense, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Expense") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.error != null) {
                item {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state.suggestedSettlements.isNotEmpty()) {
                item { SectionHeader("Suggested settlements") }
                items(state.suggestedSettlements, key = { it.from_user_id + it.to_user_id }) { SettlementRow(it) }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item { SectionHeader("Balances") }
            if (state.balances.isEmpty()) {
                item { Text("No balances yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.balances, key = { it.user_id }) { BalanceRow(it) }
            }
            item { Spacer(Modifier.height(8.dp)) }

            item { SectionHeader("Recent expenses") }
            if (state.expenses.isEmpty()) {
                item { EmptyExpensesState(onAddExpense) }
            } else {
                items(state.expenses, key = { it.id }) { ExpenseRow(it) }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun BalanceRow(entry: BalanceEntryDto) {
    val isPositive = entry.net_minor >= 0
    val accent = if (isPositive) BalanceColors.Positive else BalanceColors.Negative
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.user_name, style = MaterialTheme.typography.bodyLarge)
            if (entry.net_minor != 0L) {
                Text(
                    (if (isPositive) "+" else "-") + BalanceCalculator.formatMajorUnits(abs(entry.net_minor)),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text("settled up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettlementRow(s: SettlementSuggestionDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.from_user_name, fontWeight = FontWeight.Medium)
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 10.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(s.to_user_name, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(BalanceCalculator.formatMajorUnits(s.amount_minor), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExpenseRow(e: ExpenseEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Receipt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.description, style = MaterialTheme.typography.titleSmall)
                Text(
                    e.category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(BalanceCalculator.formatMajorUnits(e.amountMinor), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyExpensesState(onAddExpense: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Receipt,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("No expenses yet", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Add the first one to start tracking who owes what.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onAddExpense) { Text("Add expense") }
        }
    }
}
