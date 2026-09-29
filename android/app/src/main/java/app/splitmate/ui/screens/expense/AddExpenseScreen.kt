package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.data.remote.dto.GroupMemberDto
import app.splitmate.ui.common.AddGuestDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(onDone: () -> Unit, viewModel: AddExpenseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var categoryExpanded by remember { mutableStateOf(false) }
    var payerExpanded by remember { mutableStateOf(false) }
    var showAddGuest by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onDone() }

    if (showAddGuest) {
        AddGuestDialog(
            onConfirm = { name -> viewModel.addGuest(name); showAddGuest = false },
            onDismiss = { showAddGuest = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add expense", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = { d -> viewModel.update { it.copy(description = d) } },
                            label = { Text("What was it for?") },
                            placeholder = { Text("Dinner, taxi, hotel...") },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = state.amountText,
                            onValueChange = { a -> viewModel.update { it.copy(amountText = a) } },
                            label = { Text("Amount") },
                            leadingIcon = { Text("₹", style = MaterialTheme.typography.titleMedium) },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))

                        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                            OutlinedTextField(
                                readOnly = true, value = state.category.replaceFirstChar { it.uppercase() }, onValueChange = {},
                                label = { Text("Category") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                                EXPENSE_CATEGORIES.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.replaceFirstChar { ch -> ch.uppercase() }) },
                                        onClick = { viewModel.update { s -> s.copy(category = c) }; categoryExpanded = false },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))

                        val paidByName = state.members.firstOrNull { it.user_id == state.paidBy }?.name ?: "Who paid?"
                        ExposedDropdownMenuBox(expanded = payerExpanded, onExpandedChange = { payerExpanded = it }) {
                            OutlinedTextField(
                                readOnly = true, value = paidByName, onValueChange = {}, label = { Text("Paid by") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = payerExpanded, onDismissRequest = { payerExpanded = false }) {
                                state.members.forEach { m ->
                                    DropdownMenuItem(text = { Text(m.displayName()) }, onClick = { viewModel.update { s -> s.copy(paidBy = m.user_id) }; payerExpanded = false })
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Split equally", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "between ${state.participantIds.size} of ${state.members.size} people",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showAddGuest = true }) {
                        Icon(Icons.Filled.PersonAddAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add guest")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            items(state.members, key = { it.user_id }) { member ->
                MemberToggleRow(
                    member = member,
                    checked = member.user_id in state.participantIds,
                    onToggle = { viewModel.toggleParticipant(member.user_id) },
                )
            }

            item {
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.savedOffline) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Saved offline - will sync automatically when you're back online.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::submit,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save expense", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun GroupMemberDto.displayName(): String =
    if (is_placeholder) "$name (guest)" else name

@Composable
private fun MemberToggleRow(member: GroupMemberDto, checked: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Text(member.displayName(), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
