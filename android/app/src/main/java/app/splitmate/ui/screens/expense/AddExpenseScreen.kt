package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(onDone: () -> Unit, viewModel: AddExpenseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onDone() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Add expense", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.description,
            onValueChange = { d -> viewModel.update { it.copy(description = d) } },
            label = { Text("Description (e.g. Dinner)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.amountText,
            onValueChange = { a -> viewModel.update { it.copy(amountText = a) } },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                readOnly = true, value = state.category, onValueChange = {}, label = { Text("Category") },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                EXPENSE_CATEGORIES.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { viewModel.update { s -> s.copy(category = c) }; expanded = false })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.paidBy,
            onValueChange = { p -> viewModel.update { it.copy(paidBy = p) } },
            label = { Text("Paid by (user id)") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Enter the paying member's user id; a member picker wires this to real group members via GET /groups/{id}") },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.participantIds.joinToString(","),
            onValueChange = { txt -> viewModel.update { it.copy(participantIds = txt.split(",").map(String::trim).filter(String::isNotBlank).toSet()) } },
            label = { Text("Split between (comma-separated user ids)") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
        }
        if (state.savedOffline) {
            Spacer(Modifier.height(8.dp))
            Text("Saved offline - will sync automatically when you're back online.")
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = viewModel::submit, modifier = Modifier.fillMaxWidth()) { Text("Save expense") }
    }
}
