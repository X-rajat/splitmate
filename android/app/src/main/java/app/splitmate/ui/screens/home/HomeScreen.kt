package app.splitmate.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.data.local.entities.GroupEntity
import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.ui.theme.BalanceColors

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
        topBar = { TopAppBar(title = { Text("SplitMate") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateGroup, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Group") })
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Overall balance", style = MaterialTheme.typography.labelLarge)
                            Text(
                                (if (netTotal >= 0) "+" else "") + BalanceCalculator.formatMajorUnits(netTotal),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (netTotal >= 0) BalanceColors.Positive else BalanceColors.Negative,
                            )
                            Text(
                                if (netTotal >= 0) "You are owed overall" else "You owe overall",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (state.isOffline) {
                                Spacer(Modifier.height(8.dp))
                                AssistChip(onClick = {}, label = { Text("Offline - showing cached data") })
                            }
                        }
                    }
                }
                item { Text("Your groups", style = MaterialTheme.typography.titleMedium) }
                if (state.groups.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No groups yet. Create one to start splitting expenses.")
                        }
                    }
                } else {
                    items(state.groups, key = { it.id }) { group -> GroupRow(group, onClick = { onOpenGroup(group.id) }) }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(group: GroupEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text("${group.memberCount} members", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                BalanceCalculator.formatMajorUnits(group.netBalanceMinor),
                color = if (group.netBalanceMinor >= 0) BalanceColors.Positive else BalanceColors.Negative,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshBox(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Material3's PullToRefresh API varies across versions; wrap so screens don't hard-couple to it.
    Box(modifier = modifier) {
        content()
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
