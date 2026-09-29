package app.splitmate.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.data.local.entities.GroupEntity
import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.ui.theme.BalanceColors
import app.splitmate.ui.theme.MoneyDisplayStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val netTotal = state.groups.sumOf { it.netBalanceMinor }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SplitMate", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGroup,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New group") },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HeroBalanceCard(netTotal, isOffline = state.isOffline) }

                item {
                    Text(
                        "Your groups",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }

                if (state.groups.isEmpty() && !state.isLoading) {
                    item { EmptyGroupsState(onCreateGroup) }
                } else {
                    items(state.groups, key = { it.id }) { group -> GroupRow(group, onClick = { onOpenGroup(group.id) }) }
                }

                item { Spacer(Modifier.height(72.dp)) } // clears the FAB
            }
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun HeroBalanceCard(netTotal: Long, isOffline: Boolean) {
    val isPositive = netTotal >= 0
    val accent = if (isPositive) BalanceColors.Positive else BalanceColors.Negative
    val containerColor = if (isPositive) BalanceColors.PositiveContainer else BalanceColors.NegativeContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Overall balance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                (if (isPositive) "+" else "-") + BalanceCalculator.formatMajorUnits(kotlin.math.abs(netTotal)),
                style = MoneyDisplayStyle,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isPositive) "You are owed, overall" else "You owe, overall",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isOffline) {
                Spacer(Modifier.height(12.dp))
                AssistChip(
                    onClick = {},
                    label = { Text("Offline - showing saved data") },
                    leadingIcon = { Icon(Icons.Filled.WifiOff, null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

@Composable
private fun EmptyGroupsState(onCreateGroup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text("No groups yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Start one for a trip, roommates, or anything you split with others.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onCreateGroup) { Text("Create a group") }
        }
    }
}

@Composable
private fun GroupRow(group: GroupEntity, onClick: () -> Unit) {
    val isPositive = group.netBalanceMinor >= 0
    val accent = if (isPositive) BalanceColors.Positive else BalanceColors.Negative

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        group.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${group.memberCount} ${if (group.memberCount == 1) "member" else "members"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (group.netBalanceMinor != 0L) {
                Text(
                    (if (isPositive) "+" else "-") + BalanceCalculator.formatMajorUnits(kotlin.math.abs(group.netBalanceMinor)),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text("settled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
